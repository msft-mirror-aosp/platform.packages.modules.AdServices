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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public class ScheduledCustomAudienceUpdateScheduleAttemptedStatsTest {

    @Test
    public void testBuilder() {
        ScheduledCustomAudienceUpdateScheduleAttemptedStats stats =
                ScheduledCustomAudienceUpdateScheduleAttemptedStats.builder()
                        .setNumberOfPartialCustomAudiences(1)
                        .setNumberOfLeaveCustomAudiences(2)
                        .setMinimumDelayInMinutes(12345)
                        .setInitialHop(true)
                        .setExistingUpdateStatus(
                                SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE)
                        .build();

        assertWithMessage("Number of partial custom audiences")
                .that(stats.getNumberOfPartialCustomAudiences())
                .isEqualTo(1);
        assertWithMessage("Number of leave custom audiences")
                .that(stats.getNumberOfLeaveCustomAudiences())
                .isEqualTo(2);
        assertWithMessage("Minimum delay in seconds")
                .that(stats.getMinimumDelayInMinutes())
                .isEqualTo(12345);
        assertWithMessage("Is initial hop").that(stats.isInitialHop()).isTrue();
        assertWithMessage("Existing update status")
                .that(stats.getExistingUpdateStatus())
                .isEqualTo(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE);
    }

    @Test
    public void testEquals() {
        ScheduledCustomAudienceUpdateScheduleAttemptedStats stats1 =
                ScheduledCustomAudienceUpdateScheduleAttemptedStats.builder()
                        .setNumberOfPartialCustomAudiences(1)
                        .setNumberOfLeaveCustomAudiences(2)
                        .setMinimumDelayInMinutes(12345)
                        .setInitialHop(true)
                        .setExistingUpdateStatus(
                                SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE)
                        .build();
        ScheduledCustomAudienceUpdateScheduleAttemptedStats stats2 =
                ScheduledCustomAudienceUpdateScheduleAttemptedStats.builder()
                        .setNumberOfPartialCustomAudiences(1)
                        .setNumberOfLeaveCustomAudiences(2)
                        .setMinimumDelayInMinutes(12345)
                        .setInitialHop(true)
                        .setExistingUpdateStatus(
                                SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE)
                        .build();

        assertThat(stats1).isEqualTo(stats2);
    }

    @Test
    public void testBuilder_buildsWithDefaultValues() {
        ScheduledCustomAudienceUpdateScheduleAttemptedStats stats =
                ScheduledCustomAudienceUpdateScheduleAttemptedStats.builder()
                        .setNumberOfPartialCustomAudiences(1)
                        .setMinimumDelayInMinutes(12345)
                        .build();

        assertWithMessage("Is initial hop").that(stats.isInitialHop()).isTrue();
        assertWithMessage("Existing update status")
                .that(stats.getExistingUpdateStatus())
                .isEqualTo(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN);
        assertWithMessage("Number of leave custom audiences")
                .that(stats.getNumberOfLeaveCustomAudiences())
                .isEqualTo(0);
    }
}
