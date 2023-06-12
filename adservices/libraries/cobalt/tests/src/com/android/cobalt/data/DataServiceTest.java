/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cobalt.data;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public final class DataServiceTest {
    private static ExecutorService sExecutor = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private static final Instant sTime = Instant.parse("2023-06-13T16:09:30.00Z");

    private CobaltDatabase mCobaltDatabase;
    private DaoBuildingBlocks mDaoBuildingBlocks;
    private DataService mDataService;

    @Before
    public void createDb() {
        mCobaltDatabase = Room.inMemoryDatabaseBuilder(sContext, CobaltDatabase.class).build();
        mDaoBuildingBlocks = mCobaltDatabase.daoBuildingBlocks();
        mDataService = new DataService(sExecutor, mCobaltDatabase);
    }

    @After
    public void closeDb() throws IOException {
        mCobaltDatabase.close();
    }

    private Instant sTimePlusHours(int hours) {
        return sTime.plus(Duration.ofHours(hours));
    }

    private Instant sTimePlusDays(int days) {
        return sTime.plus(Duration.ofDays(days));
    }

    private Instant sTimeMinusDays(int days) {
        return sTime.minus(Duration.ofDays(days));
    }

    @Test
    public void testLoggerEnabled_oneTime_stored() throws Exception {
        assertThat(mDataService.loggerEnabled(sTime).get()).isEqualTo(sTime);
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, sTime.toString());
    }

    @Test
    public void loggerEnabled_multipleTimes_firstIsStored() throws Exception {
        // Set the logger as enabled, and again an hour later.
        mDataService.loggerEnabled(sTime).get();
        assertThat(mDataService.loggerEnabled(sTimePlusHours(1)).get()).isEqualTo(sTime);

        // Check that the original initial enabled time is returned and set in the database
        assertThat(mDataService.loggerEnabled(sTime).get()).isEqualTo(sTime);
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, sTime.toString());
    }

    @Test
    public void loggerDisabled_notYetEnabled_stored() throws Exception {
        // Set the logger as disabled.
        mDataService.loggerDisabled(sTime).get();

        // Check that the disabled time is set in the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_DISABLED_TIME, sTime.toString());
    }

    @Test
    public void loggerDisabled_afterEnabled_stored() throws Exception {
        // Set the logger as enabled.
        Instant enabledTime = sTime.minus(Duration.ofDays(1));
        mDataService.loggerEnabled(enabledTime).get();

        // Set the logger as disabled.
        mDataService.loggerDisabled(sTime).get();

        // Check that the disabled time is set in the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME, enabledTime.toString(),
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME, sTime.toString());
    }

    @Test
    public void loggerDisabled_multipleTimesAfterEnabled_stored() throws Exception {
        // Set the logger as enabled.
        Instant enabledTime = sTimeMinusDays(1);
        mDataService.loggerEnabled(enabledTime).get();

        // Set the logger as disabled, and again an hour later.
        mDataService.loggerDisabled(sTime).get();
        mDataService.loggerDisabled(sTimePlusHours(1)).get();

        // Check that the disabled time is set in the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME, enabledTime.toString(),
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME, sTime.toString());
    }

    @Test
    public void loggerEnabled_reenabledShortlyAfterDisabled_originalEnabledTime() throws Exception {
        // Set the logger as enabled.
        mDataService.loggerEnabled(sTime).get();

        // Set the logger as disabled 10 days later.
        mDataService.loggerDisabled(sTimePlusDays(10)).get();

        // Re-enable the logger after a day. Less than 2 days so the original enabled time is kept.
        assertThat(mDataService.loggerEnabled(sTimePlusDays(11)).get()).isEqualTo(sTime);

        // Check that the original initial time is kept and the disabled time is no longer set in
        // the database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(GlobalValueEntity.Key.INITIAL_ENABLED_TIME, sTime.toString());
    }

    @Test
    public void loggerEnabled_reenabledAfterMoreThanTwoDaysDisabled_newEnabledTime()
            throws Exception {
        // Set the logger as enabled.
        mDataService.loggerEnabled(sTime).get();

        // Set the logger as disabled 10 days later.
        mDataService.loggerDisabled(sTimePlusDays(10)).get();

        // Re-enable the logger after 3 days. More than 2 days so the initial enabled time is reset.
        assertThat(mDataService.loggerEnabled(sTimePlusDays(13)).get())
                .isEqualTo(sTimePlusDays(13));

        // Check that the initial time is reset and the disabled time is no longer set in the
        // database.
        assertThat(mDaoBuildingBlocks.queryEnablementTimes())
                .containsExactly(
                        GlobalValueEntity.Key.INITIAL_ENABLED_TIME, sTimePlusDays(13).toString());
    }
}
