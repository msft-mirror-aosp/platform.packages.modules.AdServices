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
import android.os.RemoteException;
import android.provider.DeviceConfig;

import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.registration.AsyncFetchStatus;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.util.Enrollment;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 *
 * <p>Tests in assets/msmt_interop_tests/ directory were copied from Chromium
 * src/content/test/data/attribution_reporting/interop April 21, 2023. Files destination_limit.json,
 * max_aggregatable_reports_per_source.json, parse_failures.json, rate_limit_max_attributions.json,
 * event_level_report_time.json, aggregatable_report_window.json,
 * event_source_event_report_windows.json and
 * rate_limit_max_reporting_origins_per_source_reporting_site.json were updated with GitHub commit
 * 71b156e24f8ef67378fbfba35edfb2fef514baf0
 */
@RunWith(Parameterized.class)
public class E2EInteropMockTest extends E2EMockTest {
    private static final String TEST_DIR_NAME = "msmt_interop_tests";
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final List<AsyncFetchStatus.EntityStatus> sParsingErrors = List.of(
            AsyncFetchStatus.EntityStatus.PARSING_ERROR,
            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
    private static final Map<String, String> sApiConfigPhFlags =
            Map.of("max_event_info_gain", "measurement_flex_api_max_information_gain_event");

    private static String preprocessor(String json) {
        return json.replaceAll("\\.test(?=[\"\\/])", ".com")
                // Remove comments
                .replaceAll("^\\s*\\/\\/.+\\n", "")
                .replaceAll("\"destination\":", "\"web_destination\":");
    }

    private static Map<String, String> sPhFlagsForInterop = Map.of(
            // TODO (b/295382171): remove this after the flag is removed.
            "measurement_enable_max_aggregate_reports_per_source", "true");

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> getData() throws IOException, JSONException {
        return data(TEST_DIR_NAME, E2EInteropMockTest::preprocessor, sApiConfigPhFlags);
    }

    public E2EInteropMockTest(
            Collection<Action> actions,
            ReportObjects expectedOutput,
            ParamsProvider paramsProvider,
            String name,
            Map<String, String> phFlagsMap)
            throws RemoteException {
        super(
                actions,
                expectedOutput,
                paramsProvider,
                name,
                (
                        (Supplier<Map<String, String>>) () -> {
                            for (String key : sPhFlagsForInterop.keySet()) {
                                phFlagsMap.put(key, sPhFlagsForInterop.get(key));
                            }
                            return phFlagsMap;
                        }
                ).get()
        );
        mAttributionHelper =
                TestObjectProvider.getAttributionJobHandler(
                        mDatastoreManager, mFlags, mErrorLogger);
        mMeasurementImpl =
                TestObjectProvider.getMeasurementImpl(
                        mDatastoreManager,
                        mClickVerifier,
                        mMeasurementDataDeleter,
                        mMockContentResolver);
        mAsyncRegistrationQueueRunner =
                TestObjectProvider.getAsyncRegistrationQueueRunner(
                        TestObjectProvider.Type.DENOISED,
                        mDatastoreManager,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        mDebugReportApi,
                        mFlags);
    }

    @Before
    public void setup() {
        // Chromium does not have a flag at dynamic noising based on expiry but Android does, so it
        // needs to be enabled.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enable_configurable_event_reporting_windows",
                "true",
                false);
    }

    @Override
    void processAction(RegisterSource sourceRegistration) throws JSONException, IOException {
        RegistrationRequest request = sourceRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            insertSource(
                    sourceRegistration.getPublisher(),
                    sourceRegistration.mTimestamp,
                    uri,
                    sourceRegistration.mArDebugPermission,
                    request,
                    getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri));
        }
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        if (sourceRegistration.mDebugReporting) {
            processActualDebugReportApiJob();
        }
    }

    @Override
    void processAction(RegisterTrigger triggerRegistration) throws IOException, JSONException {
        RegistrationRequest request = triggerRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            insertTrigger(
                    triggerRegistration.getDestination(),
                    triggerRegistration.mTimestamp,
                    uri,
                    triggerRegistration.mArDebugPermission,
                    request,
                    getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri));
        }
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        Assert.assertTrue(
                "AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
        // Attribution can happen up to an hour after registration call, due to AsyncRegistration
        processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
        if (triggerRegistration.mDebugReporting) {
            processActualDebugReportApiJob();
        }
    }

    private void insertSource(
            String publisher,
            long timestamp,
            String uri,
            boolean arDebugPermission,
            RegistrationRequest request,
            Map<String, List<String>> headers) {
        String enrollmentId =
                Enrollment.getValidEnrollmentId(
                                Uri.parse(uri),
                                request.getAppPackageName(),
                                mEnrollmentDao,
                                sContext,
                                mFlags)
                        .get();
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setTopOrigin(Uri.parse(publisher))
                        .setOsDestination(null)
                        .setWebDestination(null)
                        .setRegistrant(getRegistrant(request.getAppPackageName()))
                        .setRequestTime(timestamp)
                        .setSourceType(getSourceType(request))
                        .setType(AsyncRegistration.RegistrationType.WEB_SOURCE)
                        .setAdIdPermission(true)
                        .setDebugKeyAllowed(arDebugPermission)
                        .setRegistrationUri(Uri.parse(uri))
                        .build();

        AsyncFetchStatus status = new AsyncFetchStatus();
        Optional<Source> maybeSource = mAsyncSourceFetcher
                .parseSource(asyncRegistration, enrollmentId, headers, status);

        if (maybeSource.isPresent()) {
            Assert.assertTrue(
                    "mAsyncRegistrationQueueRunner.storeSource failed",
                    mDatastoreManager.runInTransaction(
                            measurementDao ->
                                    mAsyncRegistrationQueueRunner.storeSource(
                                            maybeSource.get(), asyncRegistration, measurementDao)));
        } else {
            Assert.assertTrue(sParsingErrors.contains(status.getEntityStatus()));
        }
    }

    private void insertTrigger(
            String destination,
            long timestamp,
            String uri,
            boolean arDebugPermission,
            RegistrationRequest request,
            Map<String, List<String>> headers) {
        String enrollmentId =
                Enrollment.getValidEnrollmentId(
                                Uri.parse(uri),
                                request.getAppPackageName(),
                                mEnrollmentDao,
                                sContext,
                                mFlags)
                        .get();
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setTopOrigin(Uri.parse(destination))
                        .setRegistrant(getRegistrant(request.getAppPackageName()))
                        .setRequestTime(timestamp)
                        .setType(AsyncRegistration.RegistrationType.WEB_TRIGGER)
                        .setAdIdPermission(true)
                        .setDebugKeyAllowed(arDebugPermission)
                        .setRegistrationUri(Uri.parse(uri))
                        .build();

        AsyncFetchStatus status = new AsyncFetchStatus();
        Optional<Trigger> maybeTrigger = mAsyncTriggerFetcher
                .parseTrigger(asyncRegistration, enrollmentId, headers, status);

        if (maybeTrigger.isPresent()) {
            Assert.assertTrue(
                    "mAsyncRegistrationQueueRunner.storeTrigger failed",
                    mDatastoreManager.runInTransaction(
                            measurementDao ->
                                    mAsyncRegistrationQueueRunner.storeTrigger(
                                            maybeTrigger.get(), measurementDao)));
        } else {
            Assert.assertTrue(sParsingErrors.contains(status.getEntityStatus()));
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
}
