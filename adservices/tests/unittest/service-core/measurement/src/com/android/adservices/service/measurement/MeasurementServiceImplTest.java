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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;
import static android.adservices.measurement.MeasurementManager.MEASUREMENT_API_STATE_DISABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.adservices.common.AdServicesStatusUtils;
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
import android.os.Binder;
import android.os.SystemClock;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.download.MddJob;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJob;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.CountUniqueReportingJob;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.adservices.shared.util.Clock;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Unit tests for {@link MeasurementServiceImpl} */
@SmallTest
@MockStatic(Binder.class)
@MockStatic(FlagsFactory.class)
@MockStatic(PermissionHelper.class)
@SpyStatic(AggregateReportingJobService.class)
@SpyStatic(AggregateFallbackReportingJobService.class)
@SpyStatic(AttributionJobService.class)
@SpyStatic(AttributionFallbackJobService.class)
@SpyStatic(EventReportingJobService.class)
@SpyStatic(EventFallbackReportingJobService.class)
@SpyStatic(DeleteExpiredJobService.class)
@SpyStatic(DeleteUninstalledJobService.class)
@SpyStatic(MddJob.class)
@SpyStatic(EncryptionKeyJobService.class)
@SpyStatic(AsyncRegistrationQueueJobService.class)
@SpyStatic(AsyncRegistrationFallbackJob.class)
@SpyStatic(VerboseDebugReportingFallbackJobService.class)
@SpyStatic(DebugReportingFallbackJobService.class)
@SpyStatic(CountUniqueReportingJob.class)
public final class MeasurementServiceImplTest extends AdServicesExtendedMockitoTestCase {

    private static final Uri APP_DESTINATION = Uri.parse("android-app://test.app-destination");
    private static final String APP_PACKAGE_NAME = "app.package.name";
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://registration-uri.test");
    private static final Uri LOCALHOST = Uri.parse("https://localhost");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final int TIMEOUT = 5_000;
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination-uri.test");
    private static final int CALLING_UID = 1;
    @Mock private AdServicesLogger mMockAdServicesLogger;

    private MeasurementServiceImpl mMeasurementServiceImpl;

    @Test
    public void testRegisterSource_noOp_apiDisabled() throws Exception {
        runWithMocks(() -> registerSourceAndAssertFailure(STATUS_ADSERVICES_DISABLED));
    }

    @Test
    public void testRegisterSources_noOp_apiDisabled() throws Exception {
        runWithMocks(() -> registerSourcesAndAssertFailure(STATUS_ADSERVICES_DISABLED));
    }

    @Test
    public void testRegisterWebSource_noOp_apiDisabled() throws Exception {
        runWithMocks(() -> registerWebSourceAndAssertFailure(STATUS_ADSERVICES_DISABLED));
    }

    @Test
    public void testRegisterTrigger_noOp_apiDisabled() throws Exception {
        runWithMocks(() -> registerTriggerAndAssertFailure(STATUS_ADSERVICES_DISABLED));
    }

    @Test
    public void testRegisterWebTrigger_noOp_apiDisabled() throws Exception {
        runWithMocks(() -> registerWebTriggerAndAssertFailure(STATUS_ADSERVICES_DISABLED));
    }

    @Test
    public void testDeleteRegistrations_noOp_apiDisabled() throws Exception {
        runWithMocks(() -> deleteRegistrationsAndAssertFailure(STATUS_ADSERVICES_DISABLED));
    }

    @Test
    public void testGetMsmtApiStatus_noOp_apiDisabled() throws Exception {
        runWithMocks(() -> getMeasurementApiStatusAndAssertFailure());
    }

    @Test
    public void testSchedulePeriodicJobs_noOp_JobsNotScheduled() throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mMeasurementServiceImpl = createServiceWithMocks();
        SyncSchedulePeriodicJobsCallback callback = new SyncSchedulePeriodicJobsCallback();
        mMeasurementServiceImpl.schedulePeriodicJobs(callback);
        callback.assertResultReceived();
        assertJobsNotScheduled();
    }

    private void registerSourceAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerSourceAndAssertFailure(status, createRegistrationSourceRequest());
    }

    private void registerSourceAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            RegistrationRequest registrationSourceRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.register(
                registrationSourceRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
        assertStatusCodeLogged();
    }

    private void registerTriggerAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerTriggerAndAssertFailure(status, createRegistrationTriggerRequest());
    }

    private void registerTriggerAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            RegistrationRequest registrationTriggerRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.register(
                registrationTriggerRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
        assertStatusCodeLogged();
    }

    private void deleteRegistrationsAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementServiceImpl.deleteRegistrations(
                createDeletionRequest(),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse errorResponse) {
                        errorContainer.add(errorResponse);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
        assertStatusCodeLogged();
    }

    private void getMeasurementApiStatusAndAssertFailure() throws InterruptedException {
        final CountDownLatch countDownLatchAny = new CountDownLatch(1);
        final AtomicInteger resultWrapper = new AtomicInteger();

        mMeasurementServiceImpl.getMeasurementApiStatus(
                createStatusParam(),
                createCallerMetadata(),
                new IMeasurementApiStatusCallback.Stub() {
                    @Override
                    public void onResult(int result) {
                        resultWrapper.set(result);
                        countDownLatchAny.countDown();
                    }
                });

        assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(resultWrapper.get()).isEqualTo(MEASUREMENT_API_STATE_DISABLED);
    }

    private void registerWebTriggerAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerWebTriggerAndAssertFailure(status, createWebTriggerRegistrationRequest());
    }

    private void registerWebTriggerAndAssertFailure(@AdServicesStatusUtils.StatusCode int status,
            WebTriggerRegistrationRequestInternal webTriggerRegistrationRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.registerWebTrigger(
                webTriggerRegistrationRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
        assertStatusCodeLogged();
    }

    private RegistrationRequest createRegistrationSourceRequest() {
        return createRegistrationSourceRequest(false);
    }

    private RegistrationRequest createRegistrationSourceRequest(boolean isLocalhost) {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_SOURCE,
                        isLocalhost ? LOCALHOST : REGISTRATION_URI,
                        APP_PACKAGE_NAME,
                        SDK_PACKAGE_NAME)
                .build();
    }

    private RegistrationRequest createRegistrationTriggerRequest() {
        return createRegistrationTriggerRequest(false);
    }

    private RegistrationRequest createRegistrationTriggerRequest(boolean isLocalhost) {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_TRIGGER,
                        isLocalhost ? LOCALHOST : REGISTRATION_URI,
                        APP_PACKAGE_NAME,
                        SDK_PACKAGE_NAME)
                .build();
    }

    private void registerWebSourceAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerWebSourceAndAssertFailure(status, createWebSourceRegistrationRequest());
    }

    private void registerWebSourceAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            WebSourceRegistrationRequestInternal webSourceRegistrationRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.registerWebSource(
                webSourceRegistrationRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
        assertStatusCodeLogged();
    }

    private void registerSourcesAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerSourcesAndAssertFailure(status, createSourcesRegistrationRequest(false));
    }

    private void registerSourcesAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            SourceRegistrationRequestInternal sourceRegistrationRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.registerSource(
                sourceRegistrationRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
        assertStatusCodeLogged();
    }

    private SourceRegistrationRequestInternal createSourcesRegistrationRequest(
            boolean isLocalhost) {
        SourceRegistrationRequest sourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(
                                Collections.singletonList(
                                        isLocalhost ? LOCALHOST : REGISTRATION_URI))
                        .build();
        return new SourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest, APP_PACKAGE_NAME, SDK_PACKAGE_NAME, 10000L)
                .build();
    }

    private WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest() {
        return createWebSourceRegistrationRequest(false);
    }

    private WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest(
            boolean isLocalhost) {
        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(
                                        new WebSourceParams.Builder(
                                                        isLocalhost ? LOCALHOST : REGISTRATION_URI)
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
        return createWebTriggerRegistrationRequest(false);
    }

    private WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest(
            boolean isLocalhost) {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(
                                        new WebTriggerParams.Builder(
                                                        isLocalhost ? LOCALHOST : REGISTRATION_URI)
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

    private void assertStatusCodeLogged() {
        ArgumentCaptor<ApiCallStats> captorStatus = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mMockAdServicesLogger, timeout(TIMEOUT)).logApiCallStats(captorStatus.capture());
        Assert.assertEquals(APP_PACKAGE_NAME, captorStatus.getValue().getAppPackageName());
        Assert.assertEquals(SDK_PACKAGE_NAME, captorStatus.getValue().getSdkPackageName());
        Assert.assertEquals(STATUS_ADSERVICES_DISABLED, captorStatus.getValue().getResultCode());
    }

    private MeasurementServiceImpl createServiceWithMocks() {
        return new MeasurementServiceImpl(Clock.getInstance(), mMockAdServicesLogger);
    }

    private void runWithMocks(final TestUtils.RunnableWithThrow execute) throws Exception {
            mMeasurementServiceImpl = createServiceWithMocks();
            execute.run();
    }

    private void assertJobsNotScheduled() {
        ExtendedMockito.verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        ExtendedMockito.verify(
                () -> AggregateFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()),
                never());
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        ExtendedMockito.verify(
                () -> AttributionFallbackJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        ExtendedMockito.verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        ExtendedMockito.verify(
                () -> EventFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()),
                never());
        ExtendedMockito.verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        ExtendedMockito.verify(
                () -> DeleteUninstalledJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        ExtendedMockito.verify(MddJob::scheduleAllMddJobs, never());
        ExtendedMockito.verify(
                () -> EncryptionKeyJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        ExtendedMockito.verify(
                () -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()),
                never());
        ExtendedMockito.verify(AsyncRegistrationFallbackJob::schedule, never());
        ExtendedMockito.verify(
                () -> VerboseDebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()),
                never());
        ExtendedMockito.verify(
                () -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()),
                never());
        ExtendedMockito.verify(CountUniqueReportingJob::schedule, never());
    }

    private static final class SyncSchedulePeriodicJobsCallback
            extends FailableOnResultSyncCallback<Void, MeasurementErrorResponse>
            implements IMeasurementCallback {
        @Override
        public void onResult() {
            internalSetCalled("onResult()");
        }
    }
}
