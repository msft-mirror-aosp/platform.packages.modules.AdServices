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

import android.adservices.measurement.RegistrationRequest;
import android.net.Uri;
import android.util.Log;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.util.Enrollment;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 *
 * Tests in assets/msmt_interop_tests/ directory were copied from Chromium
 * src/content/test/data/attribution_reporting/interop
 * Friday February 17, 2023
 */
@RunWith(Parameterized.class)
public class E2EInteropMockTest extends E2EMockTest {
    private static final String LOG_TAG = "msmt_e2e_interop_mock_test";
    private static final String TEST_DIR_NAME = "msmt_interop_tests";
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String DEFAULT_EVENT_TRIGGER_DATA = "[]";

    private static String preprocessor(String json) {
        return json
                .replaceAll("\\.test(?=[\"\\/])", ".com")
                // Remove comments
                .replaceAll("^\\s*\\/\\/.+\\n", "")
                .replaceAll("\"destination\":", "\"web_destination\":");
    }

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> getData() throws IOException, JSONException {
        return data(TEST_DIR_NAME, E2EInteropMockTest::preprocessor);
    }

    public E2EInteropMockTest(Collection<Action> actions, ReportObjects expectedOutput,
            ParamsProvider paramsProvider, String name) throws DatastoreException {
        super(actions, expectedOutput, paramsProvider, name);
        mAttributionHelper = TestObjectProvider.getAttributionJobHandler(sDatastoreManager, mFlags);
        mMeasurementImpl =
                TestObjectProvider.getMeasurementImpl(
                        sDatastoreManager,
                        mClickVerifier,
                        mMeasurementDataDeleter,
                        sEnrollmentDao);
        mAsyncRegistrationQueueRunner =
                TestObjectProvider.getAsyncRegistrationQueueRunner(
                        TestObjectProvider.Type.DENOISED,
                        sDatastoreManager,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        sEnrollmentDao,
                        mDebugReportApi);
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
                                        measurementDao,
                                        mDebugReportApi)) {
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

    private Source getSource(String publisher, long timestamp, String uri,
            RegistrationRequest request, Map<String, List<String>> headers) {
        String enrollmentId = Enrollment.maybeGetEnrollmentId(Uri.parse(uri), sEnrollmentDao).get();
        // The Source parser compares the destination from the web request to the one provided in
        // the headers in order to allow either a web or app destination (otherwise, only an app
        // destination would be allowed). Since the test runner interprets the test JSON as an app
        // request, not web, we need to get the destination from the JSON, not from the request
        // object.
        List<String> field = headers.get("Attribution-Reporting-Register-Source");
        JSONObject json;
        Uri webDestination;
        try {
            json = new JSONObject(field.get(0));
            webDestination = Uri.parse(json.getString("web_destination"));
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to parse source header. %s", e);
            return null;
        }
        List<Source> sourceWrapper = new ArrayList<>();
        mAsyncSourceFetcher.parseSource(
                UUID.randomUUID().toString(),
                Uri.parse(publisher),
                enrollmentId,
                /* appDestination */ null,
                webDestination,
                getRegistrant(request.getAppPackageName()),
                timestamp,
                getSourceType(request),
                /* shouldValidateDestinationWebSource */ true,
                /* shouldOverrideDestinationAppSource */ false,
                headers,
                sourceWrapper,
                /* isWebSource */ true,
                /* adIdPermission */ true,
                /* arDebugPermission */ true);
        return sourceWrapper.get(0);
    }

    private Trigger getTrigger(String destination, long timestamp, String uri,
            RegistrationRequest request, Map<String, List<String>> headers) {
        String enrollmentId = Enrollment.maybeGetEnrollmentId(Uri.parse(uri), sEnrollmentDao).get();
        List<Trigger> triggerWrapper = new ArrayList<>();
        mAsyncTriggerFetcher.parseTrigger(
                Uri.parse(destination),
                getRegistrant(request.getAppPackageName()),
                enrollmentId,
                timestamp,
                headers,
                triggerWrapper,
                AsyncRegistration.RegistrationType.WEB_TRIGGER,
                /* adIdPermission */ true,
                /* arDebugPermission */ true);
        return triggerWrapper.get(0);
    }

    private static Source.SourceType getSourceType(RegistrationRequest request) {
        return request.getInputEvent() == null
                ? Source.SourceType.EVENT
                : Source.SourceType.NAVIGATION;
    }

    private static Uri getRegistrant(String packageName) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
    }
}
