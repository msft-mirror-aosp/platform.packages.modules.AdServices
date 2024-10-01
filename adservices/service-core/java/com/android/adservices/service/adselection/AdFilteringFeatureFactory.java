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

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorImpl;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;

import java.time.Clock;
import java.util.Objects;

/** Factory for implementations of the ad filtering feature interfaces. */
public final class AdFilteringFeatureFactory {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final boolean mIsFledgeFrequencyCapFilteringEnabled;
    private final boolean mIsFledgeAppInstallFilteringEnabled;
    private final int mHistogramAbsoluteMaxTotalEventCount;
    private final int mHistogramLowerMaxTotalEventCount;
    private final int mHistogramAbsoluteMaxPerBuyerEventCount;
    private final int mHistogramLowerMaxPerBuyerEventCount;
    private final AppInstallDao mAppInstallDao;
    private final FrequencyCapDao mFrequencyCapDao;
    private final boolean mShouldUseUnifiedTables;

    public AdFilteringFeatureFactory(
            AppInstallDao appInstallDao, FrequencyCapDao frequencyCapDao, Flags flags) {
        mIsFledgeFrequencyCapFilteringEnabled =
                BinderFlagReader.readFlag(flags::getFledgeFrequencyCapFilteringEnabled);
        mIsFledgeAppInstallFilteringEnabled =
                BinderFlagReader.readFlag(flags::getFledgeAppInstallFilteringEnabled);
        mHistogramAbsoluteMaxTotalEventCount =
                BinderFlagReader.readFlag(
                        flags::getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount);
        mHistogramLowerMaxTotalEventCount =
                BinderFlagReader.readFlag(
                        flags::getFledgeAdCounterHistogramLowerMaxTotalEventCount);
        mHistogramAbsoluteMaxPerBuyerEventCount =
                BinderFlagReader.readFlag(
                        flags::getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount);
        mHistogramLowerMaxPerBuyerEventCount =
                BinderFlagReader.readFlag(
                        flags::getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount);
        mShouldUseUnifiedTables =
                BinderFlagReader.readFlag(flags::getFledgeOnDeviceAuctionShouldUseUnifiedTables);

        mAppInstallDao = appInstallDao;
        mFrequencyCapDao = frequencyCapDao;
        sLogger.v(
                "Initializing AdFilteringFeatureFactory with frequency cap filtering %s and app"
                        + " install filtering %s",
                mIsFledgeFrequencyCapFilteringEnabled ? "enabled" : "disabled",
                mIsFledgeAppInstallFilteringEnabled ? "enabled" : "disabled");
    }

    /**
     * Returns the correct {@link FrequencyCapAdFilterer} implementation to use based on the given
     * {@link Flags}.
     *
     * @return an instance of {@link FrequencyCapAdFiltererImpl} if frequency cap filtering is
     *     enabled and an instance of {@link FrequencyCapAdFiltererNoOpImpl} otherwise
     */
    public FrequencyCapAdFilterer getFrequencyCapAdFilterer() {
        if (mIsFledgeFrequencyCapFilteringEnabled) {
            return new FrequencyCapAdFiltererImpl(mFrequencyCapDao, Clock.systemUTC());
        } else {
            return new FrequencyCapAdFiltererNoOpImpl();
        }
    }

    /**
     * Returns the correct {@link AppInstallAdFilterer} implementation to use based on the given
     * {@link Flags}.
     *
     * @return an instance of {@link AppInstallAdFiltererImpl} if app install filtering is enabled
     *     and an instance of {@link AppInstallAdFiltererNoOpImpl} otherwise
     */
    public AppInstallAdFilterer getAppInstallAdFilterer() {
        if (mIsFledgeAppInstallFilteringEnabled) {
            return new AppInstallAdFiltererImpl(mAppInstallDao, Clock.systemUTC());
        } else {
            return new AppInstallAdFiltererNoOpImpl();
        }
    }

    /**
     * Gets the {@link AdCounterKeyCopier} implementation to use, dependent on whether the ad
     * filtering features is enabled.
     *
     * @return an {@link AdCounterKeyCopierImpl} instance if the ad filtering feature is enabled, or
     *     an {@link AdCounterKeyCopierNoOpImpl} instance otherwise
     */
    public AdCounterKeyCopier getAdCounterKeyCopier() {
        if (mIsFledgeFrequencyCapFilteringEnabled) {
            return new AdCounterKeyCopierImpl();
        } else {
            return new AdCounterKeyCopierNoOpImpl();
        }
    }

    /**
     * Gets the {@link FrequencyCapAdDataValidator} implementation to use, dependent on whether the
     * ad filtering feature is enabled.
     *
     * @return a {@link FrequencyCapAdDataValidatorImpl} instance if the ad filtering feature is
     *     enabled, or a {@link FrequencyCapAdDataValidatorNoOpImpl} instance otherwise
     */
    public FrequencyCapAdDataValidator getFrequencyCapAdDataValidator() {
        if (mIsFledgeFrequencyCapFilteringEnabled) {
            return new FrequencyCapAdDataValidatorImpl();
        } else {
            return new FrequencyCapAdDataValidatorNoOpImpl();
        }
    }

    /**
     * Gets the {@link FrequencyCapDataClearer} implementation to use, dependent on whether the ad
     * filtering feature is enabled.
     *
     * @return The desired {@link FrequencyCapDataClearer} to use.
     */
    public FrequencyCapDataClearer getFrequencyCapDataClearer() {
        if (mIsFledgeFrequencyCapFilteringEnabled) {
            return new FrequencyCapDataClearerImpl(mFrequencyCapDao);
        } else {
            return new FrequencyCapDataClearerNoOp();
        }
    }

    /**
     * Gets the {@link AdCounterHistogramUpdater} implementation to use, dependent on whether the ad
     * filtering feature is enabled.
     *
     * @return a {@link AdCounterHistogramUpdaterImpl} instance if the ad filtering feature is
     *     enabled, or a {@link AdCounterHistogramUpdaterNoOpImpl} instance otherwise
     */
    public AdCounterHistogramUpdater getAdCounterHistogramUpdater(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            boolean auctionServerEnabledForUpdateHistogram) {
        Objects.requireNonNull(adSelectionEntryDao);

        if (mIsFledgeFrequencyCapFilteringEnabled) {
            return new AdCounterHistogramUpdaterImpl(
                    adSelectionEntryDao,
                    mFrequencyCapDao,
                    mHistogramAbsoluteMaxTotalEventCount,
                    mHistogramLowerMaxTotalEventCount,
                    mHistogramAbsoluteMaxPerBuyerEventCount,
                    mHistogramLowerMaxPerBuyerEventCount,
                    auctionServerEnabledForUpdateHistogram,
                    mShouldUseUnifiedTables);
        } else {
            return new AdCounterHistogramUpdaterNoOpImpl();
        }
    }
}
