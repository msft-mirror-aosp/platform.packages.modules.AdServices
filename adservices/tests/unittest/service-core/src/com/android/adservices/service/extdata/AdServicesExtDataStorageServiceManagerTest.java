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

package com.android.adservices.service.extdata;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_NO_MANUAL_INTERACTIONS_RECORDED;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager.UNKNOWN_PACKAGE_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__ADEXT_DATA_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_ADEXT_DATA_SERVICE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PUT_ADEXT_DATA_SERVICE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ADEXT_DATA_SERVICE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@SpyStatic(AdServicesExtDataStorageServiceWorker.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(FlagsFactory.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ADEXT_DATA_SERVICE)
public final class AdServicesExtDataStorageServiceManagerTest
        extends AdServicesExtendedMockitoTestCase {

    private static final long SLEEP_MS = 2_000L;
    private static final int INVOCATION_TIMEOUT = 500;
    private static final long NO_APEX_VALUE = -1L;
    private static final AdServicesExtDataParams TEST_PARAMS =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_TRUE)
                    .setMsmtConsent(BOOLEAN_FALSE)
                    .setIsU18Account(BOOLEAN_TRUE)
                    .setIsAdultAccount(BOOLEAN_FALSE)
                    .setManualInteractionWithConsentStatus(STATE_NO_MANUAL_INTERACTIONS_RECORDED)
                    .setMsmtRollbackApexVersion(NO_APEX_VALUE)
                    .build();
    private static final int[] TEST_FIELD_LIST = {
        FIELD_IS_MEASUREMENT_CONSENTED, FIELD_IS_NOTIFICATION_DISPLAYED
    };

    private final AdServicesLogger mAdServicesLogger = spy(AdServicesLoggerImpl.getInstance());

    @Mock private AdServicesExtDataStorageServiceWorker mMockWorker;
    @Captor private ArgumentCaptor<AdServicesExtDataParams> mParamsCaptor;
    @Captor private ArgumentCaptor<int[]> mFieldsCaptor;

    private AdServicesExtDataStorageServiceManager mManager;

    @Before
    public void setup() {
        // mock the device config read for checking debug proxy
        doReturn(false).when(mMockFlags).getEnableAdExtServiceDebugProxy();

        // Mock timeouts
        doReturn(INVOCATION_TIMEOUT).when(mMockFlags).getAdExtReadTimeoutMs();
        doReturn(INVOCATION_TIMEOUT).when(mMockFlags).getAdExtWriteTimeoutMs();
        mocker.mockGetFlags(mMockFlags);

        doReturn(mMockWorker).when(AdServicesExtDataStorageServiceWorker::getInstance);
        doReturn(mAdServicesLogger).when(AdServicesLoggerImpl::getInstance);
        mManager = AdServicesExtDataStorageServiceManager.getInstance();

        mocker.mockAllCobaltLoggingFlags(false);
    }

    @Test
    public void testGetAdServicesExtData_onResultSet_returnsParams() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        expect.that(result.getIsMeasurementConsented()).isEqualTo(BOOLEAN_FALSE);
        expect.that(result.getMeasurementRollbackApexVersion()).isEqualTo(NO_APEX_VALUE);
        expect.that(result.getIsU18Account()).isEqualTo(BOOLEAN_TRUE);
        expect.that(result.getIsAdultAccount()).isEqualTo(BOOLEAN_FALSE);
        expect.that(result.getManualInteractionWithConsentStatus())
                .isEqualTo(STATE_NO_MANUAL_INTERACTIONS_RECORDED);
        expect.that(result.getIsNotificationDisplayed()).isEqualTo(BOOLEAN_TRUE);

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA, STATUS_SUCCESS);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_ADEXT_DATA_SERVICE_ERROR)
    public void testGetAdServicesExtData_onErrorSet_returnsDefaultParams() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(0))
                                    .onError(new RuntimeException("Testing exception thrown"));
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(any());

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        expect.that(result.getIsMeasurementConsented()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getMeasurementRollbackApexVersion()).isEqualTo(NO_APEX_VALUE);
        expect.that(result.getIsU18Account()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getIsAdultAccount()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getManualInteractionWithConsentStatus()).isEqualTo(STATE_UNKNOWN);
        expect.that(result.getIsNotificationDisplayed()).isEqualTo(BOOLEAN_UNKNOWN);

        verifyLogging(
                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testGetAdServicesExtData_timedOut_returnsDefaultParams() {
        doAnswer(
                        (invocation) -> {
                            Thread.sleep(SLEEP_MS);
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(any());

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        expect.that(result.getIsMeasurementConsented()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getMeasurementRollbackApexVersion()).isEqualTo(NO_APEX_VALUE);
        expect.that(result.getIsU18Account()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getIsAdultAccount()).isEqualTo(BOOLEAN_UNKNOWN);
        expect.that(result.getManualInteractionWithConsentStatus()).isEqualTo(STATE_UNKNOWN);
        expect.that(result.getIsNotificationDisplayed()).isEqualTo(BOOLEAN_UNKNOWN);

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA, STATUS_TIMEOUT);
    }

    @Test
    public void testSetAdServicesExtData_emptyFieldList_returnEarly() {
        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, new int[] {})).isTrue();
        verifyZeroInteractions(mMockWorker);
    }

    @Test
    public void testSetAdServicesExtData_onResultSet_returnsTrue() {
        mockWorkerSetAdExtDataCall();

        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST)).isTrue();

        verify(mMockWorker).setAdServicesExtData(any(), any(), any());

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA, STATUS_SUCCESS);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PUT_ADEXT_DATA_SERVICE_ERROR)
    public void testSetAdServicesExtData_onErrorSet_returnsFalse() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(2))
                                    .onError(new RuntimeException("Testing exception thrown"));
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(any(), any(), any());

        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST)).isFalse();

        verify(mMockWorker).setAdServicesExtData(any(), any(), any());

        verifyLogging(
                AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testSetAdServicesExtData_timedOut_returnsFalse() {
        doAnswer(
                        (invocation) -> {
                            Thread.sleep(SLEEP_MS);
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(any(), any(), any());

        expect.that(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST)).isFalse();

        verify(mMockWorker).setAdServicesExtData(any(), any(), any());

        verifyLogging(AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA, STATUS_TIMEOUT);
    }

    @Test
    public void testUpdateRequestToStr_emptyFields() {
        expect.that(mManager.updateRequestToString(TEST_PARAMS, new int[] {})).isEqualTo("{}");
    }

    @Test
    public void testUpdateRequestToStr_nonEmptyFields() {
        expect.that(mManager.updateRequestToString(TEST_PARAMS, TEST_FIELD_LIST))
                .isEqualTo("{MsmtConsent: 0,NotificationDisplayed: 1,}");
    }

    private void verifyLogging(int apiName, int expectedResultCode) {
        ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);

        verify(mAdServicesLogger).logApiCallStats(argument.capture());

        ApiCallStats stats = argument.getValue();
        expect.that(stats.getCode()).isEqualTo(AD_SERVICES_API_CALLED);
        expect.that(stats.getApiClass())
                .isEqualTo(AD_SERVICES_API_CALLED__API_CLASS__ADEXT_DATA_SERVICE);
        expect.that(stats.getApiName()).isEqualTo(apiName);
        expect.that(stats.getResultCode()).isEqualTo(expectedResultCode);
        expect.that(stats.getAppPackageName()).isEqualTo(mContext.getPackageName());
        expect.that(stats.getSdkPackageName()).isEqualTo(UNKNOWN_PACKAGE_NAME);
    }

    private void mockWorkerGetAdExtDataCall(AdServicesExtDataParams params) {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(0))
                                    .onResult(params);
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(any());
    }

    private void mockWorkerSetAdExtDataCall() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(2))
                                    .onResult(invocation.getArgument(0));
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(mParamsCaptor.capture(), mFieldsCaptor.capture(), any());
    }
}
