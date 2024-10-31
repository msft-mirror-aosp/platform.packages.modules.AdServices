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

public class ScheduledCustomAudienceUpdateBackgroundJobStatsTest {

    @Test
    public void testBuilder() {
        ScheduledCustomAudienceUpdateBackgroundJobStats stats =
                ScheduledCustomAudienceUpdateBackgroundJobStats.builder()
                        .setNumberOfUpdatesFound(1)
                        .setNumberOfSuccessfulUpdates(2)
                        .build();

        assertWithMessage("Number of updates found.")
                .that(stats.getNumberOfUpdatesFound())
                .isEqualTo(1);
        assertWithMessage("Number of successful updates.")
                .that(stats.getNumberOfSuccessfulUpdates())
                .isEqualTo(2);
    }

    @Test
    public void testEquals() {
        ScheduledCustomAudienceUpdateBackgroundJobStats stats1 =
                ScheduledCustomAudienceUpdateBackgroundJobStats.builder()
                        .setNumberOfUpdatesFound(1)
                        .setNumberOfSuccessfulUpdates(2)
                        .build();
        ScheduledCustomAudienceUpdateBackgroundJobStats stats2 =
                ScheduledCustomAudienceUpdateBackgroundJobStats.builder()
                        .setNumberOfUpdatesFound(1)
                        .setNumberOfSuccessfulUpdates(2)
                        .build();

        assertThat(stats1).isEqualTo(stats2);
    }

    @Test
    public void testNotEquals_DifferentNumberOfUpdatesFound() {
        ScheduledCustomAudienceUpdateBackgroundJobStats stats1 =
                ScheduledCustomAudienceUpdateBackgroundJobStats.builder()
                        .setNumberOfUpdatesFound(1)
                        .setNumberOfSuccessfulUpdates(2)
                        .build();
        ScheduledCustomAudienceUpdateBackgroundJobStats stats2 =
                ScheduledCustomAudienceUpdateBackgroundJobStats.builder()
                        .setNumberOfUpdatesFound(3)
                        .setNumberOfSuccessfulUpdates(2)
                        .build();

        assertThat(stats1).isNotEqualTo(stats2);
    }

    @Test
    public void testNotEquals_DifferentNumberOfSuccessfulUpdates() {
        ScheduledCustomAudienceUpdateBackgroundJobStats stats1 =
                ScheduledCustomAudienceUpdateBackgroundJobStats.builder()
                        .setNumberOfUpdatesFound(1)
                        .setNumberOfSuccessfulUpdates(2)
                        .build();
        ScheduledCustomAudienceUpdateBackgroundJobStats stats2 =
                ScheduledCustomAudienceUpdateBackgroundJobStats.builder()
                        .setNumberOfUpdatesFound(1)
                        .setNumberOfSuccessfulUpdates(4)
                        .build();

        assertThat(stats1).isNotEqualTo(stats2);
    }
}
