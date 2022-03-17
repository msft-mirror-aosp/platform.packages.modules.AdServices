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

import javax.annotation.concurrent.ThreadSafe;

/**
 * AdServicesLogger that log stats to WestWorld
 */
@ThreadSafe
public class WestWorldAdServicesLogger implements AdServicesLogger {
    private static volatile WestWorldAdServicesLogger sWestWorldAdServicesLogger;

    private WestWorldAdServicesLogger() {
    }

    /** Returns an instance of WestWorldAdServicesLogger. */
    public static WestWorldAdServicesLogger getInstance() {
        if (sWestWorldAdServicesLogger == null) {
            synchronized (WestWorldAdServicesLogger.class) {
                if (sWestWorldAdServicesLogger == null) {
                    sWestWorldAdServicesLogger = new WestWorldAdServicesLogger();
                }
            }
        }
        return sWestWorldAdServicesLogger;
    }
    /**
     * log method for measurement reporting.
     */
    public void logMeasurementReports(MeasurementReportsStats measurementReportsStats) {
        AdServicesStatsLog.write(measurementReportsStats.getCode(),
                measurementReportsStats.getType(), measurementReportsStats.getResultCode());
    }

    /**
     * log method for API call stats.
     */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        AdServicesStatsLog.write(apiCallStats.getCode(), apiCallStats.getApiClass(),
                apiCallStats.getApiName(), apiCallStats.getAppPackageName(),
                apiCallStats.getSdkPackageName(), apiCallStats.getLatencyMillisecond(),
                apiCallStats.getResultCode());
    }
}
