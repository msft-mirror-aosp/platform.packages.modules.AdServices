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

import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.BINDER_ELAPSED_TIMESTAMP;

import android.adservices.common.CallerMetadata;

public class AdFilteringLoggerImplTestFixture {
    public static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    public static final int APP_INSTALL_FILTERING_LATENCY_MS = 3;
    public static final int FREQUENCY_CAP_FILTERING_LATENCY_MS = 5;
    public static final int AD_FILTERING_OVERALL_LATENCY_MS =
            APP_INSTALL_FILTERING_LATENCY_MS + FREQUENCY_CAP_FILTERING_LATENCY_MS;
    public static final long AD_FILTERING_START = START_ELAPSED_TIMESTAMP + 1L;
    public static final long APP_INSTALL_FILTERING_START = AD_FILTERING_START;
    public static final long APP_INSTALL_FILTERING_END =
            APP_INSTALL_FILTERING_START + APP_INSTALL_FILTERING_LATENCY_MS;
    public static final long FREQ_CAP_FILTERING_START = APP_INSTALL_FILTERING_END;
    public static final long FREQ_CAP_FILTERING_END =
            FREQ_CAP_FILTERING_START + FREQUENCY_CAP_FILTERING_LATENCY_MS;
    public static final long AD_FILTERING_END = FREQ_CAP_FILTERING_END;
}
