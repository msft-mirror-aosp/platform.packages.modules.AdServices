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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public class ScheduledCustomAudienceUpdatePerformedStatsTest {

    @Test
    public void testBuilder() {
        ScheduledCustomAudienceUpdatePerformedStats stats =
                ScheduledCustomAudienceUpdatePerformedStats.builder()
                        .setNumberOfPartialCustomAudienceInRequest(1)
                        .setNumberOfLeaveCustomAudienceInRequest(2)
                        .setNumberOfJoinCustomAudienceInResponse(3)
                        .setNumberOfLeaveCustomAudienceInResponse(4)
                        .setNumberOfCustomAudienceJoined(5)
                        .setNumberOfCustomAudienceLeft(6)
                        .setWasInitialHop(true)
                        .setNumberOfScheduleUpdatesInResponse(7)
                        .setNumberOfUpdatesScheduled(8)
                        .build();

        assertWithMessage("Number of partial custom audience in request")
                .that(stats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(1);
        assertWithMessage("Number of leave custom audience in request")
                .that(stats.getNumberOfLeaveCustomAudienceInRequest())
                .isEqualTo(2);
        assertWithMessage("Number of join custom audience in response")
                .that(stats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(3);
        assertWithMessage("Number of leave custom audience in response")
                .that(stats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(4);
        assertWithMessage("Number of custom audience joined")
                .that(stats.getNumberOfCustomAudienceJoined())
                .isEqualTo(5);
        assertWithMessage("Number of custom audience left")
                .that(stats.getNumberOfCustomAudienceLeft())
                .isEqualTo(6);
        assertWithMessage("Was initial hop ").that(stats.getWasInitialHop()).isTrue();
        assertWithMessage("Number of schedule updates in response")
                .that(stats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(7);
        assertWithMessage("Number of updates scheduled")
                .that(stats.getNumberOfUpdatesScheduled())
                .isEqualTo(8);
    }

    @Test
    public void testEquals() {
        ScheduledCustomAudienceUpdatePerformedStats stats1 =
                ScheduledCustomAudienceUpdatePerformedStats.builder()
                        .setNumberOfPartialCustomAudienceInRequest(1)
                        .setNumberOfLeaveCustomAudienceInRequest(2)
                        .setNumberOfJoinCustomAudienceInResponse(3)
                        .setNumberOfLeaveCustomAudienceInResponse(4)
                        .setNumberOfCustomAudienceJoined(5)
                        .setNumberOfCustomAudienceLeft(6)
                        .setWasInitialHop(true)
                        .setNumberOfScheduleUpdatesInResponse(7)
                        .setNumberOfUpdatesScheduled(8)
                        .build();
        ScheduledCustomAudienceUpdatePerformedStats stats2 =
                ScheduledCustomAudienceUpdatePerformedStats.builder()
                        .setNumberOfPartialCustomAudienceInRequest(1)
                        .setNumberOfLeaveCustomAudienceInRequest(2)
                        .setNumberOfJoinCustomAudienceInResponse(3)
                        .setNumberOfLeaveCustomAudienceInResponse(4)
                        .setNumberOfCustomAudienceJoined(5)
                        .setNumberOfCustomAudienceLeft(6)
                        .setWasInitialHop(true)
                        .setNumberOfScheduleUpdatesInResponse(7)
                        .setNumberOfUpdatesScheduled(8)
                        .build();

        assertThat(stats1).isEqualTo(stats2);
    }
}
