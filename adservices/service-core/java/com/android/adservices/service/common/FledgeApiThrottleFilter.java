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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;

import android.adservices.common.AdServicesStatusUtils;
import android.os.LimitExceededException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.stats.AdServicesLogger;

/** Filter for checking API throttling in the PA/PAS (formerly FLEDGE) APIs. */
public class FledgeApiThrottleFilter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final Throttler mThrottler;
    private final AdServicesLogger mAdServicesLogger;

    public FledgeApiThrottleFilter(Throttler throttler, AdServicesLogger adServicesLogger) {
        mThrottler = throttler;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Asserts that the caller package is not throttled from calling the current API.
     *
     * <p>Also logs telemetry for the API call.
     *
     * @param callerPackageName the package name, which should be verified
     * @throws LimitExceededException if the provided {@code callerPackageName} exceeds its rate
     *     limits
     */
    public void assertCallerNotThrottled(
            final String callerPackageName, Throttler.ApiKey apiKey, int apiName)
            throws LimitExceededException {
        sLogger.v("Checking if API %s is throttled for package %s ", apiKey, callerPackageName);
        boolean isThrottled = !mThrottler.tryAcquire(apiKey, callerPackageName);

        if (isThrottled) {
            sLogger.e(
                    "API rate limit reached for API %s from calling package %s",
                    apiKey, callerPackageName);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, callerPackageName, AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, 0);
            throw new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE);
        }
    }
}
