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

import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;

import android.adservices.measurement.RegistrationRequest;
import android.net.Uri;
import android.util.Log;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.measurement.util.Web;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 *
 * Tests in assets/msmt_interop_tests/ directory were copied from
 * https://source.chromium.org/chromium/chromium/src/+/main:content/test/data/attribution_reporting/interop/
 * on October 15, 2022
 */
@RunWith(Parameterized.class)
public class E2EInteropMockTest extends E2EMockTest {
    private static final String LOG_TAG = "msmt_e2e_interop_mock_test";
    private static final String TEST_DIR_NAME = "msmt_interop_tests";
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String DEFAULT_EVENT_TRIGGER_DATA = "[]";

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> getData() throws IOException, JSONException {
        return data(TEST_DIR_NAME);
    }

    public E2EInteropMockTest(Collection<Action> actions, ReportObjects expectedOutput,
            PrivacyParamsProvider privacyParamsProvider, String name) throws DatastoreException {
        super(actions, expectedOutput, privacyParamsProvider, name);
        mAttributionHelper = TestObjectProvider.getAttributionJobHandler(sDatastoreManager);
        mMeasurementImpl =
                TestObjectProvider.getMeasurementImpl(
                        sDatastoreManager,
                        mClickVerifier,
                        mFlags,
                        mMeasurementDataDeleter,
                        sEnrollmentDao);
        mAsyncRegistrationQueueRunner =
                TestObjectProvider.getAsyncRegistrationQueueRunner(
                        TestObjectProvider.Type.DENOISED,
                        sDatastoreManager,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        sEnrollmentDao);
    }

    @Override
    void processAction(RegisterSource sourceRegistration) throws IOException {
        RegistrationRequest request = sourceRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            Source source = getSource(
                    sourceRegistration.getPublisher(),
                    sourceRegistration.mTimestamp,
                    uri,
                    request,
                    getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri));
            Assert.assertTrue(
                    "measurementDao.insertSource failed",
                    sDatastoreManager.runInTransaction(
                            measurementDao -> {
                                if (AsyncRegistrationQueueRunner.isSourceAllowedToInsert(
                                        source,
                                        source.getPublisher(),
                                        EventSurfaceType.WEB,
                                        measurementDao)) {
                                    measurementDao.insertSource(source);
                                }
                            }));
        }
    }

    @Override
    void processAction(RegisterTrigger triggerRegistration) throws IOException {
        RegistrationRequest request = triggerRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            Trigger trigger = getTrigger(
                    triggerRegistration.getDestination(),
                    triggerRegistration.mTimestamp,
                    uri,
                    request,
                    getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri));
            Assert.assertTrue(
                    "measurementDao.insertTrigger failed",
                    sDatastoreManager.runInTransaction(
                            measurementDao ->
                                    measurementDao.insertTrigger(trigger)));
        }
        Assert.assertTrue("AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
    }

    private static Source getSource(String publisher, long timestamp, String uri,
            RegistrationRequest request, Map<String, List<String>> headers) {
        try {
            Source.Builder sourceBuilder = new Source.Builder();
            String enrollmentId =
                    Enrollment.maybeGetEnrollmentId(Uri.parse(uri), sEnrollmentDao).get();
            sourceBuilder.setEnrollmentId(enrollmentId);
            sourceBuilder.setPublisher(Uri.parse(publisher));
            sourceBuilder.setPublisherType(EventSurfaceType.WEB);
            sourceBuilder.setEventTime(timestamp);
            sourceBuilder.setSourceType(getSourceType(request));
            sourceBuilder.setAttributionMode(Source.AttributionMode.TRUTHFULLY);
            sourceBuilder.setRegistrant(getRegistrant(request.getAppPackageName()));
            List<String> field = headers.get("Attribution-Reporting-Register-Source");
            JSONObject json = new JSONObject(field.get(0));
            sourceBuilder.setEventId(new UnsignedLong(json.getString("source_event_id")));
            if (!json.isNull("expiry")) {
                long offset =
                        TimeUnit.SECONDS.toMillis(
                                extractValidNumberInRange(
                                        json.getLong("expiry"),
                                        MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
                sourceBuilder.setExpiryTime(timestamp + offset);
            } else {
                sourceBuilder.setExpiryTime(
                        timestamp
                                + TimeUnit.SECONDS.toMillis(
                                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
            }
            if (!json.isNull("priority")) {
                sourceBuilder.setPriority(json.getLong("priority"));
            }
            if (!json.isNull("debug_key")) {
                sourceBuilder.setDebugKey(new UnsignedLong(json.getString("debug_key")));
            }
            if (!json.isNull("filter_data")) {
                sourceBuilder.setFilterData(json.getJSONObject("filter_data").toString());
            }
            sourceBuilder.setWebDestination(Web.topPrivateDomainAndScheme(
                    Uri.parse(json.getString("destination"))).get());
            if (!json.isNull("aggregation_keys")) {
                sourceBuilder.setAggregateSource(json.getJSONObject("aggregation_keys").toString());
            }
            return sourceBuilder.build();
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to parse source registration. %s", e);
            return null;
        }
    }

    private static Trigger getTrigger(String destination, long timestamp, String uri,
            RegistrationRequest request, Map<String, List<String>> headers) {
        try {
            Trigger.Builder triggerBuilder = new Trigger.Builder();
            String enrollmentId =
                    Enrollment.maybeGetEnrollmentId(Uri.parse(uri), sEnrollmentDao).get();
            triggerBuilder.setEnrollmentId(enrollmentId);
            triggerBuilder.setAttributionDestination(Uri.parse(destination));
            triggerBuilder.setDestinationType(EventSurfaceType.WEB);
            triggerBuilder.setTriggerTime(timestamp);
            triggerBuilder.setRegistrant(getRegistrant(request.getAppPackageName()));
            List<String> field = headers.get("Attribution-Reporting-Register-Trigger");
            JSONObject json = new JSONObject(field.get(0));
            String eventTriggerData = DEFAULT_EVENT_TRIGGER_DATA;
            if (!json.isNull("event_trigger_data")) {
                eventTriggerData = json.getJSONArray("event_trigger_data").toString();
            }
            triggerBuilder.setEventTriggers(eventTriggerData);
            if (!json.isNull("aggregatable_trigger_data")) {
                triggerBuilder.setAggregateTriggerData(
                        json.getJSONArray("aggregatable_trigger_data").toString());
            }
            if (!json.isNull("aggregatable_values")) {
                triggerBuilder.setAggregateValues(
                        json.getJSONObject("aggregatable_values").toString());
            }
            if (!json.isNull("filters")) {
                triggerBuilder.setFilters(json.getString("filters"));
            }
            if (!json.isNull("not_filters")) {
                triggerBuilder.setNotFilters(json.getString("not_filters"));
            }
            if (!json.isNull("debug_key")) {
                triggerBuilder.setDebugKey(new UnsignedLong(json.getString("debug_key")));
            }
            return triggerBuilder.build();
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to parse trigger registration. %s", e);
            return null;
        }
    }

    private static Source.SourceType getSourceType(RegistrationRequest request) {
        return request.getInputEvent() == null
                ? Source.SourceType.EVENT
                : Source.SourceType.NAVIGATION;
    }

    private static Uri getRegistrant(String packageName) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
    }

    private static long extractValidNumberInRange(long value, long lowerLimit, long upperLimit) {
        if (value < lowerLimit) {
            return lowerLimit;
        } else if (value > upperLimit) {
            return upperLimit;
        }

        return value;
    }
}
