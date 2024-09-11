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

import com.google.auto.value.AutoValue;

/** Class for logging topics epoch job setting during scheduling EpochJobService. */
@AutoValue
public abstract class TopicsScheduleEpochJobSettingReportedStats {
    /** Returns the status when forcing reschedule EpochJob. */
    @AdsRelevanceStatusUtils.TopicsRescheduleEpochJobStatus
    public abstract int getRescheduleEpochJobStatus();

    /** Returns the previous epoch job setting. This field will be UNKNOWN_SETTING
     * when reschedule EpochJob status is not RESCHEDULE_SUCCESS. */
    @AdsRelevanceStatusUtils.TopicsEpochJobBatteryConstraint
    public abstract int getPreviousEpochJobSetting();

    /** Returns the current epoch job setting. This field will be UNKNOWN_SETTING
     * when reschedule EpochJob status is not RESCHEDULE_SUCCESS. */
    @AdsRelevanceStatusUtils.TopicsEpochJobBatteryConstraint
    public abstract int getCurrentEpochJobSetting();

    /** Returns the epoch job setting when scheduling the epoch job in
     * {@code EpochJobService.scheduleIfNeeded()} */
    @AdsRelevanceStatusUtils.TopicsEpochJobBatteryConstraint
    public abstract int getScheduleIfNeededEpochJobStatus();

    /** Returns generic builder. */
    public static Builder builder() {
        return new AutoValue_TopicsScheduleEpochJobSettingReportedStats.Builder();
    }

    /** Builder class for TopicsScheduleEpochJobSettingReportedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setRescheduleEpochJobStatus(
                @AdsRelevanceStatusUtils.TopicsRescheduleEpochJobStatus int value);

        public abstract Builder setPreviousEpochJobSetting(
                @AdsRelevanceStatusUtils.TopicsEpochJobBatteryConstraint int value);

        public abstract Builder setCurrentEpochJobSetting(
                @AdsRelevanceStatusUtils.TopicsEpochJobBatteryConstraint int value);

        public abstract Builder setScheduleIfNeededEpochJobStatus(
                @AdsRelevanceStatusUtils.TopicsEpochJobBatteryConstraint int value);

        public abstract TopicsScheduleEpochJobSettingReportedStats build();
    }
}
