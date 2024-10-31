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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;

import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ReportImpressionExecutionLogger;
import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
public final class ImpressionReporterTest extends AdServicesExtendedMockitoTestCase {
    private static final long AD_SELECTION_ID = 100;
    private static final int LOGGING_TIMEOUT_MS = 5_000;

    @Mock private AdServicesHttpsClient mMockAdServicesHttpsClient;
    @Mock private AdServicesLogger mMockAdServicesLogger;
    @Mock private AdSelectionServiceFilter mMockAdSelectionServiceFilter;
    @Mock private FledgeAuthorizationFilter mMockFledgeAuthorizationFilter;
    @Mock private ReportImpressionExecutionLogger mMockReportImpressionExecutionLogger;

    private AdSelectionEntryDao mAdSelectionEntryDao;
    private CustomAudienceDao mCustomAudienceDao;

    @Before
    public void setup() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(sContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(sContext, CustomAudienceDatabase.class)
                        .addTypeConverter(
                                new DBCustomAudience.Converters(
                                        /* frequencyCapFilteringEnabled= */ true,
                                        /* appInstallFilteringEnabled= */ true,
                                        /* adRenderIdEnabled= */ true))
                        .build()
                        .customAudienceDao();
    }

    @Test
    public void testReportImpression_unifiedTables_persistedBuyerUriEmpty_skipsBuyer()
            throws Exception {
        when(mMockAdServicesHttpsClient.getAndReadNothing(any(), any()))
                .thenReturn(immediateVoidFuture());
        AdSelectionInitialization initialization =
                AdSelectionInitialization.builder()
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setCreationInstant(CommonFixture.FIXED_NOW)
                        .setSeller(CommonFixture.VALID_BUYER_1)
                        .build();
        ReportingData reportingData =
                ReportingData.builder()
                        .setBuyerWinReportingUri(Uri.EMPTY)
                        .setSellerWinReportingUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        CommonFixture.VALID_BUYER_1.toString(), "/report/seller"))
                        .build();
        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, initialization);
        mAdSelectionEntryDao.persistReportingData(AD_SELECTION_ID, reportingData);
        Flags flagsWithAuctionServerEnabled =
                new Flags() {
                    @Override
                    public boolean getFledgeAuctionServerKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportImpression() {
                        return true;
                    }
                };
        ImpressionReporter reporter =
                new ImpressionReporter(
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mMockAdServicesHttpsClient,
                        DevContext.createForDevOptionsDisabled(),
                        mMockAdServicesLogger,
                        flagsWithAuctionServerEnabled,
                        mMockDebugFlags,
                        mMockAdSelectionServiceFilter,
                        mMockFledgeAuthorizationFilter,
                        new FrequencyCapAdDataValidatorNoOpImpl(),
                        Process.myUid(),
                        new NoOpRetryStrategyImpl(),
                        /* shouldUseUnifiedTables= */ true,
                        mMockReportImpressionExecutionLogger);
        SyncReportImpressionCallback callback = new SyncReportImpressionCallback();
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(
                                AdSelectionConfigFixture.anAdSelectionConfig(
                                        CommonFixture.VALID_BUYER_1))
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        reporter.reportImpression(input, callback);
        callback.assertResultReceived();

        verify(mMockAdServicesLogger, timeout(LOGGING_TIMEOUT_MS))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        anyString(),
                        eq(STATUS_SUCCESS),
                        anyInt());
        verify(mMockAdServicesHttpsClient).getAndReadNothing(any(), any());
    }

    @Test
    public void testReportImpression_unifiedTables_persistedBuyerUriMismatched_skipsBuyer()
            throws Exception {
        when(mMockAdServicesHttpsClient.getAndReadNothing(any(), any()))
                .thenReturn(immediateVoidFuture());
        AdSelectionInitialization initialization =
                AdSelectionInitialization.builder()
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setCreationInstant(CommonFixture.FIXED_NOW)
                        .setSeller(CommonFixture.VALID_BUYER_1)
                        .build();
        ReportingData reportingData =
                ReportingData.builder()
                        .setBuyerWinReportingUri(Uri.parse("this.is.not.a.valid.enrolled.uri"))
                        .setSellerWinReportingUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        CommonFixture.VALID_BUYER_1.toString(), "/report/seller"))
                        .build();
        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, initialization);
        mAdSelectionEntryDao.persistReportingData(AD_SELECTION_ID, reportingData);
        Flags flagsWithAuctionServerEnabled =
                new Flags() {
                    @Override
                    public boolean getFledgeAuctionServerKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportImpression() {
                        return true;
                    }
                };
        ImpressionReporter reporter =
                new ImpressionReporter(
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mMockAdServicesHttpsClient,
                        DevContext.createForDevOptionsDisabled(),
                        mMockAdServicesLogger,
                        flagsWithAuctionServerEnabled,
                        mMockDebugFlags,
                        mMockAdSelectionServiceFilter,
                        mMockFledgeAuthorizationFilter,
                        new FrequencyCapAdDataValidatorNoOpImpl(),
                        Process.myUid(),
                        new NoOpRetryStrategyImpl(),
                        /* shouldUseUnifiedTables= */ true,
                        mMockReportImpressionExecutionLogger);
        SyncReportImpressionCallback callback = new SyncReportImpressionCallback();
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(
                                AdSelectionConfigFixture.anAdSelectionConfig(
                                        CommonFixture.VALID_BUYER_1))
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        reporter.reportImpression(input, callback);
        callback.assertResultReceived();

        verify(mMockAdServicesLogger, timeout(LOGGING_TIMEOUT_MS))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        anyString(),
                        eq(STATUS_SUCCESS),
                        anyInt());
        verify(mMockAdServicesHttpsClient).getAndReadNothing(any(), any());
    }

    private static final class SyncReportImpressionCallback
            extends FailableOnResultSyncCallback<Object, FledgeErrorResponse>
            implements ReportImpressionCallback {
        @Override
        public void onSuccess() throws RemoteException {
            injectResult(null);
        }
    }
}
