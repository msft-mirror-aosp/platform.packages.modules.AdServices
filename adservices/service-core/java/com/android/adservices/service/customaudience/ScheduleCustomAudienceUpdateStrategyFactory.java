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

package com.android.adservices.service.customaudience;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.data.customaudience.CustomAudienceDao;

import com.google.common.util.concurrent.ListeningExecutorService;

/** Factory for {@link ScheduleCustomAudienceUpdateStrategy}s */
@RequiresApi(Build.VERSION_CODES.S)
public class ScheduleCustomAudienceUpdateStrategyFactory {

    /**
     * Returns the appropriate ScheduleCustomAudienceUpdateStrategy based whether schedule request
     * is enabled
     *
     * @param additionalScheduleRequestsEnabled Should be true if schedule request feature is
     *     enabled.
     * @return An implementation of ScheduleCustomAudienceUpdateStrategy
     */
    public static ScheduleCustomAudienceUpdateStrategy createStrategy(
            CustomAudienceDao customAudienceDao,
            ListeningExecutorService backgroundExecutor,
            ListeningExecutorService lightWeightExecutor,
            int minDelayMinsOverride,
            boolean additionalScheduleRequestsEnabled) {
        if (additionalScheduleRequestsEnabled) {
            return new AdditionalScheduleRequestsEnabledStrategy(
                    customAudienceDao,
                    backgroundExecutor,
                    lightWeightExecutor,
                    new AdditionalScheduleRequestsEnabledStrategyHelper(minDelayMinsOverride));
        }
        return new AdditionalScheduleRequestsDisabledStrategy(customAudienceDao);
    }
}
