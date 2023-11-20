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

import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_ADULT_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_U18_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

public class AdServicesExtDataStorageServiceManagerTest {
    private static final long TIMEOUT = 5000L;
    private static final int DEFAULT_NO_DATA_VALUE = -1;
    private static final int MANUAL_INTERACTION_UNKNOWN_VALUE = 0;
    private static final AdServicesExtDataParams TEST_PARAMS =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(1)
                    .setMsmtConsent(0)
                    .setIsU18Account(1)
                    .setIsAdultAccount(0)
                    .setManualInteractionWithConsentStatus(-1)
                    .setMsmtRollbackApexVersion(-1)
                    .build();
    private static final int[] TEST_FIELD_LIST = {
        FIELD_IS_MEASUREMENT_CONSENTED, FIELD_IS_NOTIFICATION_DISPLAYED
    };

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

    @Mock private AdServicesExtDataStorageServiceWorker mMockWorker;
    @Captor private ArgumentCaptor<AdServicesExtDataParams> mParamsCaptor;
    @Captor private ArgumentCaptor<int[]> mFieldsCaptor;

    private MockitoSession mMockitoSession;
    private AdServicesExtDataStorageServiceManager mManager;

    @Before
    public void setup() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(AdServicesExtDataStorageServiceWorker.class)
                        .initMocks(this)
                        .startMocking();

        ExtendedMockito.doReturn(mMockWorker)
                .when(() -> AdServicesExtDataStorageServiceWorker.getInstance(mContext));
        mManager = AdServicesExtDataStorageServiceManager.getInstance(mContext);
    }

    @After
    public void teardown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testGetAdServicesExtData_onResultSet_returnsParams() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        Assert.assertEquals(0, result.getIsMeasurementConsented());
        Assert.assertEquals(-1, result.getMeasurementRollbackApexVersion());
        Assert.assertEquals(1, result.getIsU18Account());
        Assert.assertEquals(0, result.getIsAdultAccount());
        Assert.assertEquals(-1, result.getManualInteractionWithConsentStatus());
        Assert.assertEquals(1, result.getIsNotificationDisplayed());
    }

    @Test
    public void testGetAdServicesExtData_onErrorSet_returnsDefaultParams() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(0))
                                    .onError(new RuntimeException("Testing exception thrown"));
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(Mockito.any(AdServicesOutcomeReceiver.class));

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsMeasurementConsented());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getMeasurementRollbackApexVersion());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsNotificationDisplayed());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsU18Account());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsAdultAccount());
        Assert.assertEquals(
                MANUAL_INTERACTION_UNKNOWN_VALUE, result.getManualInteractionWithConsentStatus());
    }

    @Test
    public void testGetAdServicesExtData_timedOut_returnsDefaultParams() {
        doAnswer(
                        (invocation) -> {
                            Thread.sleep(TIMEOUT);
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(Mockito.any(AdServicesOutcomeReceiver.class));

        AdServicesExtDataParams result = mManager.getAdServicesExtData();
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsMeasurementConsented());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getMeasurementRollbackApexVersion());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsNotificationDisplayed());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsU18Account());
        Assert.assertEquals(DEFAULT_NO_DATA_VALUE, result.getIsAdultAccount());
        Assert.assertEquals(
                MANUAL_INTERACTION_UNKNOWN_VALUE, result.getManualInteractionWithConsentStatus());
    }

    @Test
    public void testSetAdServicesExtData_emptyFieldList_returnEarly() {
        Assert.assertTrue(mManager.setAdServicesExtData(TEST_PARAMS, new int[] {}));
        Mockito.verifyZeroInteractions(mMockWorker);
    }

    @Test
    public void testSetAdServicesExtData_onResultSet_returnsTrue() {
        mockWorkerSetAdExtDataCall();

        Assert.assertTrue(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST));

        Mockito.verify(mMockWorker, times(1))
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(int[].class),
                        Mockito.any(AdServicesOutcomeReceiver.class));
    }

    @Test
    public void testSetAdServicesExtData_onErrorSet_returnsFalse() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(2))
                                    .onError(new RuntimeException("Testing exception thrown"));
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(int[].class),
                        Mockito.any(AdServicesOutcomeReceiver.class));

        Assert.assertFalse(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST));

        Mockito.verify(mMockWorker, times(1))
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(int[].class),
                        Mockito.any(AdServicesOutcomeReceiver.class));
    }

    @Test
    public void testSetAdServicesExtData_timedOut_returnsFalse() {
        doAnswer(
                        (invocation) -> {
                            Thread.sleep(TIMEOUT);
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(int[].class),
                        Mockito.any(AdServicesOutcomeReceiver.class));

        Assert.assertFalse(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST));

        Mockito.verify(mMockWorker, times(1))
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(int[].class),
                        Mockito.any(AdServicesOutcomeReceiver.class));
    }

    @Test
    public void testUpdateRequestToStr_emptyFields() {
        Assert.assertEquals("{}", mManager.updateRequestToStr(TEST_PARAMS, new int[] {}));
    }

    @Test
    public void testUpdateRequestToStr_nonEmptyFields() {
        Assert.assertEquals(
                "{MsmtConsent: 0,NotificationDisplayed: 1,}",
                mManager.updateRequestToStr(TEST_PARAMS, TEST_FIELD_LIST));
    }

    @Test
    public void testSetMsmtConsent_withFalse() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setMsmtConsent(false));
        Assert.assertEquals(0, mParamsCaptor.getValue().getIsMeasurementConsented());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_MEASUREMENT_CONSENTED, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testSetMsmtConsent_withTrue() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setMsmtConsent(true));
        Assert.assertEquals(1, mParamsCaptor.getValue().getIsMeasurementConsented());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_MEASUREMENT_CONSENTED, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testGetMsmtConsent_withZeroRetrieved_returnsFalse() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        Assert.assertFalse(mManager.getMsmtConsent());
    }

    @Test
    public void testGetMsmtConsent_withOneRetrieved_returnsTrue() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setMsmtConsent(1).build();
        mockWorkerGetAdExtDataCall(params);
        Assert.assertTrue(mManager.getMsmtConsent());
    }

    @Test
    public void testGetManualInteractionWithConsentStatus() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        Assert.assertEquals(-1, mManager.getManualInteractionWithConsentStatus());
    }

    @Test
    public void testSetManualInteractionWithConsentStatus() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setManualInteractionWithConsentStatus(-1));
        Assert.assertEquals(-1, mParamsCaptor.getValue().getManualInteractionWithConsentStatus());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(
                FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testSetNotifDisplayed_withFalse() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setNotifDisplayed(false));
        Assert.assertEquals(0, mParamsCaptor.getValue().getIsNotificationDisplayed());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_NOTIFICATION_DISPLAYED, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testSetNotifDisplayed_withTrue() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setNotifDisplayed(true));
        Assert.assertEquals(1, mParamsCaptor.getValue().getIsNotificationDisplayed());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_NOTIFICATION_DISPLAYED, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testGetNotifDisplayed_withOneRetrieved_returnsTrue() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        Assert.assertTrue(mManager.getNotifDisplayed());
    }

    @Test
    public void testGetNotifDisplayed_withZeroRetrieved_returnsFalse() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setNotificationDisplayed(0).build();
        mockWorkerGetAdExtDataCall(params);
        Assert.assertFalse(mManager.getNotifDisplayed());
    }

    @Test
    public void testSetIsAdultAccount_withFalse() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setIsAdultAccount(false));
        Assert.assertEquals(0, mParamsCaptor.getValue().getIsAdultAccount());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_ADULT_ACCOUNT, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testSetIsAdultAccount_withTrue() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setIsAdultAccount(true));
        Assert.assertEquals(1, mParamsCaptor.getValue().getIsAdultAccount());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_ADULT_ACCOUNT, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testGetIsAdultAccount_withZeroRetrieved_returnsFalse() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        Assert.assertFalse(mManager.getIsAdultAccount());
    }

    @Test
    public void testGetIsAdultAccount_withOneRetrieved_returnsTrue() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setIsAdultAccount(1).build();
        mockWorkerGetAdExtDataCall(params);
        Assert.assertTrue(mManager.getIsAdultAccount());
    }

    @Test
    public void testSetIsU18Account_withFalse() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setIsU18Account(false));
        Assert.assertEquals(0, mParamsCaptor.getValue().getIsU18Account());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_U18_ACCOUNT, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testSetIsU18Account_withTrue() {
        mockWorkerSetAdExtDataCall();
        Assert.assertTrue(mManager.setIsU18Account(true));
        Assert.assertEquals(1, mParamsCaptor.getValue().getIsU18Account());
        Assert.assertEquals(1, mFieldsCaptor.getValue().length);
        Assert.assertEquals(FIELD_IS_U18_ACCOUNT, mFieldsCaptor.getValue()[0]);
    }

    @Test
    public void testGetIsU18Account_withOneRetrieved_returnsTrue() {
        mockWorkerGetAdExtDataCall(TEST_PARAMS);
        Assert.assertTrue(mManager.getIsU18Account());
    }

    @Test
    public void testGetIsU18Account_withZeroRetrieved_returnsFalse() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder().setIsU18Account(0).build();
        mockWorkerGetAdExtDataCall(params);
        Assert.assertFalse(mManager.getIsU18Account());
    }

    @Test
    public void testClearAllDataAsync() {
        mockWorkerSetAdExtDataCall();
        mManager.clearAllDataAsync();

        Assert.assertEquals(-1, mParamsCaptor.getValue().getIsNotificationDisplayed());
        Assert.assertEquals(-1, mParamsCaptor.getValue().getIsMeasurementConsented());
        Assert.assertEquals(-1, mParamsCaptor.getValue().getIsU18Account());
        Assert.assertEquals(-1, mParamsCaptor.getValue().getIsAdultAccount());
        Assert.assertEquals(0, mParamsCaptor.getValue().getManualInteractionWithConsentStatus());
        Assert.assertEquals(-1L, mParamsCaptor.getValue().getMeasurementRollbackApexVersion());

        int[] expectedFields =
                new int[] {
                    FIELD_IS_NOTIFICATION_DISPLAYED,
                    FIELD_IS_MEASUREMENT_CONSENTED,
                    FIELD_IS_U18_ACCOUNT,
                    FIELD_IS_ADULT_ACCOUNT,
                    FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS,
                    FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION
                };
        Assert.assertEquals(expectedFields.length, mFieldsCaptor.getValue().length);
        Assert.assertArrayEquals(expectedFields, mFieldsCaptor.getValue());
    }

    private void mockWorkerGetAdExtDataCall(AdServicesExtDataParams params) {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(0))
                                    .onResult(params);
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(Mockito.any(AdServicesOutcomeReceiver.class));
    }

    private void mockWorkerSetAdExtDataCall() {
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(2))
                                    .onResult(invocation.getArgument(0));
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(
                        mParamsCaptor.capture(),
                        mFieldsCaptor.capture(),
                        Mockito.any(AdServicesOutcomeReceiver.class));
    }
}
