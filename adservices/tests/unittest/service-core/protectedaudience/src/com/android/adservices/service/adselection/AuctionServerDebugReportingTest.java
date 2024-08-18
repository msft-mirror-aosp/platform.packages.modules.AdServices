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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.os.Process;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import com.google.common.util.concurrent.Futures;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@RequiresSdkLevelAtLeastS
public final class AuctionServerDebugReportingTest extends AdServicesMockitoTestCase {

    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final int CALLER_UID = Process.myUid();
    private static final long AD_ID_FETCHER_TIMEOUT_MS = 20;

    @Mock private Flags mMockFlags;
    @Mock private AdIdFetcher mAdIdFetcher;
    private ExecutorService mLightweightExecutorService;

    @Before
    public void setUp() {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeAuctionServerEnableDebugReporting()).thenReturn(false);
        when(mMockFlags.getFledgeAuctionServerAdIdFetcherTimeoutMs())
                .thenReturn(AD_ID_FETCHER_TIMEOUT_MS);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));
    }

    @Test
    public void isDisabled_withAdIdServiceKillSwitchTrue() {
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(true);

        AuctionServerDebugReporting auctionServerDebugReporting = initAuctionServerDebugReporting();

        assertThat(auctionServerDebugReporting.isEnabled()).isFalse();
        verify(mMockFlags, times(1)).getAdIdKillSwitch();
        verify(mMockFlags, never()).getFledgeAuctionServerEnableDebugReporting();
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isDisabled_withDisabledDebugReporting() {
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeAuctionServerEnableDebugReporting()).thenReturn(false);

        AuctionServerDebugReporting auctionServerDebugReporting = initAuctionServerDebugReporting();

        assertThat(auctionServerDebugReporting.isEnabled()).isFalse();
        verify(mMockFlags, times(1)).getFledgeAuctionServerEnableDebugReporting();
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isDisabled_withLatTrue() {
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeAuctionServerEnableDebugReporting()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));

        AuctionServerDebugReporting auctionServerDebugReporting = initAuctionServerDebugReporting();

        assertThat(auctionServerDebugReporting.isEnabled()).isFalse();
        verify(mAdIdFetcher, times(1))
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isEnabled_withLatFalse() {
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeAuctionServerEnableDebugReporting()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        AuctionServerDebugReporting auctionServerDebugReporting = initAuctionServerDebugReporting();

        assertThat(auctionServerDebugReporting.isEnabled()).isTrue();
        verify(mAdIdFetcher, times(1))
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    private AuctionServerDebugReporting initAuctionServerDebugReporting() {
        try {
            return AuctionServerDebugReporting.createInstance(
                            mMockFlags,
                            mAdIdFetcher,
                            CALLER_PACKAGE_NAME,
                            CALLER_UID,
                            mLightweightExecutorService)
                    .get();
        } catch (ExecutionException | InterruptedException exception) {
            Assert.fail(
                    "Failed to create instance of auction server debug reporting."
                            + exception.getLocalizedMessage());
            throw new RuntimeException("Unchecked Exception");
        }
    }
}