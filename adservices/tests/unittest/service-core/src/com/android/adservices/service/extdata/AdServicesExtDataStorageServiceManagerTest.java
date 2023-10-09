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

import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;

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
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(0))
                                    .onResult(TEST_PARAMS);
                            return null;
                        })
                .when(mMockWorker)
                .getAdServicesExtData(Mockito.any(AdServicesOutcomeReceiver.class));

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
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(2))
                                    .onResult(invocation.getArgument(0));
                            return null;
                        })
                .when(mMockWorker)
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(),
                        Mockito.any(AdServicesOutcomeReceiver.class));

        Assert.assertTrue(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST));

        Mockito.verify(mMockWorker, times(1))
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(),
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
                        Mockito.any(),
                        Mockito.any(AdServicesOutcomeReceiver.class));

        Assert.assertFalse(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST));

        Mockito.verify(mMockWorker, times(1))
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(),
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
                        Mockito.any(),
                        Mockito.any(AdServicesOutcomeReceiver.class));

        Assert.assertFalse(mManager.setAdServicesExtData(TEST_PARAMS, TEST_FIELD_LIST));

        Mockito.verify(mMockWorker, times(1))
                .setAdServicesExtData(
                        Mockito.any(AdServicesExtDataParams.class),
                        Mockito.any(),
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
}
