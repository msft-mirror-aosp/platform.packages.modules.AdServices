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

package com.android.adservices.service.signals;

import android.os.Process;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.adselection.AdIdFetcher;

import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EgressConfigurationGeneratorTest extends AdServicesMockitoTestCase {

    private static final int CALLER_UID = Process.myUid();
    private static final long AD_ID_FETCHER_TIMEOUT = 20L;
    private static final long FUTURE_TIMEOUT = 1L;
    private static final TimeUnit FUTURE_TIMEOUT_UNIT = TimeUnit.SECONDS;
    private static final ExecutorService LIGHT_WEIGHT_EXECUTOR_SERVICE =
            AdServicesExecutors.getLightWeightExecutor();
    @Mock private AdIdFetcher mAdIdFetcher;
    private EgressConfigurationGenerator mEgressConfigurationGenerator;

    @Test
    public void testCreateInstance_nullAdIdFetcher() {
        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        EgressConfigurationGenerator.createInstance(
                                false, null, AD_ID_FETCHER_TIMEOUT, LIGHT_WEIGHT_EXECUTOR_SERVICE));
    }

    @Test
    public void testCreateInstance_nullExecutorService() {
        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        EgressConfigurationGenerator.createInstance(
                                false, mAdIdFetcher, AD_ID_FETCHER_TIMEOUT, null));
    }

    @Test
    public void test_isUnlimitedEgressEnabled_success()
            throws ExecutionException, InterruptedException, TimeoutException {
        boolean enablePasUnlimitedEgress = true;
        mEgressConfigurationGenerator =
                EgressConfigurationGenerator.createInstance(
                        enablePasUnlimitedEgress,
                        mAdIdFetcher,
                        AD_ID_FETCHER_TIMEOUT,
                        LIGHT_WEIGHT_EXECUTOR_SERVICE);
        Mockito.when(
                        mAdIdFetcher.isLimitedAdTrackingEnabled(
                                mPackageName, CALLER_UID, AD_ID_FETCHER_TIMEOUT))
                .thenReturn(Futures.immediateFuture(false));

        boolean expected =
                mEgressConfigurationGenerator
                        .isUnlimitedEgressEnabledForAuction(mPackageName, CALLER_UID)
                        .get(FUTURE_TIMEOUT, FUTURE_TIMEOUT_UNIT);

        Truth.assertThat(expected).isTrue();
        Mockito.verify(mAdIdFetcher)
                .isLimitedAdTrackingEnabled(mPackageName, CALLER_UID, AD_ID_FETCHER_TIMEOUT);
    }

    @Test
    public void test_isUnlimitedEgressEnabled_disabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        boolean enablePasUnlimitedEgress = false;
        mEgressConfigurationGenerator =
                EgressConfigurationGenerator.createInstance(
                        enablePasUnlimitedEgress,
                        mAdIdFetcher,
                        AD_ID_FETCHER_TIMEOUT,
                        LIGHT_WEIGHT_EXECUTOR_SERVICE);
        Mockito.when(
                        mAdIdFetcher.isLimitedAdTrackingEnabled(
                                mPackageName, CALLER_UID, AD_ID_FETCHER_TIMEOUT))
                .thenReturn(Futures.immediateFuture(true));

        boolean expected =
                mEgressConfigurationGenerator
                        .isUnlimitedEgressEnabledForAuction(mPackageName, CALLER_UID)
                        .get(FUTURE_TIMEOUT, FUTURE_TIMEOUT_UNIT);

        Truth.assertThat(expected).isFalse();
        Mockito.verify(mAdIdFetcher, Mockito.never())
                .isLimitedAdTrackingEnabled(mPackageName, CALLER_UID, AD_ID_FETCHER_TIMEOUT);
    }

    @Test
    public void test_isUnlimitedEgressEnabled_limitedAdTrackingEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        boolean enablePasUnlimitedEgress = true;
        mEgressConfigurationGenerator =
                EgressConfigurationGenerator.createInstance(
                        enablePasUnlimitedEgress,
                        mAdIdFetcher,
                        AD_ID_FETCHER_TIMEOUT,
                        LIGHT_WEIGHT_EXECUTOR_SERVICE);
        Mockito.when(
                        mAdIdFetcher.isLimitedAdTrackingEnabled(
                                mPackageName, CALLER_UID, AD_ID_FETCHER_TIMEOUT))
                .thenReturn(Futures.immediateFuture(true));

        boolean expected =
                mEgressConfigurationGenerator
                        .isUnlimitedEgressEnabledForAuction(mPackageName, CALLER_UID)
                        .get(FUTURE_TIMEOUT, FUTURE_TIMEOUT_UNIT);

        Truth.assertThat(expected).isFalse();
        Mockito.verify(mAdIdFetcher)
                .isLimitedAdTrackingEnabled(mPackageName, CALLER_UID, AD_ID_FETCHER_TIMEOUT);
    }
}
