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

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.adselection.AdIdFetcher;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/**
 * This class populates all the debugging and egress related configurations in the payload sent to
 * auction server
 */
public final class AuctionServerDebugConfigurationGenerator {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final boolean mAdIdKillSwitch;
    private final long mAuctionServerAdIdFetchTimeoutMs;
    private final boolean mEnableDebugReportingInAuctionServer;
    private final boolean mEnablePasUnlimitedEgressInAuctionServer;
    private final boolean mEnableProdDebugInAuctionServer;
    private final AdIdFetcher mAdIdFetcher;
    private final ConsentedDebugConfigurationGenerator mConsentedDebugConfigurationGenerator;
    private final Executor mLightWeightExecutor;

    public AuctionServerDebugConfigurationGenerator(
            boolean adIdKillSwitch,
            long auctionServerAdIdFetchTimeoutM,
            boolean enableDebugReportingInAuctionServer,
            boolean enablePasUnlimitedEgressInAuctionServer,
            boolean enableProdDebugInAuctionServer,
            AdIdFetcher adIdFetcher,
            ConsentedDebugConfigurationGenerator consentedDebugConfigurationGenerator,
            Executor lightWeightExecutor) {
        mAdIdKillSwitch = adIdKillSwitch;
        mEnableDebugReportingInAuctionServer = enableDebugReportingInAuctionServer;
        mAuctionServerAdIdFetchTimeoutMs = auctionServerAdIdFetchTimeoutM;
        mEnablePasUnlimitedEgressInAuctionServer = enablePasUnlimitedEgressInAuctionServer;
        mEnableProdDebugInAuctionServer = enableProdDebugInAuctionServer;
        mAdIdFetcher = adIdFetcher;
        mConsentedDebugConfigurationGenerator = consentedDebugConfigurationGenerator;
        mLightWeightExecutor = lightWeightExecutor;
    }

    /**
     * Generates debug configurations for server auction payload.
     *
     * @param packageName the caller package name.
     * @param callerUid the caller UID.
     * @return {@link AuctionServerDebugConfiguration}
     */
    public ListenableFuture<AuctionServerDebugConfiguration> getAuctionServerDebugConfiguration(
            String packageName, int callerUid) {
        return FluentFuture.from(isLimitedAdTrackingEnabled(packageName, callerUid))
                .transform(
                        isLimitedAdTrackingEnabled -> {
                            AuctionServerDebugConfiguration.Builder configurationBuilder =
                                    AuctionServerDebugConfiguration.builder()
                                            .setDebugReportingEnabled(
                                                    mEnableDebugReportingInAuctionServer
                                                            && !isLimitedAdTrackingEnabled)
                                            .setProdDebugEnabled(
                                                    mEnableProdDebugInAuctionServer
                                                            && !isLimitedAdTrackingEnabled)
                                            .setUnlimitedEgressEnabled(
                                                    mEnablePasUnlimitedEgressInAuctionServer
                                                            && !isLimitedAdTrackingEnabled);
                            mConsentedDebugConfigurationGenerator
                                    .getConsentedDebugConfiguration()
                                    .ifPresent(
                                            configurationBuilder::setConsentedDebugConfiguration);
                            AuctionServerDebugConfiguration configuration =
                                    configurationBuilder.build();
                            sLogger.v("AuctionServerDebugConfiguration is: %s", configuration);
                            return configuration;
                        },
                        mLightWeightExecutor);
    }

    private ListenableFuture<Boolean> isLimitedAdTrackingEnabled(
            String packageName, int callingUid) {
        if (mAdIdKillSwitch) {
            sLogger.v(
                    "AdIdService kill switch is enabled, returning  "
                            + "isLimitedAdTrackingEnabled as true");
            return Futures.immediateFuture(true);
        }
        if (mEnableDebugReportingInAuctionServer
                || mEnablePasUnlimitedEgressInAuctionServer
                || mEnableProdDebugInAuctionServer) {
            return FluentFuture.from(
                    mAdIdFetcher.isLimitedAdTrackingEnabled(
                            packageName, callingUid, mAuctionServerAdIdFetchTimeoutMs));
        }
        sLogger.v(
                "All feature flags are set to false, returning isLimitedAdTrackingEnabled as true");
        return Futures.immediateFuture(true);
    }
}
