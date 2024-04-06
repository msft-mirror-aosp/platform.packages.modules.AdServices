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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CONTEXTUAL_ADS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;

import java.util.Objects;

/** Logger factory for {@link AdFilteringLogger} */
public class AdFilteringLoggerFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String NO_OP_LOGGER =
            "Both FledgeAppInstallFilteringMetricsEnabled and "
                    + "FledgeFrequencyCapFilteringMetricsEnabled flags are off NoOp logger is "
                    + "returned";
    @NonNull private final AdServicesLogger mAdServicesLogger;
    private final boolean mIsAppInstallMetricsEnabled;
    private final boolean mIsFrequencyCapMetricsEnabled;

    public AdFilteringLoggerFactory(
            @NonNull AdServicesLogger adServicesLogger, @NonNull Flags flags) {
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);

        mAdServicesLogger = adServicesLogger;
        mIsAppInstallMetricsEnabled = flags.getFledgeAppInstallFilteringMetricsEnabled();
        mIsFrequencyCapMetricsEnabled = flags.getFledgeFrequencyCapFilteringMetricsEnabled();
    }

    /** Returns a {@link AdFilteringLogger} implementation. */
    public AdFilteringLogger getCustomAudienceFilteringLogger() {
        if (mIsAppInstallMetricsEnabled || mIsFrequencyCapMetricsEnabled) {
            return new AdFilteringLoggerImpl(
                    FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES, mAdServicesLogger);
        } else {
            return new AdFilteringLoggerNoOp();
        }
    }

    /** Returns a {@link AdFilteringLogger} implementation. */
    public AdFilteringLogger getContextualAdFilteringLogger() {
        if (mIsAppInstallMetricsEnabled || mIsFrequencyCapMetricsEnabled) {
            return new AdFilteringLoggerImpl(FILTER_PROCESS_TYPE_CONTEXTUAL_ADS, mAdServicesLogger);
        } else {
            sLogger.v(NO_OP_LOGGER);
            return new AdFilteringLoggerNoOp();
        }
    }
}
