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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ScheduledCustomAudienceUpdatePerformedFailureStatsTest {

    @Test
    public void testbuilder() {
        ScheduledCustomAudienceUpdatePerformedFailureStats failure =
                ScheduledCustomAudienceUpdatePerformedFailureStats.builder()
                        .setFailureAction(
                                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE)
                        .setFailureType(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR)
                        .build();
        assertThat(failure.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE);
        assertThat(failure.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR);
    }

    @Test
    public void testEquals() {
        ScheduledCustomAudienceUpdatePerformedFailureStats failure1 =
                ScheduledCustomAudienceUpdatePerformedFailureStats.builder()
                        .setFailureAction(
                                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE)
                        .setFailureType(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR)
                        .build();
        ScheduledCustomAudienceUpdatePerformedFailureStats failure2 =
                ScheduledCustomAudienceUpdatePerformedFailureStats.builder()
                        .setFailureAction(
                                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE)
                        .setFailureType(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR)
                        .build();
        assertThat(failure1).isEqualTo(failure2);
    }
}
