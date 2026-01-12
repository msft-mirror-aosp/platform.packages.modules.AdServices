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


import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_EXTENDED_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_PRODUCT_METRICS_V1_ENABLED;
import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.signals.SignalsFixture.UpdateSignalsSyncCallback;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerFactory;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.util.concurrent.ExecutorService;

@MockStatic(PeriodicEncodingJobService.class)
@RequiresSdkLevelAtLeastT(reason = "Protected App Signals is enabled for T+")
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@SetFlagFalse(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagTrue(KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS)
@SetFlagTrue(KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED)
@SetFlagTrue(KEY_PAS_EXTENDED_METRICS_ENABLED)
@SetFlagTrue(KEY_PAS_PRODUCT_METRICS_V1_ENABLED)
@SpyStatic(DebugFlags.class)
public final class ProtectedSignalsServiceImplTest extends AdServicesExtendedMockitoTestCase {

    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
    private static final int UID = 42;
    private static final AdTechIdentifier ADTECH = AdTechIdentifier.fromString("example.com");
    private static final Uri URI = Uri.parse("https://example.com");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();

    @Mock private UpdateSignalsOrchestrator mUpdateSignalsOrchestratorMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private CallingAppUidSupplier mCallingAppUidSupplierMock;
    @Mock private ProtectedSignalsServiceFilter mProtectedSignalsServiceFilterMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;

    @Mock
    private UpdateSignalsProcessReportedLoggerFactory
            mUpdateSignalsProcessReportedLoggerFactoryMock;

    @Mock private UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLoggerMock;
    @Mock private UpdateSignalsCallback mMockUpdateSignalsCallback;

    private ProtectedSignalsServiceImpl mProtectedSignalsService;
    private DevContext mDevContext;
    private UpdateSignalsInput mInput;
    private UpdateSignalsSyncCallback mUpdateSignalsCallback;
    private InOrder mInOrder;

    @Before
    public void setup() {
        mocker.mockGetDebugFlags(mFakeDebugFlags);
        when(mUpdateSignalsProcessReportedLoggerFactoryMock.getLoggerInstance())
                .thenReturn(mUpdateSignalsProcessReportedLoggerMock);
        mInOrder = inOrder(mUpdateSignalsProcessReportedLoggerMock);
        mUpdateSignalsCallback = new UpdateSignalsSyncCallback();

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
                        mFakeDebugFlags,
                        mCallingAppUidSupplierMock,
                        mProtectedSignalsServiceFilterMock,
                        mEnrollmentDaoMock,
                        mUpdateSignalsProcessReportedLoggerFactoryMock);

        mDevContext = DevContext.builder(PACKAGE).setDeviceDevOptionsEnabled(false).build();
        mInput = new UpdateSignalsInput.Builder(URI, PACKAGE).build();

        // Set up the mocks for a success flow -- individual tests that want a failure can overwrite
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenReturn(UID);
        when(mDevContextFilterMock.createDevContext()).thenReturn(mDevContext);
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        /* disableEnrollmentCheck= */ eq(false),
                        /* enforceForeground= */ eq(true),
                        /* enforceConsent= */ eq(false),
                        /* enforceNotificationShown= */ eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenReturn(ADTECH);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        when(mConsentManagerMock.isPasConsentGiven()).thenReturn(true);
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
    public void testUpdateSignals_succeeded_shouldReturnDisabledStatus() throws Exception {
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallback);

        FledgeErrorResponse error = mUpdateSignalsCallback.assertFailureReceived();
        assertWithMessage("Verifies deprecation status code.")
                .that(error.getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS,
                        PACKAGE,
                        AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED, /* latencyMs */
                        0);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testUpdateSignals_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockUpdateSignalsCallback).onFailure(any());

        mProtectedSignalsService.updateSignals(mInput, mMockUpdateSignalsCallback);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS,
                        PACKAGE,
                        AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED, /* latencyMs */
                        0);
    }
}
