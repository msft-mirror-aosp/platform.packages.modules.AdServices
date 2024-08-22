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
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import com.google.common.util.concurrent.Futures;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@RequiresSdkLevelAtLeastS
public final class DebugReportingTest extends AdServicesMockitoTestCase {

    private static final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final int CALLER_UID = Process.myUid();
    private static final long AD_ID_FETCHER_TIMEOUT_MS = 50;
    @Mock private AdServicesHttpsClient mHttpClientMock;
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @Mock private AdIdFetcher mAdIdFetcher;
    private ExecutorService mLightweightExecutorService;

    @Before
    public void setUp() {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeEventLevelDebugReportSendImmediately()).thenReturn(false);
        when(mMockFlags.getAdIdFetcherTimeoutMs()).thenReturn(AD_ID_FETCHER_TIMEOUT_MS);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));
    }

    @Test
    public void isDisabled_withAdIdServiceKillSwitchTrue_returnsFalse() {
        when(mMockFlags.getAdIdKillSwitch()).thenReturn(true);
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isFalse();
        assertThat(debugReporting).isInstanceOf(DebugReportingDisabled.class);
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isEnabled_withLatDisabledAndFlagEnabled_returnsTrue() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isTrue();
        assertThat(debugReporting).isInstanceOf(DebugReportingEnabled.class);
        verify(mAdIdFetcher, times(1))
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isEnabled_withFlagDisabled_returnsFalse() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isFalse();
        assertThat(debugReporting).isInstanceOf(DebugReportingDisabled.class);
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isEnabled_withLatEnabled_returnsFalse() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isFalse();
        assertThat(debugReporting).isInstanceOf(DebugReportingDisabled.class);
        verify(mAdIdFetcher, times(1))
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void getScriptStrategy_isEnabled_returnsCorrect() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getScriptStrategy())
                .isInstanceOf(DebugReportingEnabledScriptStrategy.class);
    }

    @Test
    public void getScriptStrategy_isDisabled_returnsCorrect() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getScriptStrategy()).isInstanceOf(
                DebugReportingScriptDisabledStrategy.class);
    }

    @Test
    public void getSenderStrategy_isSentImmediatelyEnabled_returnsCorrect() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mMockFlags.getFledgeEventLevelDebugReportSendImmediately()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getSenderStrategy()).isInstanceOf(
                DebugReportSenderStrategyHttpImpl.class);
    }

    @Test
    public void getSenderStrategy_isEnabled_returnsCorrect() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getSenderStrategy())
                .isInstanceOf(DebugReportSenderStrategyBatchImpl.class);
    }

    @Test
    public void getSenderStrategy_isDisabled_returnsCorrect() {
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getSenderStrategy()).isInstanceOf(
                DebugReportSenderStrategyNoOp.class);
    }

    private DebugReporting initDebugReporting() {
        try {
            return DebugReporting.createInstance(
                            mContext,
                            mMockFlags,
                            mHttpClientMock,
                            DEV_CONTEXT_DISABLED,
                            mAdSelectionDebugReportDao,
                            mLightweightExecutorService,
                            mAdIdFetcher,
                            CALLER_PACKAGE_NAME,
                            CALLER_UID)
                    .get();
        } catch (ExecutionException | InterruptedException exception) {
            Assert.fail(
                    "Failed to create instance of debug reporting."
                            + exception.getLocalizedMessage());
            throw new RuntimeException("Unchecked Exception");
        }
    }
}
