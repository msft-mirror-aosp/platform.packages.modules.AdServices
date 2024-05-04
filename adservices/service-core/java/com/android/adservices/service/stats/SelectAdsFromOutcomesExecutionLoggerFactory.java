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

import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.shared.util.Clock;

import java.util.Objects;

public class SelectAdsFromOutcomesExecutionLoggerFactory {
    private final Clock mClock;

    private final AdServicesLogger mAdServicesLogger;

    private final boolean mFledgeSelectAdsFromOutcomesMetricsEnabled;

    public SelectAdsFromOutcomesExecutionLoggerFactory(
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        mFledgeSelectAdsFromOutcomesMetricsEnabled =
                BinderFlagReader.readFlag(flags::getFledgeSelectAdsFromOutcomesApiMetricsEnabled);
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Gets the {@link SelectAdsFromOutcomesExecutionLogger} implementation to use, dependent on
     * whether the Fledge Select Ads From Outcomes Metrics Enabled are enabled.
     *
     * @return an {@link SelectAdsFromOutcomesExecutionLoggerImpl} instance if the Fledge Select Ads
     *     From Outcomes metrics are enabled, or {@link
     *     SelectAdsFromOutcomesExecutionLoggerNoLoggingImpl} instance otherwise
     */
    public SelectAdsFromOutcomesExecutionLogger getSelectAdsFromOutcomesExecutionLogger() {
        if (mFledgeSelectAdsFromOutcomesMetricsEnabled) {
            return new SelectAdsFromOutcomesExecutionLoggerImpl(mClock, mAdServicesLogger);
        }
        return new SelectAdsFromOutcomesExecutionLoggerNoLoggingImpl();
    }
}
