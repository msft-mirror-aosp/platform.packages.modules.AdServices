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

package com.android.adservices.service.devapi;

import static com.android.adservices.devapi.DevSessionFixture.IN_DEV;
import static com.android.adservices.devapi.DevSessionFixture.IN_PROD;
import static com.android.adservices.devapi.DevSessionFixture.TRANSITIONING_DEV_TO_PROD;
import static com.android.adservices.devapi.DevSessionFixture.TRANSITIONING_PROD_TO_DEV;
import static com.android.adservices.service.devapi.DevSessionControllerResult.FAILURE;
import static com.android.adservices.service.devapi.DevSessionControllerResult.NO_OP;
import static com.android.adservices.service.devapi.DevSessionControllerResult.SUCCESS;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.DatabaseClearer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DevSessionControllerImplTest extends AdServicesMockitoTestCase {

    private static final int TIMEOUT_SEC = 5;

    @Mock private DatabaseClearer mMockDatabaseClearer;
    @Mock private DevSessionDataStore mMockDevSessionDataStore;
    private DevSessionControllerImpl mDevSessionController;

    @Before
    public void setUp() {
        mDevSessionController =
                new DevSessionControllerImpl(
                        mMockDatabaseClearer,
                        mMockDevSessionDataStore,
                        AdServicesExecutors.getLightWeightExecutor());

        doReturn(immediateVoidFuture())
                .when(mMockDatabaseClearer)
                .deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true);
        doReturn(immediateVoidFuture()).when(mMockDatabaseClearer).deleteMeasurementData();
    }

    @Test
    public void startDevSession_withDevSessionDisabled_returnsSuccess() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV))
                .thenReturn(immediateFuture(IN_DEV));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(SUCCESS);
    }

    @Test
    public void startDevSession_withFailingProtectedAudienceAndAppSignalsDataClear_returnsFailure()
            throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV))
                .thenReturn(immediateFuture(IN_DEV));
        when(mMockDatabaseClearer.deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true))
                .thenThrow(new RuntimeException());

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(FAILURE);
    }

    @Test
    public void startDevSession_withFailingMeasurementDataClear_returnsFailure() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV))
                .thenReturn(immediateFuture(IN_DEV));
        when(mMockDatabaseClearer.deleteMeasurementData()).thenThrow(new RuntimeException());

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(FAILURE);
    }

    @Test
    public void startDevSession_withDevSessionTransitioningToProd_returnsSuccess()
            throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(TRANSITIONING_DEV_TO_PROD));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_DEV_TO_PROD))
                .thenReturn(immediateFuture(IN_PROD));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(SUCCESS);
    }

    @Test
    public void startDevSession_withDevSessionTransitioningToDev_returnsSuccess() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV))
                .thenReturn(immediateFuture(IN_DEV));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(SUCCESS);
    }

    @Test
    public void startDevSession_withDevSessionActive_returnsNoOp() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_DEV));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(NO_OP);
        verifyZeroInteractions(mMockDatabaseClearer);
    }

    @Test
    public void startDevSession_withDevSessionActiveButExpired_returnsNoOp() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_DEV));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(NO_OP);
        verifyZeroInteractions(mMockDatabaseClearer);
    }

    @Test
    public void endDevSession_withDevSessionActive_returnsSuccess() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_DEV));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_DEV_TO_PROD))
                .thenReturn(immediateFuture(IN_PROD));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.endDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(SUCCESS);
    }

    @Test
    public void endDevSession_withDevSessionTransitioningToProd_returnsSuccess() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(TRANSITIONING_DEV_TO_PROD));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_DEV_TO_PROD))
                .thenReturn(immediateFuture(IN_PROD));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.endDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(SUCCESS);
    }

    @Test
    public void endDevSession_withDevSessionTransitioningToDev_returnsSuccess() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_DEV_TO_PROD))
                .thenReturn(immediateFuture(IN_DEV));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.endDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(SUCCESS);
    }

    @Test
    public void endDevSession_withDevSessionDisabled_returnsNoOp() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.endDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(NO_OP);
        verifyZeroInteractions(mMockDatabaseClearer);
    }

    @Test
    public void startDevSession_withClearsAllDatabases_returnsSuccess() throws Exception {
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV))
                .thenReturn(immediateFuture(IN_DEV));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(SUCCESS);
    }

    @Test
    public void startDevSession_failingToDeleteProtectedAudienceAndAppSignalsData_returnsFailure()
            throws Exception {
        when(mMockDatabaseClearer.deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true))
                .thenReturn(immediateFailedFuture(new Exception("Database clear failed")));
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));
        when(mMockDevSessionDataStore.set(any(DevSession.class)))
                .thenReturn(immediateFuture(TRANSITIONING_PROD_TO_DEV));

        Future<DevSessionControllerResult> resultFuture = mDevSessionController.startDevSession();

        expect.withMessage("DevSession future").that(wait(resultFuture)).isEqualTo(FAILURE);
    }

    private static <T> T wait(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }
}
