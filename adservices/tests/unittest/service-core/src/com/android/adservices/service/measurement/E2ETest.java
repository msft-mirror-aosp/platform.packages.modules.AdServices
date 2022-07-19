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

import static com.android.adservices.ResultCode.RESULT_OK;

import android.content.AttributionSource;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.InstallApp;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.RegisterWebSource;
import com.android.adservices.service.measurement.actions.RegisterWebTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.actions.ReportingJob;
import com.android.adservices.service.measurement.actions.UninstallApp;
import com.android.adservices.service.measurement.attribution.AttributionJobHandlerWrapper;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

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
public abstract class E2ETest {
    // Used to fuzzy-match expected report (not delivery) time
    private static final long REPORT_TIME_EPSILON = TimeUnit.HOURS.toMillis(2);

    static final Context sContext = ApplicationProvider.getApplicationContext();
    static final DatastoreManager sDatastoreManager = DatastoreManagerFactory.getDatastoreManager(
            ApplicationProvider.getApplicationContext());
    private final Collection<Action> mActionsList;
    final ReportObjects mExpectedOutput;
    // Extenders of the class populate in their own ways this container for actual output.
    final ReportObjects mActualOutput;
    // Class extensions may choose to disable or enable added noise.
    AttributionJobHandlerWrapper mAttributionHelper;
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

    interface AggregateReportPayloadKeys {
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String HISTOGRAMS = "histograms";
    }

    interface AggregateHistogramKeys {
        String BUCKET = "key";
        String VALUE = "value";
    }

    public interface TestFormatJsonMapping {
        String TEST_INPUT_KEY = "input";
        String TEST_OUTPUT_KEY = "output";
        String SOURCE_REGISTRATIONS_KEY = "sources";
        String WEB_SOURCES_KEY = "web_sources";
        String SOURCE_PARAMS_REGISTRATIONS_KEY = "source_params";
        String TRIGGER_KEY = "triggers";
        String WEB_TRIGGERS_KEY = "web_triggers";
        String TRIGGER_PARAMS_REGISTRATIONS_KEY = "trigger_params";
        String URI_TO_RESPONSE_HEADERS_KEY = "responses";
        String URI_TO_RESPONSE_HEADERS_URL_KEY = "url";
        String URI_TO_RESPONSE_HEADERS_RESPONSE_KEY = "response";
        String REGISTRATION_REQUEST_KEY = "registration_request";
        String ATTRIBUTION_SOURCE_KEY = "registrant";
        String SOURCE_TOP_ORIGIN_URI_KEY = "source_origin";
        String TRIGGER_TOP_ORIGIN_URI_KEY = "destination_origin";
        String SOURCE_APP_DESTINATION_URI_KEY = "app_destination";
        String SOURCE_WEB_DESTINATION_URI_KEY = "web_destination";
        String SOURCE_VERIFIED_DESTINATION_URI_KEY = "verified_destination";
        String REGISTRATION_URI_KEY = "attribution_src_url";
        String INPUT_EVENT_KEY = "source_type";
        String SOURCE_VIEW_TYPE = "event";
        String TIMESTAMP_KEY = "timestamp";
        String EVENT_REPORT_OBJECTS_KEY = "event_level_results";
        String AGGREGATE_REPORT_OBJECTS_KEY = "aggregatable_results";
        String INSTALLS_KEY = "installs";
        String UNINSTALLS_KEY = "uninstalls";
        String INSTALLS_URI_KEY = "uri";
        String INSTALLS_TIMESTAMP_KEY = "timestamp";
        String REPORT_TIME_KEY = "report_time";
        String REPORT_TO_KEY = "report_url";
        String PAYLOAD_KEY = "payload";
        String DEBUG_KEY = "debug_key";
    }

    static Collection<Object[]> data(String testDirName) throws IOException, JSONException {
        AssetManager assetManager = sContext.getAssets();
        List<InputStream> inputStreams = new ArrayList<>();
        String[] testDirectoryList = assetManager.list(testDirName);
        for (String testFile : testDirectoryList) {
            inputStreams.add(assetManager.open(testDirName + "/" + testFile));
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

    static void clearDatabase() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        emptyTables(db);
    }

    // The 'name' parameter is needed for the JUnit parameterized test, although it's ostensibly
    // unused by this constructor.
    E2ETest(Collection<Action> actions, ReportObjects expectedOutput, String name) {
        mActionsList = actions;
        mExpectedOutput = expectedOutput;
        mActualOutput = new ReportObjects();
    }

    @Test
    public void runTest() throws IOException, JSONException {
        clearDatabase();
        for (Action action : mActionsList) {
            if (action instanceof RegisterSource) {
                processAction((RegisterSource) action);
            } else if (action instanceof RegisterTrigger) {
                processAction((RegisterTrigger) action);
            } else if (action instanceof RegisterWebSource) {
                processAction((RegisterWebSource) action);
            } else if (action instanceof RegisterWebTrigger) {
                processAction((RegisterWebTrigger) action);
            } else if (action instanceof ReportingJob) {
                processAction((ReportingJob) action);
            } else if (action instanceof InstallApp) {
                processAction((InstallApp) action);
            } else if (action instanceof UninstallApp) {
                processAction((UninstallApp) action);
            }
        }
        evaluateResults();
        clearDatabase();
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

    /** Override with HTTP response mocks, for example. */
    abstract void prepareRegistrationServer(RegisterWebSource sourceRegistration)
            throws IOException;

    /** Override with HTTP response mocks, for example. */
    abstract void prepareRegistrationServer(RegisterWebTrigger triggerRegistration)
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

    private static int hashForAggregateReportObject(JSONObject obj) {
        Object[] objArray = new Object[3];
        // We cannot use report time due to fuzzy matching between actual and expected output.
        objArray[0] = obj.optString(TestFormatJsonMapping.REPORT_TO_KEY, "");
        JSONObject payload = obj.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        objArray[1] = payload.optString(AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION, "");
        // To compare histograms, we already converted them to an ordered string of value pairs.
        objArray[2] = getComparableHistograms(
                payload.optJSONArray(AggregateReportPayloadKeys.HISTOGRAMS));
        return Arrays.hashCode(objArray);
    }

    private static long reportTimeFrom(JSONObject obj) {
        return obj.optLong(TestFormatJsonMapping.REPORT_TIME_KEY, 0);
    }

    private static boolean matchReportTimeAndReportTo(JSONObject obj1, JSONObject obj2)
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
        return true;
    }

    private static boolean areEqualEventReportJsons(JSONObject obj1, JSONObject obj2)
            throws JSONException {
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
        return matchReportTimeAndReportTo(obj1, obj2);
    }

    private static boolean areEqualAggregateReportJsons(JSONObject obj1, JSONObject obj2)
            throws JSONException {
        JSONObject payload1 = obj1.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        JSONObject payload2 = obj2.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        if (!payload1.optString(AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION, "").equals(
                payload2.optString(AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION, ""))) {
            return false;
        }
        JSONArray histograms1 = payload1.optJSONArray(AggregateReportPayloadKeys.HISTOGRAMS);
        JSONArray histograms2 = payload2.optJSONArray(AggregateReportPayloadKeys.HISTOGRAMS);
        if (!getComparableHistograms(histograms1).equals(getComparableHistograms(histograms2))) {
            return false;
        }
        return matchReportTimeAndReportTo(obj1, obj2);
    }

    private static String getComparableHistograms(@Nullable JSONArray arr) {
        if (arr == null) {
            return "";
        }
        try {
            List<String> tempList = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject pair = arr.getJSONObject(i);
                tempList.add(pair.getString(AggregateHistogramKeys.BUCKET) + ","
                        + pair.getString(AggregateHistogramKeys.VALUE));
            }
            Collections.sort(tempList);
            return String.join(";", tempList);
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static void sortEventReportObjects(List<JSONObject> eventReportObjects) {
        eventReportObjects.sort(
                // Report time can vary across implementations so cannot be included in the hash;
                // they should be similarly ordered, however, so we can use them to sort.
                Comparator.comparing(E2ETest::reportTimeFrom)
                .thenComparing(E2ETest::hashForEventReportObject));
    }

    private static void sortAggregateReportObjects(List<JSONObject> aggregateReportObjects) {
        aggregateReportObjects.sort(
                // Report time can vary across implementations so cannot be included in the hash;
                // they should be similarly ordered, however, so we can use them to sort.
                Comparator.comparing(E2ETest::reportTimeFrom)
                .thenComparing(E2ETest::hashForAggregateReportObject));
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
        for (int i = 0; i < p1.mAggregateReportObjects.size(); i++) {
            if (!areEqualAggregateReportJsons(p1.mAggregateReportObjects.get(i),
                    p2.mAggregateReportObjects.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static String getTestFailureMessage(ReportObjects expectedOutput,
            ReportObjects actualOutput) {
        return String.format("Actual output does not match expected.\n\n"
                + "(Note that report IDs are ignored in comparisons since they are not known in"
                + " advance.)\n\nEvent report objects:\n%s\n\n"
                + "Expected aggregate report objects: %s\n\n"
                + "Actual aggregate report objects: %s\n",
                prettify(expectedOutput.mEventReportObjects, actualOutput.mEventReportObjects),
                expectedOutput.mAggregateReportObjects, actualOutput.mAggregateReportObjects);
    }

    private static String prettify(List<JSONObject> expected, List<JSONObject> actual) {
        StringBuilder result = new StringBuilder("(Expected ::: Actual)"
                + "\n------------------------\n");
        for (int i = 0; i < Math.max(expected.size(), actual.size()); i++) {
            if (i < expected.size() && i < actual.size()) {
                result.append(prettifyObjs(expected.get(i), actual.get(i)));
            } else {
                if (i < expected.size()) {
                    result.append(prettifyObj("", expected.get(i)));
                }
                if (i < actual.size()) {
                    result.append(prettifyObj(" ::: ", actual.get(i)));
                }
            }
            result.append("\n------------------------\n");
        }
        return result.toString();
    }

    private static String prettifyObjs(JSONObject obj1, JSONObject obj2) {
        StringBuilder result = new StringBuilder();
        result.append(TestFormatJsonMapping.REPORT_TIME_KEY + ": ")
                .append(obj1.optString(TestFormatJsonMapping.REPORT_TIME_KEY))
                .append(" ::: ")
                .append(obj2.optString(TestFormatJsonMapping.REPORT_TIME_KEY))
                .append("\n");
        JSONObject payload1 = obj1.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        JSONObject payload2 = obj2.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        for (String key : EventReportPayloadKeys.STRINGS) {
            result.append(key)
                    .append(": ")
                    .append(payload1.optString(key))
                    .append(" ::: ")
                    .append(payload2.optString(key))
                    .append("\n");
        }
        result.append(EventReportPayloadKeys.DOUBLE + ": ")
                .append(payload1.optDouble(EventReportPayloadKeys.DOUBLE))
                .append(" ::: ")
                .append(payload2.optDouble(EventReportPayloadKeys.DOUBLE));
        return result.toString();
    }

    private static String prettifyObj(String pad, JSONObject obj) {
        StringBuilder result = new StringBuilder();
        result.append(TestFormatJsonMapping.REPORT_TIME_KEY + ": ")
                .append(pad)
                .append(obj.optString(TestFormatJsonMapping.REPORT_TIME_KEY))
                .append("\n");
        JSONObject payload = obj.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        for (String key : EventReportPayloadKeys.STRINGS) {
            result.append(key).append(": ").append(pad).append(payload.optString(key)).append("\n");
        }
        result.append(EventReportPayloadKeys.DOUBLE + ": ")
                .append(pad)
                .append(payload.optDouble(EventReportPayloadKeys.DOUBLE));
        return result.toString();
    }

    private static Set<Long> getExpiryTimesFrom(
            Collection<List<Map<String, List<String>>>> responseHeadersCollection)
            throws JSONException {
        Set<Long> expiryTimes = new HashSet<>();

        for (List<Map<String, List<String>>> responseHeaders : responseHeadersCollection) {
            for (Map<String, List<String>> headersMap : responseHeaders) {
                String sourceStr = headersMap.get("Attribution-Reporting-Register-Source").get(0);
                JSONObject sourceJson = new JSONObject(sourceStr);
                if (sourceJson.has("expiry")) {
                    expiryTimes.add(sourceJson.getLong("expiry"));
                } else {
                    expiryTimes.add(
                            PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
                }
            }
        }

        return expiryTimes;
    }

    private static Set<Action> maybeAddReportingJobTimes(
            long sourceTime, Collection<List<Map<String, List<String>>>> responseHeaders)
            throws JSONException {
        Set<Action> reportingJobsActions = new HashSet<>();
        Set<Long> expiryTimes = getExpiryTimesFrom(responseHeaders);
        for (Long expiry : expiryTimes) {
            long validExpiry = expiry;
            if (expiry > PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS) {
                validExpiry = PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
            } else if (expiry < PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS) {
                validExpiry = PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
            }
            long jobTime = sourceTime + 1000 * validExpiry + 3600000L;
            reportingJobsActions.add(new ReportingJob(jobTime));
        }

        return reportingJobsActions;
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

            actions.addAll(createSourceBasedActions(input));
            actions.addAll(createTriggerActions(input));
            actions.addAll(createInstallActions(input));
            actions.addAll(createUninstallActions(input));

            actions.sort(Comparator.comparing(Action::getComparable));

            ReportObjects expectedOutput = getExpectedOutput(output);

            testCases.add(new Object[] {actions, expectedOutput, name});
        }

        return testCases;
    }

    private static List<Action> createSourceBasedActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        // Set avoids duplicate reporting times across sources to do attribution upon.
        Set<Action> reportingJobActions = new HashSet<>();
        if (!input.isNull(TestFormatJsonMapping.SOURCE_REGISTRATIONS_KEY)) {
            JSONArray sourceRegistrationArray = input.getJSONArray(
                    TestFormatJsonMapping.SOURCE_REGISTRATIONS_KEY);
            for (int j = 0; j < sourceRegistrationArray.length(); j++) {
                RegisterSource sourceRegistration =
                        new RegisterSource(sourceRegistrationArray.getJSONObject(j));
                actions.add(sourceRegistration);
                // Add corresponding reporting job time actions
                reportingJobActions.addAll(
                        maybeAddReportingJobTimes(
                                sourceRegistration.mTimestamp,
                                sourceRegistration.mUriToResponseHeadersMap.values()));
            }
        }

        if (!input.isNull(TestFormatJsonMapping.WEB_SOURCES_KEY)) {
            JSONArray webSourceRegistrationArray =
                    input.getJSONArray(TestFormatJsonMapping.WEB_SOURCES_KEY);
            for (int j = 0; j < webSourceRegistrationArray.length(); j++) {
                RegisterWebSource webSource =
                        new RegisterWebSource(webSourceRegistrationArray.getJSONObject(j));
                actions.add(webSource);
                // Add corresponding reporting job time actions
                reportingJobActions.addAll(
                        maybeAddReportingJobTimes(
                                webSource.mTimestamp, webSource.mUriToResponseHeadersMap.values()));
            }
        }

        actions.addAll(reportingJobActions);
        return actions;
    }

    private static List<Action> createTriggerActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        if (!input.isNull(TestFormatJsonMapping.TRIGGER_KEY)) {
            JSONArray triggerRegistrationArray =
                    input.getJSONArray(TestFormatJsonMapping.TRIGGER_KEY);
            for (int j = 0; j < triggerRegistrationArray.length(); j++) {
                RegisterTrigger triggerRegistration =
                        new RegisterTrigger(triggerRegistrationArray.getJSONObject(j));
                actions.add(triggerRegistration);
            }
        }

        if (!input.isNull(TestFormatJsonMapping.WEB_TRIGGERS_KEY)) {
            JSONArray webTriggerRegistrationArray =
                    input.getJSONArray(TestFormatJsonMapping.WEB_TRIGGERS_KEY);
            for (int j = 0; j < webTriggerRegistrationArray.length(); j++) {
                RegisterWebTrigger webTrigger =
                        new RegisterWebTrigger(webTriggerRegistrationArray.getJSONObject(j));
                actions.add(webTrigger);
            }
        }

        return actions;
    }

    private static List<Action> createInstallActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        if (!input.isNull(TestFormatJsonMapping.INSTALLS_KEY)) {
            JSONArray installsArray = input.getJSONArray(TestFormatJsonMapping.INSTALLS_KEY);
            for (int j = 0; j < installsArray.length(); j++) {
                InstallApp installApp = new InstallApp(installsArray.getJSONObject(j));
                actions.add(installApp);
            }
        }

        return actions;
    }

    private static List<Action> createUninstallActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        if (!input.isNull(TestFormatJsonMapping.UNINSTALLS_KEY)) {
            JSONArray uninstallsArray = input.getJSONArray(TestFormatJsonMapping.UNINSTALLS_KEY);
            for (int j = 0; j < uninstallsArray.length(); j++) {
                UninstallApp uninstallApp = new UninstallApp(uninstallsArray.getJSONObject(j));
                actions.add(uninstallApp);
            }
        }

        return actions;
    }

    private static ReportObjects getExpectedOutput(JSONObject output) throws JSONException {
        List<JSONObject> eventReportObjects = new ArrayList<>();
        JSONArray eventReportObjectsArray = output.getJSONArray(
                TestFormatJsonMapping.EVENT_REPORT_OBJECTS_KEY);
        for (int i = 0; i < eventReportObjectsArray.length(); i++) {
            JSONObject obj = eventReportObjectsArray.getJSONObject(i);
            String adTechDomain = obj.getString(TestFormatJsonMapping.REPORT_TO_KEY);
            eventReportObjects.add(obj.put(TestFormatJsonMapping.REPORT_TO_KEY, adTechDomain));
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
        db.delete("msmt_aggregate_report", null, null);
    }

    void processAction(RegisterSource sourceRegistration) throws IOException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertEquals(
                "MeasurementImpl.register source failed",
                RESULT_OK,
                mMeasurementImpl.register(
                        sourceRegistration.mRegistrationRequest, sourceRegistration.mTimestamp));
    }

    void processAction(RegisterWebSource sourceRegistration) throws IOException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertEquals(
                "MeasurementImpl.registerWebSource failed",
                RESULT_OK,
                mMeasurementImpl.registerWebSource(
                        sourceRegistration.mRegistrationRequest, sourceRegistration.mTimestamp));
    }

    void processAction(RegisterWebTrigger triggerRegistration) throws IOException {
        prepareRegistrationServer(triggerRegistration);
        Assert.assertEquals(
                "MeasurementImpl.registerWebTrigger failed",
                RESULT_OK,
                mMeasurementImpl.registerWebTrigger(
                        triggerRegistration.mRegistrationRequest, triggerRegistration.mTimestamp));
        Assert.assertTrue(
                "AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
    }

    void processAction(RegisterTrigger triggerRegistration) throws IOException {
        prepareRegistrationServer(triggerRegistration);
        Assert.assertEquals(
                "MeasurementImpl.register trigger failed",
                RESULT_OK,
                mMeasurementImpl.register(
                        triggerRegistration.mRegistrationRequest, triggerRegistration.mTimestamp));
        Assert.assertTrue("AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
    }

    void processAction(InstallApp installApp) {
        Assert.assertTrue(
                "measurementDao.doInstallAttribution failed",
                sDatastoreManager.runInTransaction(
                        measurementDao ->
                                measurementDao.doInstallAttribution(
                                        installApp.mUri, installApp.mTimestamp)));
    }

    void processAction(UninstallApp uninstallApp) {
        Assert.assertTrue("measurementDao.undoInstallAttribution failed",
                sDatastoreManager.runInTransaction(
                    measurementDao -> {
                        measurementDao.deleteAppRecords(uninstallApp.mUri);
                        measurementDao.undoInstallAttribution(uninstallApp.mUri);
                    }));
    }

    void evaluateResults() throws JSONException {
        sortEventReportObjects(mExpectedOutput.mEventReportObjects);
        sortEventReportObjects(mActualOutput.mEventReportObjects);
        sortAggregateReportObjects(mExpectedOutput.mAggregateReportObjects);
        sortAggregateReportObjects(mActualOutput.mAggregateReportObjects);
        Assert.assertTrue(getTestFailureMessage(mExpectedOutput, mActualOutput),
                areEqual(mExpectedOutput, mActualOutput));
    }
}
