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

/** AdServicesLogger that delegate to the appropriate Logger Implementations. */
@ThreadSafe
public class AdServicesLoggerImpl implements AdServicesLogger {
    private static volatile AdServicesLoggerImpl sAdServicesLogger;

    private AdServicesLoggerImpl() {}

    /** Returns an instance of AdServicesLogger. */
    public static AdServicesLoggerImpl getInstance() {
        if (sAdServicesLogger == null) {
            synchronized (AdServicesLoggerImpl.class) {
                if (sAdServicesLogger == null) {
                    sAdServicesLogger = new AdServicesLoggerImpl();
                }
            }
        }
        return sAdServicesLogger;
    }

    @Override
    public void logMeasurementReports(MeasurementReportsStats measurementReportsStats) {
        StatsdAdServicesLogger.getInstance().logMeasurementReports(measurementReportsStats);
    }

    @Override
    public void logApiCallStats(ApiCallStats apiCallStats) {
        StatsdAdServicesLogger.getInstance().logApiCallStats(apiCallStats);
    }
}
