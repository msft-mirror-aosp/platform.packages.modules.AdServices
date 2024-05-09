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

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;

import java.util.Objects;

public final class ReportImpressionExecutionLoggerFactory {
    private final AdServicesLogger mAdServicesLogger;

    private final Flags mFlags;

    private final boolean mFledgeReportImpressionApiMetricsEnabled;

    public ReportImpressionExecutionLoggerFactory(AdServicesLogger adServicesLogger, Flags flags) {
        mAdServicesLogger = Objects.requireNonNull(adServicesLogger);
        mFlags = Objects.requireNonNull(flags);
        mFledgeReportImpressionApiMetricsEnabled =
                BinderFlagReader.readFlag(flags::getFledgeReportImpressionApiMetricsEnabled);
    }

    /**
     * Gets the {@link ReportImpressionExecutionLogger} implementation to use, dependent on whether
     * the Fledge Select Ads From Outcomes Metrics Enabled are enabled.
     *
     * @return an {@link ReportImpressionExecutionLoggerImpl} instance if the Fledge Select Ads From
     *     Outcomes metrics are enabled, or {@link ReportImpressionExecutionLoggerNoLoggingImpl}
     *     instance otherwise
     */
    public ReportImpressionExecutionLogger getReportImpressionExecutionLogger() {
        if (mFledgeReportImpressionApiMetricsEnabled) {
            return new ReportImpressionExecutionLoggerImpl(mAdServicesLogger, mFlags);
        }
        return new ReportImpressionExecutionLoggerNoLoggingImpl();
    }
}
