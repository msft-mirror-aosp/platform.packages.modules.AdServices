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

package com.android.adservices.service.adselection.debug;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.os.Process;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.adselection.AdIdFetcher;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class AuctionServerDebugConfigurationGeneratorTest extends AdServicesMockitoTestCase {
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final int CALLER_UID = Process.myUid();
    private static final boolean AD_ID_KILL_SWITCH = false;
    private static final boolean ENABLE_DEBUG_REPORTING_IN_AUCTION_SERVER = false;
    private static final boolean ENABLE_PAS_EGRESS_IN_AUCTION_SERVER = false;
    private static final boolean ENABLE_PROD_DEBUG_IN_AUCTION_SERVER = false;
    private static final long AD_ID_FETCHER_TIMEOUT_MS = 20;
    private static final long TEST_TIMEOUT = 5;
    private static final boolean IS_CONSENTED_DEBUG_ENABLED = true;
    private static final String DEBUG_TOKEN = UUID.randomUUID().toString();
    private static final BiddingAuctionServers.ConsentedDebugConfiguration
            CONSENTED_DEBUG_CONFIGURATION =
                    BiddingAuctionServers.ConsentedDebugConfiguration.newBuilder()
                            .setIsConsented(IS_CONSENTED_DEBUG_ENABLED)
                            .setToken(DEBUG_TOKEN)
                            .setIsDebugInfoInResponse(false)
                            .build();

    @Mock private AdIdFetcher mAdIdFetcher;
    @Mock private ConsentedDebugConfigurationGenerator mConsentedDebugConfigurationGenerator;

    private ExecutorService mLightweightExecutorService;
    private AuctionServerDebugConfigurationGenerator mAuctionServerDebugConfigurationGenerator;

    @Before
    public void setUp() {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));
        when(mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration())
                .thenReturn(Optional.of(CONSENTED_DEBUG_CONFIGURATION));
        mAuctionServerDebugConfigurationGenerator = getAuctionServerDebugConfigurationGenerator();
    }

    @Test
    public void test_consentedDebugConfigurationPresent_success() throws Exception {
        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                mAuctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage("consentedDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration.getConsentedDebugConfiguration())
                .isNotNull();
        expect.withMessage("consentedDebugConfiguration equals ")
                .that(auctionServerDebugConfiguration.getConsentedDebugConfiguration())
                .isEqualTo(CONSENTED_DEBUG_CONFIGURATION);
        verify(mConsentedDebugConfigurationGenerator).getConsentedDebugConfiguration();
    }

    @Test
    public void test_consentedDebugConfigurationNotPresent_success() throws Exception {
        when(mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration())
                .thenReturn(Optional.empty());

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                mAuctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage("consentedDebugConfiguration should be null")
                .that(auctionServerDebugConfiguration.getConsentedDebugConfiguration())
                .isNull();
        verify(mConsentedDebugConfigurationGenerator).getConsentedDebugConfiguration();
    }

    @Test
    public void test_allFeatureFlagsDisabled_success() throws Exception {
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                mAuctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isDebugReportingEnabled() should be False")
                .that(auctionServerDebugConfiguration.isDebugReportingEnabled())
                .isFalse();
        expect.withMessage("auctionServerDebugConfiguration.isProdDebugEnabled() should be False")
                .that(auctionServerDebugConfiguration.isProdDebugEnabled())
                .isFalse();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isUnlimitedEgressEnabled() should be"
                                + " False")
                .that(auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                .isFalse();
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void test_adIdKillSwitchEnabled_success() throws Exception {
        boolean adIdKillSwitch = true;
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        AuctionServerDebugConfigurationGenerator auctionServerDebugConfigurationGenerator =
                new AuctionServerDebugConfigurationGenerator(
                        adIdKillSwitch,
                        AD_ID_FETCHER_TIMEOUT_MS,
                        ENABLE_DEBUG_REPORTING_IN_AUCTION_SERVER,
                        ENABLE_PAS_EGRESS_IN_AUCTION_SERVER,
                        ENABLE_PROD_DEBUG_IN_AUCTION_SERVER,
                        mAdIdFetcher,
                        mConsentedDebugConfigurationGenerator,
                        mLightweightExecutorService);

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                auctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isDebugReportingEnabled() should be False")
                .that(auctionServerDebugConfiguration.isDebugReportingEnabled())
                .isFalse();
        expect.withMessage("auctionServerDebugConfiguration.isProdDebugEnabled() should be False")
                .that(auctionServerDebugConfiguration.isProdDebugEnabled())
                .isFalse();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isUnlimitedEgressEnabled() should be"
                                + " False")
                .that(auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                .isFalse();
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void test_adIdKillSwitchEnabled_featureFlagsEnabled_success() throws Exception {
        boolean adIdKillSwitch = true;
        boolean enableDebugReporting = true;
        boolean enableProdDebug = true;
        boolean enableUnlimitedPasEgress = true;
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        AuctionServerDebugConfigurationGenerator auctionServerDebugConfigurationGenerator =
                new AuctionServerDebugConfigurationGenerator(
                        adIdKillSwitch,
                        AD_ID_FETCHER_TIMEOUT_MS,
                        enableDebugReporting,
                        enableUnlimitedPasEgress,
                        enableProdDebug,
                        mAdIdFetcher,
                        mConsentedDebugConfigurationGenerator,
                        mLightweightExecutorService);

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                auctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isDebugReportingEnabled() should be False")
                .that(auctionServerDebugConfiguration.isDebugReportingEnabled())
                .isFalse();
        expect.withMessage("auctionServerDebugConfiguration.isProdDebugEnabled() should be False")
                .that(auctionServerDebugConfiguration.isProdDebugEnabled())
                .isFalse();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isUnlimitedEgressEnabled() should be"
                                + " False")
                .that(auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                .isFalse();
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void test_isLimitedAdTracking_returnsTrue_success() throws Exception {
        boolean enableDebugReporting = true;
        boolean enableProdDebug = true;
        boolean enableUnlimitedPasEgress = true;
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));

        AuctionServerDebugConfigurationGenerator auctionServerDebugConfigurationGenerator =
                new AuctionServerDebugConfigurationGenerator(
                        AD_ID_KILL_SWITCH,
                        AD_ID_FETCHER_TIMEOUT_MS,
                        enableDebugReporting,
                        enableUnlimitedPasEgress,
                        enableProdDebug,
                        mAdIdFetcher,
                        mConsentedDebugConfigurationGenerator,
                        mLightweightExecutorService);

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                auctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isDebugReportingEnabled() should be False")
                .that(auctionServerDebugConfiguration.isDebugReportingEnabled())
                .isFalse();
        expect.withMessage("auctionServerDebugConfiguration.isProdDebugEnabled() should be False")
                .that(auctionServerDebugConfiguration.isProdDebugEnabled())
                .isFalse();
        expect.withMessage(
                        "auctionServerDebugConfiguration.isUnlimitedEgressEnabled() should be"
                                + " False")
                .that(auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                .isFalse();
        verify(mAdIdFetcher)
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void test_enableProdDebugInAuctionServerIsTrue_limitedAdTrackingEnabledFalse_success()
            throws Exception {
        boolean enableProdDebug = true;
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        AuctionServerDebugConfigurationGenerator auctionServerDebugConfigurationGenerator =
                new AuctionServerDebugConfigurationGenerator(
                        AD_ID_KILL_SWITCH,
                        AD_ID_FETCHER_TIMEOUT_MS,
                        ENABLE_DEBUG_REPORTING_IN_AUCTION_SERVER,
                        ENABLE_PAS_EGRESS_IN_AUCTION_SERVER,
                        enableProdDebug,
                        mAdIdFetcher,
                        mConsentedDebugConfigurationGenerator,
                        mLightweightExecutorService);

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                auctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage("isUnlimitedEgressEnabled should return false")
                .that(auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                .isFalse();
        expect.withMessage("isDebugReportingEnabled should return false")
                .that(auctionServerDebugConfiguration.isDebugReportingEnabled())
                .isFalse();
        expect.withMessage("isProdDebugEnabled should return true")
                .that(auctionServerDebugConfiguration.isProdDebugEnabled())
                .isTrue();
        verify(mAdIdFetcher)
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void test_enableDebugReportingInAuctionServer_limitedAdTrackingEnabledFalse_success()
            throws Exception {
        boolean enableDebugReporting = true;
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        AuctionServerDebugConfigurationGenerator auctionServerDebugConfigurationGenerator =
                new AuctionServerDebugConfigurationGenerator(
                        AD_ID_KILL_SWITCH,
                        AD_ID_FETCHER_TIMEOUT_MS,
                        enableDebugReporting,
                        ENABLE_PAS_EGRESS_IN_AUCTION_SERVER,
                        ENABLE_PROD_DEBUG_IN_AUCTION_SERVER,
                        mAdIdFetcher,
                        mConsentedDebugConfigurationGenerator,
                        mLightweightExecutorService);

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                auctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage("isUnlimitedEgressEnabled should return false")
                .that(auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                .isFalse();
        expect.withMessage("isDebugReportingEnabled should return true")
                .that(auctionServerDebugConfiguration.isDebugReportingEnabled())
                .isTrue();
        expect.withMessage("isProdDebugEnabled should return false")
                .that(auctionServerDebugConfiguration.isProdDebugEnabled())
                .isFalse();
        verify(mAdIdFetcher)
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void test_enableUnlimitedPasEgress_limitedAdTrackingEnabledFalse_success()
            throws Exception {
        boolean enablePasUnlimitedEgress = true;
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        AuctionServerDebugConfigurationGenerator auctionServerDebugConfigurationGenerator =
                new AuctionServerDebugConfigurationGenerator(
                        AD_ID_KILL_SWITCH,
                        AD_ID_FETCHER_TIMEOUT_MS,
                        ENABLE_DEBUG_REPORTING_IN_AUCTION_SERVER,
                        enablePasUnlimitedEgress,
                        ENABLE_PROD_DEBUG_IN_AUCTION_SERVER,
                        mAdIdFetcher,
                        mConsentedDebugConfigurationGenerator,
                        mLightweightExecutorService);

        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                auctionServerDebugConfigurationGenerator
                        .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID)
                        .get(TEST_TIMEOUT, TimeUnit.SECONDS);

        expect.withMessage("auctionServerDebugConfiguration should not be null")
                .that(auctionServerDebugConfiguration)
                .isNotNull();
        expect.withMessage("isUnlimitedEgressEnabled should return true")
                .that(auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                .isTrue();
        expect.withMessage("isDebugReportingEnabled should return false")
                .that(auctionServerDebugConfiguration.isDebugReportingEnabled())
                .isFalse();
        expect.withMessage("isProdDebugEnabled should return false")
                .that(auctionServerDebugConfiguration.isProdDebugEnabled())
                .isFalse();
        verify(mAdIdFetcher)
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    private AuctionServerDebugConfigurationGenerator getAuctionServerDebugConfigurationGenerator() {
        return new AuctionServerDebugConfigurationGenerator(
                AD_ID_KILL_SWITCH,
                AD_ID_FETCHER_TIMEOUT_MS,
                ENABLE_DEBUG_REPORTING_IN_AUCTION_SERVER,
                ENABLE_PAS_EGRESS_IN_AUCTION_SERVER,
                ENABLE_PROD_DEBUG_IN_AUCTION_SERVER,
                mAdIdFetcher,
                mConsentedDebugConfigurationGenerator,
                mLightweightExecutorService);
    }
}
