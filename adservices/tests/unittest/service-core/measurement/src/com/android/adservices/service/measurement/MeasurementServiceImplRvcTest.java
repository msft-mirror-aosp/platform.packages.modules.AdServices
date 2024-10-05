/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;
import static android.adservices.measurement.MeasurementManager.MEASUREMENT_API_STATE_DISABLED;

import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.adservices.common.CallerMetadata;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.net.Uri;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.OnResultSyncCallback;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Collections;

@RequiresSdkRange(atMost = RVC)
public final class MeasurementServiceImplRvcTest extends AdServicesExtendedMockitoTestCase {

    private static final Uri APP_DESTINATION = Uri.parse("android-app://test.app-destination");
    private static final String APP_PACKAGE_NAME = "app.package.name";
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://registration-uri.test");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination-uri.test");
    @Mock private AdServicesLogger mMockAdServicesLogger;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;
    @Mock private ConsentManager mMockConsentManager;
    @Mock private MeasurementImpl mMockMeasurementImpl;
    @Mock private Throttler mMockThrottler;
    @Mock private DevContextFilter mDevContextFilter;

    private MeasurementServiceImpl mMeasurementServiceImpl;

    @Before
    public void setUp() {
        mMeasurementServiceImpl = createServiceWithMocks();
    }

    @Test
    public void testRegister_onR_invokesCallbackOnFailure() throws Exception {
        SyncMeasurementCallback callback = new SyncMeasurementCallback();
        mMeasurementServiceImpl.register(
                createRegistrationSourceRequest(), createCallerMetadata(), callback);

        verify(mMockMeasurementImpl, never()).register(any(), anyBoolean(), anyLong());

        assertFailureReceived(callback, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testRegisterWebSource_onR_invokesCallbackOnFailure() throws Exception {
        SyncMeasurementCallback callback = new SyncMeasurementCallback();
        mMeasurementServiceImpl.registerWebSource(
                createWebSourceRegistrationRequest(), createCallerMetadata(), callback);

        assertFailureReceived(callback, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testRegisterSource_onR_invokesCallbackOnFailure() throws Exception {
        SyncMeasurementCallback callback = new SyncMeasurementCallback();
        mMeasurementServiceImpl.registerSource(
                createSourcesRegistrationRequest(), createCallerMetadata(), callback);

        assertFailureReceived(callback, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testRegisterWebTrigger_onR_invokesCallbackOnFailure() throws Exception {
        SyncMeasurementCallback callback = new SyncMeasurementCallback();
        mMeasurementServiceImpl.registerWebTrigger(
                createWebTriggerRegistrationRequest(), createCallerMetadata(), callback);

        assertFailureReceived(callback, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testDeleteRegistrations_onR_invokesCallbackOnFailure() throws Exception {
        SyncMeasurementCallback callback = new SyncMeasurementCallback();
        mMeasurementServiceImpl.deleteRegistrations(
                createDeletionRequest(), createCallerMetadata(), callback);

        assertFailureReceived(callback, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testSchedulePeriodicJobs_onR_invokesCallbackOnFailure() throws Exception {
        SyncMeasurementCallback callback = new SyncMeasurementCallback();

        mMeasurementServiceImpl = createServiceWithMocks();
        mMeasurementServiceImpl.schedulePeriodicJobs(callback);

        assertFailureReceived(callback, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testGetMeasurementApiStatus_onR_disabled() throws Exception {
        SyncMeasurementApiStatusCallback callback = new SyncMeasurementApiStatusCallback();

        mMeasurementServiceImpl.getMeasurementApiStatus(
                createStatusParam(), createCallerMetadata(), callback);

        Integer result = callback.assertResultReceived();
        assertWithMessage("result").that(result).isNotNull();
        expect.withMessage("api status")
                .that(result.intValue())
                .isEqualTo(MEASUREMENT_API_STATE_DISABLED);
    }

    private void assertFailureReceived(SyncMeasurementCallback callback, int status)
            throws Exception {
        MeasurementErrorResponse failure = callback.assertFailureReceived();
        assertWithMessage("failure").that(failure).isNotNull();
        expect.withMessage("status").that(failure.getStatusCode()).isEqualTo(status);
    }

    private RegistrationRequest createRegistrationSourceRequest() {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_SOURCE,
                        REGISTRATION_URI,
                        APP_PACKAGE_NAME,
                        SDK_PACKAGE_NAME)
                .build();
    }

    private SourceRegistrationRequestInternal createSourcesRegistrationRequest() {
        SourceRegistrationRequest sourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(Collections.singletonList(REGISTRATION_URI))
                        .build();
        return new SourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest, APP_PACKAGE_NAME, SDK_PACKAGE_NAME, 10000L)
                .build();
    }

    private WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest() {
        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(
                                        new WebSourceParams.Builder(REGISTRATION_URI)
                                                .setDebugKeyAllowed(true)
                                                .build()),
                                Uri.parse("android-app//com.example"))
                        .setWebDestination(WEB_DESTINATION)
                        .setAppDestination(APP_DESTINATION)
                        .build();
        return new WebSourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest, APP_PACKAGE_NAME, SDK_PACKAGE_NAME, 10000L)
                .build();
    }

    private WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest() {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(
                                        new WebTriggerParams.Builder(REGISTRATION_URI)
                                                .setDebugKeyAllowed(true)
                                                .build()),
                                Uri.parse("android-app://com.example"))
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder(
                        webTriggerRegistrationRequest, APP_PACKAGE_NAME, SDK_PACKAGE_NAME)
                .build();
    }

    private DeletionParam createDeletionRequest() {
        return new DeletionParam.Builder(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Instant.MIN,
                        Instant.MAX,
                        APP_PACKAGE_NAME,
                        SDK_PACKAGE_NAME)
                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                .build();
    }

    private CallerMetadata createCallerMetadata() {
        return new CallerMetadata.Builder()
                .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                .build();
    }

    private StatusParam createStatusParam() {
        return new StatusParam.Builder(APP_PACKAGE_NAME, SDK_PACKAGE_NAME).build();
    }

    private MeasurementServiceImpl createServiceWithMocks() {
        return new MeasurementServiceImpl(
                mMockMeasurementImpl,
                mMockContext,
                Clock.getInstance(),
                mMockConsentManager,
                mMockThrottler,
                new CachedFlags(mMockFlags),
                mMockDebugFlags,
                mMockAdServicesLogger,
                mMockAppImportanceFilter,
                mDevContextFilter,
                AdServicesExecutors.getBackgroundExecutor());
    }

    private static final class SyncMeasurementCallback
            extends FailableOnResultSyncCallback<Void, MeasurementErrorResponse>
            implements IMeasurementCallback {

        @Override
        public void onResult() throws RemoteException {}
    }

    private static final class SyncMeasurementApiStatusCallback
            extends OnResultSyncCallback<Integer> implements IMeasurementApiStatusCallback {

        @Override
        public void onResult(int result) throws RemoteException {
            super.onResult(result);
        }
    }
}
