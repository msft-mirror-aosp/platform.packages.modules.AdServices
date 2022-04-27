/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.measurement;

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;
import static android.view.MotionEvent.obtain;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.measurement.IMeasurementCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.actions.ReportingJob;
import com.android.adservices.service.measurement.attribution.AttributionJobHandlerWrapper;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end test from source and trigger registration to attribution reporting. Extensions of
 * this class can implement different ways to prepare the registrations, either with an external
 * server or mocking HTTP responses, for example; similarly for examining the attribution reports.
 *
 * Consider @RunWith(Parameterized.class)
 */
public abstract class InternalE2ETest {
    // Used to fuzzy-match expected report (not delivery) time
    private static final long REPORT_TIME_EPSILON = TimeUnit.HOURS.toMillis(2);
    private static final String TEST_DIR_NAME = "msmt_internal_e2e_tests";

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    static final DatastoreManager sDatastoreManager = DatastoreManagerFactory.getDatastoreManager(
            ApplicationProvider.getApplicationContext());
    private final AttributionJobHandlerWrapper mAttributionHelper;
    private final Collection<Action> mActionsList;
    final ReportObjects mExpectedOutput;
    // Extenders of the class populate in their own ways this container for actual output.
    final ReportObjects mActualOutput;
    MeasurementImpl mMeasurementImpl;

    private interface EventReportPayloadKeys {
        // Keys used to compare actual with expected output
        List<String> STRINGS = ImmutableList.of(
                "attribution_destination",
                "source_event_id",
                "trigger_data",
                "source_type");
        String DOUBLE = "randomized_trigger_rate";
    }

    public interface TestFormatJsonMapping {
        String TEST_INPUT_KEY = "input";
        String TEST_OUTPUT_KEY = "output";
        String SOURCE_REGISTRATIONS_KEY = "sources";
        String TRIGGER_REGISTRATIONS_KEY = "triggers";
        String URI_TO_RESPONSE_HEADERS_KEY = "responses";
        String URI_TO_RESPONSE_HEADERS_URL_KEY = "url";
        String URI_TO_RESPONSE_HEADERS_RESPONSE_KEY = "response";
        String REGISTRATION_REQUEST_KEY = "registration_request";
        String ATTRIBUTION_SOURCE_KEY = "registrant";
        String SOURCE_TOP_ORIGIN_URI_KEY = "source_origin";
        String TRIGGER_TOP_ORIGIN_URI_KEY = "destination_origin";
        String REGISTRATION_URI_KEY = "attribution_src_url";
        String INPUT_EVENT_KEY = "source_type";
        String SOURCE_VIEW_TYPE = "event";
        String TIMESTAMP_KEY = "timestamp";
        String EVENT_REPORT_OBJECTS_KEY = "event_level_results";
        String AGGREGATE_REPORT_OBJECTS_KEY = "aggregatable_results";
        String REPORT_TIME_KEY = "report_time";
        String REPORT_TO_KEY = "report_url";
        String PAYLOAD_KEY = "payload";
    }

    @Parameterized.Parameters(name = "{2}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        AssetManager assetManager = sContext.getAssets();
        List<InputStream> inputStreams = new ArrayList<InputStream>();
        String[] testDirectoryList = assetManager.list(TEST_DIR_NAME);
        for (int i = 0; i < testDirectoryList.length; i++) {
            inputStreams.add(assetManager.open(TEST_DIR_NAME + "/" + testDirectoryList[i]));
        }
        return getTestCasesFrom(inputStreams, testDirectoryList);
    }

    public static Map<String, List<Map<String, List<String>>>>
            getUriToResponseHeadersMap(JSONObject obj) throws JSONException {
        JSONArray uriToResArray = obj.getJSONArray(
                TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_KEY);
        Map<String, List<Map<String, List<String>>>> uriToResponseHeadersMap = new HashMap<>();

        for (int i = 0; i < uriToResArray.length(); i++) {
            JSONObject urlToResponse = uriToResArray.getJSONObject(i);
            String uri = urlToResponse.getString(
                    TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_URL_KEY);
            JSONObject headersMapJson = urlToResponse.getJSONObject(
                    TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_RESPONSE_KEY);

            Iterator<String> headers = headersMapJson.keys();
            Map<String, List<String>> headersMap = new HashMap<>();

            while (headers.hasNext()) {
                String header = headers.next();
                if (!headersMapJson.isNull(header)) {
                    String data = headersMapJson.getString(header);
                    if (header.equals("Attribution-Reporting-Redirect")) {
                        JSONArray redirects = new JSONArray(data);
                        for (int j = 0; j < redirects.length(); j++) {
                            String redirectUri = redirects.getString(j);
                            headersMap.computeIfAbsent(
                                    header, k -> new ArrayList<>()).add(redirectUri);
                        }
                    } else {
                        headersMap.put(header, Collections.singletonList(data));
                    }
                } else {
                    headersMap.put(header, null);
                }
            }

            uriToResponseHeadersMap.computeIfAbsent(uri, k -> new ArrayList<>()).add(headersMap);
        }

        return uriToResponseHeadersMap;
    }

    // 'uid', the parameter passed to Builder(), is unimportant for this test; we only need the
    // package name.
    public static AttributionSource getAttributionSource(String source) {
        return new AttributionSource.Builder(1).setPackageName(source).build();
    }

    public static InputEvent getInputEvent() {
        return obtain(
                0 /*long downTime*/,
                0 /*long eventTime*/,
                ACTION_BUTTON_PRESS,
                1 /*int pointerCount*/,
                new PointerProperties[] { new PointerProperties() },
                new PointerCoords[] { new PointerCoords() },
                0 /*int metaState*/,
                0 /*int buttonState*/,
                1.0f /*float xPrecision*/,
                1.0f /*float yPrecision*/,
                0 /*int deviceId*/,
                0 /*int edgeFlags*/,
                InputDevice.SOURCE_TOUCH_NAVIGATION,
                0 /*int flags*/);
    }

    @Before
    public void before() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        emptyTables(db);
    }

    // The 'name' parameter is needed for the JUnit parameterized test, although it's ostensibly
    // unused by this constructor.
    InternalE2ETest(Collection<Action> actions, ReportObjects expectedOutput, String name)
            throws DatastoreException {
        DatastoreManager datastoreManager = spy(sDatastoreManager);
        // Mocking the randomized trigger data to always return the truth value.
        IMeasurementDao dao = spy(datastoreManager.getMeasurementDao());
        when(datastoreManager.getMeasurementDao()).thenReturn(dao);
        doAnswer((Answer<Trigger>) triggerInvocation -> {
            Trigger trigger = spy((Trigger) triggerInvocation.callRealMethod());
            doAnswer((Answer<Long>) triggerDataInvocation ->
                    trigger.getTruncatedTriggerData(
                            triggerDataInvocation.getArgument(0)))
                    .when(trigger)
                    .getRandomizedTriggerData(any());
            return trigger;
        }).when(dao).getTrigger(anyString());

        this.mAttributionHelper = new AttributionJobHandlerWrapper(datastoreManager);
        this.mActionsList = actions;
        this.mExpectedOutput = expectedOutput;
        this.mActualOutput = new ReportObjects();
    }

    @Test
    public void runTest() throws IOException, JSONException {
        for (Action action : mActionsList) {
            if (action instanceof RegisterSource) {
                processAction((RegisterSource) action);
            } else if (action instanceof RegisterTrigger) {
                processAction((RegisterTrigger) action);
            } else if (action instanceof ReportingJob) {
                processAction((ReportingJob) action);
            }
        }

        evaluateResults();
    }

    @After
    public void after() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        emptyTables(db);
    }

    /**
     * The reporting job may be handled differently depending on whether network requests are mocked
     * or a test server is used.
     */
    abstract void processAction(ReportingJob reportingJob) throws IOException, JSONException;

    /**
     * Override with HTTP response mocks, for example.
     */
    abstract void prepareRegistrationServer(RegisterSource sourceRegistration)
            throws IOException;

    /**
     * Override with HTTP response mocks, for example.
     */
    abstract void prepareRegistrationServer(RegisterTrigger triggerRegistration)
            throws IOException;

    private static int hashForEventReportObject(JSONObject obj) {
        int n = EventReportPayloadKeys.STRINGS.size();
        Object[] objArray = new Object[n + 2];
        // We cannot use report time due to fuzzy matching between actual and expected output.
        objArray[0] = obj.optString(TestFormatJsonMapping.REPORT_TO_KEY, "");
        JSONObject payload = obj.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        objArray[1] = payload.optDouble(EventReportPayloadKeys.DOUBLE, 0);
        for (int i = 0; i < n; i++) {
            objArray[i + 2] = payload.optString(EventReportPayloadKeys.STRINGS.get(i), "");
        }
        return Arrays.hashCode(objArray);
    }

    private static long reportTimeFrom(JSONObject obj) {
        return obj.optLong(TestFormatJsonMapping.REPORT_TIME_KEY, 0);
    }

    private static boolean areEqualEventReportJsons(JSONObject obj1, JSONObject obj2)
            throws JSONException {
        if (Math.abs(obj1.getLong(TestFormatJsonMapping.REPORT_TIME_KEY)
                - obj2.getLong(TestFormatJsonMapping.REPORT_TIME_KEY))
                > REPORT_TIME_EPSILON) {
            return false;
        }
        if (!obj1.getString(TestFormatJsonMapping.REPORT_TO_KEY).equals(
                obj2.getString(TestFormatJsonMapping.REPORT_TO_KEY))) {
            return false;
        }
        JSONObject payload1 = obj1.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        JSONObject payload2 = obj2.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        if (payload1.getDouble(EventReportPayloadKeys.DOUBLE)
                != payload2.getDouble(EventReportPayloadKeys.DOUBLE)) {
            return false;
        }
        for (String key : EventReportPayloadKeys.STRINGS) {
            if (!payload1.optString(key, "").equals(payload2.optString(key, ""))) {
                return false;
            }
        }
        return true;
    }

    private static void sortEventReportObjects(List<JSONObject> eventReportObjects) {
        eventReportObjects.sort(
                // Report time can vary across implementations so cannot be included in the hash;
                // they should be similarly ordered, however, so we can use them to sort.
                Comparator.comparing(InternalE2ETest::reportTimeFrom)
                .thenComparing(InternalE2ETest::hashForEventReportObject));
    }

    private static boolean areEqual(ReportObjects p1, ReportObjects p2) throws JSONException {
        if (p1.mEventReportObjects.size() != p2.mEventReportObjects.size()
                || p1.mAggregateReportObjects.size() != p2.mAggregateReportObjects.size()) {
            return false;
        }
        for (int i = 0; i < p1.mEventReportObjects.size(); i++) {
            if (!areEqualEventReportJsons(p1.mEventReportObjects.get(i),
                    p2.mEventReportObjects.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static String getTestFailureMessage(ReportObjects expectedOutput,
            ReportObjects actualOutput) {
        return String.format("Actual output does not match expected.\n\n"
                + "(Note that report IDs are ignored in comparisons since they are not known in"
                + " advance.)\n\nExpected event report objects: %s\n\n"
                + "Actual event report objects: %s\n\n"
                + "Expected aggregate report objects: %s\n\n"
                + "Actual aggregate report objects: %s\n",
                expectedOutput.mEventReportObjects, actualOutput.mEventReportObjects,
                expectedOutput.mAggregateReportObjects, actualOutput.mAggregateReportObjects);
    }

    private static Set<Long> getExpiryTimesFrom(RegisterSource sourceRegistration)
            throws JSONException {
        Set<Long> expiryTimes = new HashSet<>();

        for (List<Map<String, List<String>>> responseHeaders :
                sourceRegistration.mUriToResponseHeadersMap.values()) {
            for (Map<String, List<String>> headersMap : responseHeaders) {
                String sourceStr = headersMap.get("Attribution-Reporting-Register-Source").get(0);
                JSONObject sourceJson = new JSONObject(sourceStr);
                long expiry = sourceJson.optLong("expiry", 0);
                expiryTimes.add(expiry);
            }
        }

        return expiryTimes;
    }

    private static void maybeAddReportingJobTimes(RegisterSource sourceRegistration,
            Set<Long> reportingJobTimes) throws JSONException {
        long sourceTime = sourceRegistration.mTimestamp;
        Set<Long> expiryTimes = getExpiryTimesFrom(sourceRegistration);
        for (Long expiry : expiryTimes) {
            long jobTime = sourceTime + 1000 * (expiry == 0
                    ? PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
                    : expiry) + 3600000L;
            reportingJobTimes.add(jobTime);
        }
    }

    /**
     * Builds and returns test cases from a JSON InputStream to be used by JUnit parameterized
     * tests.
     *
     * @return A collection of Object arrays, each with
     * {@code [Collection<Object> actions, ReportObjects expectedOutput, String name]}
     */
    private static Collection<Object[]> getTestCasesFrom(List<InputStream> inputStreams,
            String[] filenames) throws IOException, JSONException {
        List<Object[]> testCases = new ArrayList<>();

        for (int i = 0; i < inputStreams.size(); i++) {
            int size = inputStreams.get(i).available();
            byte[] buffer = new byte[size];
            inputStreams.get(i).read(buffer);
            inputStreams.get(i).close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            JSONObject testObj = new JSONObject(json);
            String name = filenames[i];
            JSONObject input = testObj.getJSONObject(TestFormatJsonMapping.TEST_INPUT_KEY);
            JSONObject output = testObj.getJSONObject(TestFormatJsonMapping.TEST_OUTPUT_KEY);

            // "Actions" are source or trigger registrations, or a reporting job.
            List<Action> actions = new ArrayList<>();
            Set<Long> reportingJobTimes = new HashSet<>();

            JSONArray sourceRegistrationArray = input.getJSONArray(
                    TestFormatJsonMapping.SOURCE_REGISTRATIONS_KEY);
            for (int j = 0; j < sourceRegistrationArray.length(); j++) {
                RegisterSource sourceRegistration =
                        new RegisterSource(sourceRegistrationArray.getJSONObject(j));
                actions.add(sourceRegistration);
                maybeAddReportingJobTimes(sourceRegistration, reportingJobTimes);
            }

            for (Long reportingJobTime : reportingJobTimes) {
                actions.add(new ReportingJob(reportingJobTime.longValue()));
            }

            JSONArray triggerRegistrationArray = input.getJSONArray(
                    TestFormatJsonMapping.TRIGGER_REGISTRATIONS_KEY);
            for (int j = 0; j < triggerRegistrationArray.length(); j++) {
                RegisterTrigger triggerRegistration =
                        new RegisterTrigger(triggerRegistrationArray.getJSONObject(j));
                actions.add(triggerRegistration);
            }

            actions.sort(Comparator.comparing(Action::getComparable));

            ReportObjects expectedOutput = getExpectedOutput(output);

            testCases.add(new Object[]{actions, expectedOutput, name});
        }

        return testCases;
    }

    private static ReportObjects getExpectedOutput(JSONObject output) throws JSONException {
        List<JSONObject> eventReportObjects = new ArrayList<>();
        JSONArray eventReportObjectsArray = output.getJSONArray(
                TestFormatJsonMapping.EVENT_REPORT_OBJECTS_KEY);
        for (int i = 0; i < eventReportObjectsArray.length(); i++) {
            JSONObject obj = eventReportObjectsArray.getJSONObject(i);
            String reportTo = obj.getString(TestFormatJsonMapping.REPORT_TO_KEY);
            eventReportObjects.add(obj.put(TestFormatJsonMapping.REPORT_TO_KEY, reportTo));
        }

        List<JSONObject> aggregateReportObjects = new ArrayList<>();
        JSONArray aggregateReportObjectsArray =
                output.getJSONArray(TestFormatJsonMapping.AGGREGATE_REPORT_OBJECTS_KEY);
        for (int i = 0; i < aggregateReportObjectsArray.length(); i++) {
            aggregateReportObjects.add(aggregateReportObjectsArray.getJSONObject(i));
        }

        return new ReportObjects(eventReportObjects, aggregateReportObjects);
    }

    /**
     * Empties measurement database tables, used for test cleanup.
     */
    private static void emptyTables(SQLiteDatabase db) {
        db.delete("msmt_source", null, null);
        db.delete("msmt_trigger", null, null);
        db.delete("msmt_adtech_urls", null, null);
        db.delete("msmt_event_report", null, null);
        db.delete("msmt_attribution_rate_limit", null, null);
    }

    private void processAction(RegisterSource sourceRegistration) throws IOException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertTrue("MeasurementImpl.register source failed",
                mMeasurementImpl.register(sourceRegistration.mRegistrationRequest,
                    sourceRegistration.mTimestamp) == IMeasurementCallback.RESULT_OK);
    }

    private void processAction(RegisterTrigger triggerRegistration) throws IOException {
        prepareRegistrationServer(triggerRegistration);
        Assert.assertTrue("MeasurementImpl.register trigger failed",
                mMeasurementImpl.register(triggerRegistration.mRegistrationRequest,
                    triggerRegistration.mTimestamp) == IMeasurementCallback.RESULT_OK);
        Assert.assertTrue("AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
    }

    private void evaluateResults() throws JSONException {
        sortEventReportObjects(mExpectedOutput.mEventReportObjects);
        sortEventReportObjects(mActualOutput.mEventReportObjects);
        Assert.assertTrue(getTestFailureMessage(mExpectedOutput, mActualOutput),
                areEqual(mExpectedOutput, mActualOutput));
    }
}
