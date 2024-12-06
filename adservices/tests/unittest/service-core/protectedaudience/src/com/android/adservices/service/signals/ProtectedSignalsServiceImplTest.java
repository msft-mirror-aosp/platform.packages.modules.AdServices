/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals;

import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_NOT_GIVEN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_REVOKED_FOR_APP_AFTER_SETTING_FLEDGE_USE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_GET_CALLING_UID_ILLEGAL_STATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_INVALID_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_SERVICE_IMPL_NULL_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNABLE_SEND_RESULT_TO_CALLBACK;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_OTHER_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_SUCCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.net.Uri;
import android.os.LimitExceededException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.pas.UpdateSignalsApiCalledStats;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.concurrent.ExecutorService;

@MockStatic(PeriodicEncodingJobService.class)
@RequiresSdkLevelAtLeastT(reason = "Protected App Signals is enabled for T+")
@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
public final class ProtectedSignalsServiceImplTest extends AdServicesExtendedMockitoTestCase {

    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
    private static final int UID = 42;
    private static final AdTechIdentifier ADTECH = AdTechIdentifier.fromString("example.com");
    private static final Uri URI = Uri.parse("https://example.com");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final String EXCEPTION_MESSAGE = "message";

    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    @Mock private UpdateSignalsOrchestrator mUpdateSignalsOrchestratorMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private CallingAppUidSupplier mCallingAppUidSupplierMock;
    @Mock private ProtectedSignalsServiceFilter mProtectedSignalsServiceFilterMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private UpdateSignalsCallback mUpdateSignalsCallbackMock;
    @Mock private UpdateSignalsProcessReportedLoggerImpl mUpdateSignalsProcessReportedLoggerMock;

    @Captor ArgumentCaptor<FledgeErrorResponse> mErrorCaptor;
    @Captor ArgumentCaptor<UpdateSignalsApiCalledStats> mStatsCaptor;

    private ProtectedSignalsServiceImpl mProtectedSignalsService;
    private DevContext mDevContext;
    private UpdateSignalsInput mInput;
    private Flags mFakeFlags;
    private ResultSyncCallback<ApiCallStats> logApiCallStatsCallback;

    @Before
    public void setup() {
        mFakeFlags = new ProtectedSignalsServiceImplTestFlags();
        mocker.mockGetDebugFlags(mMockDebugFlags);
        logApiCallStatsCallback = mocker.mockLogApiCallStats(mAdServicesLoggerMock);

        mProtectedSignalsService =
                new ProtectedSignalsServiceImpl(
                        mContext,
                        mUpdateSignalsOrchestratorMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        mCallingAppUidSupplierMock,
                        mProtectedSignalsServiceFilterMock,
                        mEnrollmentDaoMock,
                        mUpdateSignalsProcessReportedLoggerMock);

        mDevContext = DevContext.builder(PACKAGE).setDeviceDevOptionsEnabled(false).build();
        mInput = new UpdateSignalsInput.Builder(URI, PACKAGE).build();

        // Set up the mocks for a success flow -- individual tests that want a failure can overwrite
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenReturn(UID);
        when(mDevContextFilterMock.createDevContext()).thenReturn(mDevContext);
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenReturn(ADTECH);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);
        SettableFuture<Object> emptyReturn = SettableFuture.create();
        emptyReturn.set(new Object());
        when(mUpdateSignalsOrchestratorMock.orchestrateUpdate(
                        eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext), any(), any()))
                .thenReturn(FluentFuture.from(emptyReturn));
        when(mEnrollmentDaoMock.getEnrollmentDataForPASByAdTechIdentifier(eq(ADTECH)))
                .thenReturn(new EnrollmentData.Builder().setEnrollmentId("123").build());
        doNothing()
                .when(
                        () ->
                                PeriodicEncodingJobService.scheduleIfNeeded(
                                        any(), any(), anyBoolean()));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testUpdateSignalsSuccess() throws Exception {
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        eq(mContext),
                        eq(PACKAGE),
                        eq(API_NAME),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS));
        verify(mCallingAppUidSupplierMock).getCallingAppUid();
        verify(mDevContextFilterMock).createDevContext();
        verify(mProtectedSignalsServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext));
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE));
        verify(mUpdateSignalsOrchestratorMock)
                .orchestrateUpdate(eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext), any(), any());
        verify(mUpdateSignalsCallbackMock).onSuccess();
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_SUCCESS, PACKAGE);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_SUCCESS, mStatsCaptor.getValue().getJsonProcessingStatus());
        // Shouldn't be logged if status is success
        assertEquals("", mStatsCaptor.getValue().getAdTechId());
        assertEquals(0, mStatsCaptor.getValue().getPackageUid());
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testUpdateSignals_passesInCorrectStats() throws Exception {
        when(mUpdateSignalsOrchestratorMock.orchestrateUpdate(
                        eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext), any(), any()))
                .thenAnswer(
                        invocation -> {
                            UpdateSignalsApiCalledStats.Builder passedBuilder =
                                    invocation.getArgument(4);
                            passedBuilder.setJsonSize(9876);
                            SettableFuture<Object> returnedFuture = SettableFuture.create();
                            returnedFuture.set(new Object());
                            return FluentFuture.from(returnedFuture);
                        });

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        ArgumentCaptor<UpdateSignalsApiCalledStats.Builder> orchestratorStatsCaptor =
                ArgumentCaptor.forClass(UpdateSignalsApiCalledStats.Builder.class);
        verify(mUpdateSignalsOrchestratorMock)
                .orchestrateUpdate(
                        eq(URI),
                        eq(ADTECH),
                        eq(PACKAGE),
                        eq(mDevContext),
                        orchestratorStatsCaptor.capture(),
                        /* updateSignalsProcessReportedLogger= */ any());
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertWithMessage("Logged JSON processing status")
                .that(mStatsCaptor.getValue().getJsonProcessingStatus())
                .isEqualTo(JSON_PROCESSING_STATUS_SUCCESS);
        assertWithMessage("Logged JSON size")
                .that(mStatsCaptor.getValue().getJsonSize())
                .isEqualTo(orchestratorStatsCaptor.getValue().build().getJsonSize());
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testUpdateSignalsSuccessWithUXNotificationNotEnforced() throws Exception {
        mocker.mockGetConsentNotificationDebugMode(true);
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenReturn(ADTECH);

        ProtectedSignalsServiceImpl protectedSignalsService =
                new ProtectedSignalsServiceImpl(
                        mContext,
                        mUpdateSignalsOrchestratorMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        mCallingAppUidSupplierMock,
                        mProtectedSignalsServiceFilterMock,
                        mEnrollmentDaoMock,
                        mUpdateSignalsProcessReportedLoggerMock);

        protectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        eq(mContext),
                        eq(PACKAGE),
                        eq(API_NAME),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS));
        verify(mCallingAppUidSupplierMock).getCallingAppUid();
        verify(mDevContextFilterMock).createDevContext();
        verify(mProtectedSignalsServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext));
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE));
        verify(mUpdateSignalsOrchestratorMock)
                .orchestrateUpdate(eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext), any(), any());
        verify(mUpdateSignalsCallbackMock).onSuccess();
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_SUCCESS, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_SUCCESS, mStatsCaptor.getValue().getJsonProcessingStatus());
        // Shouldn't be logged if status is success
        assertEquals("", mStatsCaptor.getValue().getAdTechId());
        assertEquals(0, mStatsCaptor.getValue().getPackageUid());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_SERVICE_IMPL_NULL_ARGUMENT)
    public void testUpdateSignalsNullInput() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.updateSignals(null, mUpdateSignalsCallbackMock));
        verifyUpdateSignalsApiUsageLog(
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, /* packageName */ "");
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_SERVICE_IMPL_NULL_ARGUMENT)
    public void testUpdateSignalsLogsPackageUidAndAdtech() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.updateSignals(null, mUpdateSignalsCallbackMock));
        verifyUpdateSignalsApiUsageLog(
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, /* packageName */ "");
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_SERVICE_IMPL_NULL_ARGUMENT)
    public void testUpdateSignalsNullCallback() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.updateSignals(mInput, null));
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_GET_CALLING_UID_ILLEGAL_STATE)
    public void testUpdateSignalsExceptionGettingUid() throws Exception {
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenThrow(new IllegalStateException());
        assertThrows(
                IllegalStateException.class,
                () -> mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock));
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INTERNAL_ERROR, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED)
    public void testUpdateSignalsFilterException() throws Exception {
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenThrow(new LimitExceededException(EXCEPTION_MESSAGE));
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mUpdateSignalsCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_OTHER_ERROR,
                mStatsCaptor.getValue().getJsonProcessingStatus());
        verifyNoMoreInteractions(mAdServicesLoggerMock);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED)
    public void testUpdateSignals_filterExceptionFromConsent_notifiesSuccess() throws Exception {
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenThrow(new ConsentManager.RevokedConsentException());

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mUpdateSignalsCallbackMock).onSuccess();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED)
    public void testUpdateSignals_filterExceptionFromConsent_doesNotLogApiCalledLog()
            throws Exception {
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenThrow(new ConsentManager.RevokedConsentException());

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_OTHER_ERROR,
                mStatsCaptor.getValue().getJsonProcessingStatus());
        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED)
    public void testUpdateSignals_filterExceptionFromConsent_doesNotScheduleJob() throws Exception {
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenThrow(new ConsentManager.RevokedConsentException());

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)), never());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_INVALID_ARGUMENT)
    public void testUpdateSignalsIllegalArgumentException() throws Exception {
        IllegalArgumentException exception = new IllegalArgumentException(EXCEPTION_MESSAGE);
        SettableFuture<Object> future = SettableFuture.create();
        future.setException(exception);
        when(mUpdateSignalsOrchestratorMock.orchestrateUpdate(
                        eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext), any(), any()))
                .thenReturn(FluentFuture.from(future));
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mUpdateSignalsCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_OTHER_ERROR,
                mStatsCaptor.getValue().getJsonProcessingStatus());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_REVOKED_FOR_APP_AFTER_SETTING_FLEDGE_USE)
    public void testUpdateSignalsNoConsentIfCallerNotHaveApiConsent() throws Exception {
        // Does NOT have FLEDGE consent
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(true);
        // Seen the PAS notification
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_OTHER_ERROR,
                mStatsCaptor.getValue().getJsonProcessingStatus());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_NOT_GIVEN)
    public void testUpdateSignalsNoConsentIfUserNotSeenPASNotification() throws Exception {
        // Has FLEDGE consent
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        // Not seen the PAS notification
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(false);

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_OTHER_ERROR,
                mStatsCaptor.getValue().getJsonProcessingStatus());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_NOT_GIVEN)
    public void testUpdateSignalsNoConsent() throws Exception {
        // Revokes PA consent
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(true);
        // Revokes PAS consent
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(false);

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
        verify(mUpdateSignalsCallbackMock).onSuccess();
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
        verify(mAdServicesLoggerMock).logUpdateSignalsApiCalledStats(mStatsCaptor.capture());
        assertEquals(
                JSON_PROCESSING_STATUS_OTHER_ERROR,
                mStatsCaptor.getValue().getJsonProcessingStatus());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNABLE_SEND_RESULT_TO_CALLBACK)
    public void testUpdateSignalsCallbackException() throws Exception {
        doThrow(new RuntimeException()).when(mUpdateSignalsCallbackMock).onSuccess();
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INTERNAL_ERROR, PACKAGE);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setAdservicesApiStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));
    }

    private void verifyUpdateSignalsApiUsageLog(int resultCode, String packageName)
            throws InterruptedException {
        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS);
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(packageName);
        assertThat(apiCallStats.getResultCode()).isEqualTo(resultCode);
        assertThat(apiCallStats.getLatencyMillisecond()).isAtLeast(0);
    }

    private static class ProtectedSignalsServiceImplTestFlags implements Flags {
        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return false;
        }

        @Override
        public boolean getEnforceForegroundStatusForSignals() {
            return true;
        }

        @Override
        public boolean getFledgeAppPackageNameLoggingEnabled() {
            return true;
        }

        @Override
        public boolean getPasExtendedMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getPasProductMetricsV1Enabled() {
            return true;
        }
    }
}
