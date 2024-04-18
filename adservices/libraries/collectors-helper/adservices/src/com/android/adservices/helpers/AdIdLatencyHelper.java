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

package com.android.adservices.helpers;

import com.android.helpers.LatencyHelper;

import com.google.common.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A helper class consists helper methods to collect Ad ID API hot/code start-up latencies. */
public final class AdIdLatencyHelper {
    /** The tag used in {@link LatencyHelper} to collect stats from specific labels. */
    public static final String TAG = "GetAdIdApiCall";

    /** A tag used to measure Ad ID API hot start-up latency. */
    public static final String AD_ID_HOT_START_LATENCY_METRIC = "AD_ID_HOT_START_LATENCY_METRIC";

    /** A tag used to measure Ad ID API cold start-up latency. */
    public static final String AD_ID_COLD_START_LATENCY_METRIC = "AD_ID_COLD_START_LATENCY_METRIC";

    /** Gets a logcat version of {@link LatencyHelper}. */
    public static LatencyHelper getLogcatCollector() {
        return LatencyHelper.getLogcatLatencyHelper(
                new AdIdLatencyHelper.AdIdProcessInputForLatencyMetrics());
    }

    @VisibleForTesting
    static LatencyHelper getCollector(LatencyHelper.InputStreamFilter inputStreamFilter) {
        return new LatencyHelper(
                new AdIdLatencyHelper.AdIdProcessInputForLatencyMetrics(), inputStreamFilter);
    }

    private static class AdIdProcessInputForLatencyMetrics
            implements LatencyHelper.ProcessInputForLatencyMetrics {
        @Override
        public String getTestLabel() {
            return TAG;
        }

        @Override
        public Map<String, Long> processInput(InputStream inputStream) throws IOException {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            Pattern latencyMetricPattern = Pattern.compile(getTestLabel() + ": \\((.*): (\\d+)\\)");

            String line;
            Map<String, Long> output = new HashMap<>();
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = latencyMetricPattern.matcher(line);
                while (matcher.find()) {
                    // The lines from Logcat will look like: 06-13 18:09:24.058 20765 20781 D
                    // GetAdIdApiCall: (AD_ID_HOT_START_LATENCY_METRIC: 14)
                    String metric = matcher.group(1);
                    long latency = Long.parseLong(matcher.group(2));
                    if (AD_ID_HOT_START_LATENCY_METRIC.equals(metric)) {
                        output.put(AD_ID_HOT_START_LATENCY_METRIC, latency);
                    } else if (AD_ID_COLD_START_LATENCY_METRIC.equals(metric)) {
                        output.put(AD_ID_COLD_START_LATENCY_METRIC, latency);
                    }
                }
            }
            return output;
        }
    }
}
