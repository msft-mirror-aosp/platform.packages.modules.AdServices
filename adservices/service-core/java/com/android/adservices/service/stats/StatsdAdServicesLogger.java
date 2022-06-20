/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;

import javax.annotation.concurrent.ThreadSafe;

/** AdServicesLogger that log stats to StatsD */
@ThreadSafe
public class StatsdAdServicesLogger implements AdServicesLogger {
    private static volatile StatsdAdServicesLogger sStatsdAdServicesLogger;

    private StatsdAdServicesLogger() {}

    /** Returns an instance of WestWorldAdServicesLogger. */
    public static StatsdAdServicesLogger getInstance() {
        if (sStatsdAdServicesLogger == null) {
            synchronized (StatsdAdServicesLogger.class) {
                if (sStatsdAdServicesLogger == null) {
                    sStatsdAdServicesLogger = new StatsdAdServicesLogger();
                }
            }
        }
        return sStatsdAdServicesLogger;
    }
    /** log method for measurement reporting. */
    public void logMeasurementReports(MeasurementReportsStats measurementReportsStats) {
        AdServicesStatsLog.write(
                measurementReportsStats.getCode(),
                measurementReportsStats.getType(),
                measurementReportsStats.getResultCode());
    }

    /** log method for API call stats. */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        AdServicesStatsLog.write(
                apiCallStats.getCode(),
                apiCallStats.getApiClass(),
                apiCallStats.getApiName(),
                apiCallStats.getAppPackageName(),
                apiCallStats.getSdkPackageName(),
                apiCallStats.getLatencyMillisecond(),
                apiCallStats.getResultCode());
    }

    @Override
    public void logFledgeApiCallStats(int apiName, int resultCode) {
        // TODO(b/233628316): Implement latency measurement
        logApiCallStats(
                new ApiCallStats.Builder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__FLEDGE)
                        .setApiName(apiName)
                        .setResultCode(resultCode)
                        // TODO(b/233629557): Implement app/SDK reporting
                        .setSdkPackageName("")
                        .setAppPackageName("")
                        .build());
    }
}
