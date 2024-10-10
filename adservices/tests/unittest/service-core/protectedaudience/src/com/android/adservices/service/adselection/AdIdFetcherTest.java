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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.AdIdFetcher.DEFAULT_IS_LAT_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adid.AdId;
import android.adservices.common.CommonFixture;
import android.os.Process;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.common.PermissionHelper;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@MockStatic(PermissionHelper.class)
public final class AdIdFetcherTest extends AdServicesExtendedMockitoTestCase {
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final int CALLER_UID = Process.myUid();
    private static final long AD_ID_FETCHER_TIMEOUT_MS = 50;
    private MockAdIdWorker mMockAdIdWorker;
    private ExecutorService mLightweightExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private AdIdFetcher mAdIdFetcher;

    @Before
    public void setup() {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mMockAdIdWorker = new MockAdIdWorker(new AdIdCacheManager(mContext));
    }

    @Test
    public void testConstructor_nullContext() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdIdFetcher(
                                null,
                                mMockAdIdWorker,
                                mLightweightExecutorService,
                                mScheduledExecutor));
    }

    @Test
    public void testConstructor_nullAdIdWorker() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdIdFetcher(
                                mContext, null, mLightweightExecutorService, mScheduledExecutor));
    }

    @Test
    public void testConstructor_nullExecutorService() {
        assertThrows(
                NullPointerException.class,
                () -> new AdIdFetcher(mContext, mMockAdIdWorker, null, mScheduledExecutor));
    }

    @Test
    public void testConstructor_nullScheduledThreadPoolExecutor() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdIdFetcher(
                                mContext, mMockAdIdWorker, mLightweightExecutorService, null));
    }

    @Test
    public void testIsLimitedTrackingEnabled_PermissionsNotDeclared_ReturnsDefault()
            throws ExecutionException, InterruptedException {
        Mockito.when(PermissionHelper.hasAdIdPermission(mContext, CALLER_PACKAGE_NAME, CALLER_UID))
                .thenReturn(false);
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);
        mAdIdFetcher =
                new AdIdFetcher(
                        mContext, mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);

        Boolean isLatEnabled =
                mAdIdFetcher
                        .isLimitedAdTrackingEnabled(
                                CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS)
                        .get();

        assertThat(isLatEnabled).isEqualTo(DEFAULT_IS_LAT_ENABLED);
    }

    @Test
    public void testIsLimitedTrackingEnabled_Disabled_ReturnsFalse()
            throws ExecutionException, InterruptedException {
        Mockito.when(PermissionHelper.hasAdIdPermission(mContext, CALLER_PACKAGE_NAME, CALLER_UID))
                .thenReturn(true);
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);
        mAdIdFetcher =
                new AdIdFetcher(
                        mContext, mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);

        Boolean isLatEnabled =
                mAdIdFetcher
                        .isLimitedAdTrackingEnabled(
                                CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS)
                        .get();

        assertThat(isLatEnabled).isFalse();
    }

    @Test
    public void testIsLimitedTrackingEnabled_LatEnabled_ReturnsTrue()
            throws ExecutionException, InterruptedException {
        Mockito.when(PermissionHelper.hasAdIdPermission(mContext, CALLER_PACKAGE_NAME, CALLER_UID))
                .thenReturn(true);
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, true);
        mAdIdFetcher =
                new AdIdFetcher(
                        mContext, mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);

        Boolean isLatEnabled =
                mAdIdFetcher
                        .isLimitedAdTrackingEnabled(
                                CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS)
                        .get();

        assertThat(isLatEnabled).isTrue();
    }

    @Test
    public void testIsLimitedTrackingEnabled_AdIdZeroedOut_ReturnsDefault()
            throws ExecutionException, InterruptedException {
        Mockito.when(PermissionHelper.hasAdIdPermission(mContext, CALLER_PACKAGE_NAME, CALLER_UID))
                .thenReturn(true);
        mMockAdIdWorker.setResult(AdId.ZERO_OUT, false);
        mAdIdFetcher =
                new AdIdFetcher(
                        mContext, mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);

        Boolean isLatEnabled =
                mAdIdFetcher
                        .isLimitedAdTrackingEnabled(
                                CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS)
                        .get();

        assertThat(isLatEnabled).isEqualTo(DEFAULT_IS_LAT_ENABLED);
    }

    @Test
    public void testIsLimitedTrackingEnabled_TimeoutException_ReturnsDefault()
            throws ExecutionException, InterruptedException {
        Mockito.when(PermissionHelper.hasAdIdPermission(mContext, CALLER_PACKAGE_NAME, CALLER_UID))
                .thenReturn(true);
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);
        mMockAdIdWorker.setDelay(AD_ID_FETCHER_TIMEOUT_MS * 2);
        mAdIdFetcher =
                new AdIdFetcher(
                        mContext, mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);

        Boolean isLatEnabled =
                mAdIdFetcher
                        .isLimitedAdTrackingEnabled(
                                CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS)
                        .get();

        assertThat(isLatEnabled).isEqualTo(DEFAULT_IS_LAT_ENABLED);
    }

    @Test
    public void testIsLimitedTrackingEnabled_OnError_ReturnsDefault()
            throws ExecutionException, InterruptedException {
        Mockito.when(PermissionHelper.hasAdIdPermission(mContext, CALLER_PACKAGE_NAME, CALLER_UID))
                .thenReturn(true);
        mMockAdIdWorker.setError(20);
        mAdIdFetcher =
                new AdIdFetcher(
                        mContext, mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);

        Boolean isLatEnabled =
                mAdIdFetcher
                        .isLimitedAdTrackingEnabled(
                                CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS)
                        .get();

        assertThat(isLatEnabled).isEqualTo(DEFAULT_IS_LAT_ENABLED);
    }
}
