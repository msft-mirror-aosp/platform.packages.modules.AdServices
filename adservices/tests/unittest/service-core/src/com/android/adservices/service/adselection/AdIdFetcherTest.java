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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.adid.AdId;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class AdIdFetcherTest {
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final int CALLER_UID = Process.myUid();
    private Context mContext;
    private Flags mFlags;
    private MockAdIdWorker mMockAdIdWorker;
    private ExecutorService mLightweightExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private AdIdFetcher mAdIdFetcher;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mFlags = new AdIdFetcherTestFlags(false);
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mMockAdIdWorker = new MockAdIdWorker(mContext, mFlags);
    }

    @Test
    public void testConstructorNullArguments() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdIdFetcher(
                                null, mLightweightExecutorService, mScheduledExecutor, mFlags));
        assertThrows(
                NullPointerException.class,
                () -> new AdIdFetcher(mMockAdIdWorker, null, mScheduledExecutor, mFlags));
        assertThrows(
                NullPointerException.class,
                () -> new AdIdFetcher(mMockAdIdWorker, mLightweightExecutorService, null, mFlags));
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdIdFetcher(
                                mMockAdIdWorker,
                                mLightweightExecutorService,
                                mScheduledExecutor,
                                null));
    }

    @Test
    public void testIsLimitedTrackingEnabled_Disabled_ReturnsFalse()
            throws ExecutionException, InterruptedException {
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);
        mAdIdFetcher =
                new AdIdFetcher(
                        mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor, mFlags);

        Boolean isLatEnabled =
                mAdIdFetcher.isLimitedAdTrackingEnabled(CALLER_PACKAGE_NAME, CALLER_UID).get();

        assertEquals(false, isLatEnabled);
    }

    @Test
    public void testIsLimitedTrackingEnabled_LatEnabled_ReturnsTrue()
            throws ExecutionException, InterruptedException {
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, true);
        mAdIdFetcher =
                new AdIdFetcher(
                        mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor, mFlags);

        Boolean isLatEnabled =
                mAdIdFetcher.isLimitedAdTrackingEnabled(CALLER_PACKAGE_NAME, CALLER_UID).get();

        assertEquals(true, isLatEnabled);
    }

    @Test
    public void testIsLimitedTrackingEnabled_AdIdZeroedOut_ReturnsTrue()
            throws ExecutionException, InterruptedException {
        mMockAdIdWorker.setResult(AdId.ZERO_OUT, false);
        mAdIdFetcher =
                new AdIdFetcher(
                        mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor, mFlags);

        Boolean isLatEnabled =
                mAdIdFetcher.isLimitedAdTrackingEnabled(CALLER_PACKAGE_NAME, CALLER_UID).get();

        assertEquals(true, isLatEnabled);
    }

    @Test
    public void testIsLimitedTrackingEnabled_TimeoutException_ReturnsTrue()
            throws ExecutionException, InterruptedException {
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);
        mMockAdIdWorker.setDelay(AdIdFetcher.AD_ID_TIMEOUT_IN_MS * 2);
        mAdIdFetcher =
                new AdIdFetcher(
                        mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor, mFlags);

        Boolean isLatEnabled =
                mAdIdFetcher.isLimitedAdTrackingEnabled(CALLER_PACKAGE_NAME, CALLER_UID).get();

        assertTrue(isLatEnabled);
    }

    @Test
    public void testIsLimitedTrackingEnabled_OnError_ReturnsTrue()
            throws ExecutionException, InterruptedException {
        mMockAdIdWorker.setError(20);
        mAdIdFetcher =
                new AdIdFetcher(
                        mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor, mFlags);

        Boolean isLatEnabled =
                mAdIdFetcher.isLimitedAdTrackingEnabled(CALLER_PACKAGE_NAME, CALLER_UID).get();

        assertTrue(isLatEnabled);
    }

    @Test
    public void testIsLimitedTrackingEnabled_OnAdIdKillSwitchTrue_ReturnsTrue()
            throws ExecutionException, InterruptedException {
        Flags flags = new AdIdFetcherTestFlags(true);
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);
        mAdIdFetcher =
                new AdIdFetcher(
                        mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor, flags);

        Boolean isLatEnabled =
                mAdIdFetcher.isLimitedAdTrackingEnabled(CALLER_PACKAGE_NAME, CALLER_UID).get();

        assertTrue(isLatEnabled);
    }

    static class AdIdFetcherTestFlags implements Flags {
        private final boolean mAdIdKillSwitch;

        AdIdFetcherTestFlags(boolean adIdKillSwitch) {
            mAdIdKillSwitch = adIdKillSwitch;
        }

        @Override
        public boolean getAdIdKillSwitch() {
            return mAdIdKillSwitch;
        }
    }
}
