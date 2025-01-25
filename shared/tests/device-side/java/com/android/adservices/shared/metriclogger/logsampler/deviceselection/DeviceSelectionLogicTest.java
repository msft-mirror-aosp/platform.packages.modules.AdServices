/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.shared.metriclogger.logsampler.deviceselection;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.metriclogger.logsampler.deviceselection.DeviceSelectionLogic.PeriodInfo;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public final class DeviceSelectionLogicTest extends SharedUnitTestCase {
    private static final Instant EVENT_TIME_INSTANT = Instant.ofEpochSecond(1L);
    private static final long SELECTION_ID_ONE = 1;
    private static final long SELECTION_ID_TWO = 2;

    @Test
    public void testComputePeriodInfo_zeroRotationPeriod_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DeviceSelectionLogic.computePeriodInfo(
                                EVENT_TIME_INSTANT,
                                SELECTION_ID_ONE,
                                /* staggeringPeriod= */ Duration.ofSeconds(1L),
                                /* rotationPeriod= */ Duration.ZERO));
    }

    @Test
    public void testComputePeriodInfo_zeroStaggeringPeriod_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DeviceSelectionLogic.computePeriodInfo(
                                EVENT_TIME_INSTANT,
                                SELECTION_ID_ONE,
                                /* staggeringPeriod= */ Duration.ZERO,
                                /* rotationPeriod= */ Duration.ofSeconds(1L)));
    }

    @Test
    public void testComputePeriodInfo_staggeringPeriodGreaterThanRotationPeriod_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DeviceSelectionLogic.computePeriodInfo(
                                EVENT_TIME_INSTANT,
                                SELECTION_ID_ONE,
                                /* staggeringPeriod= */ Duration.ofSeconds(100),
                                /* rotationPeriod= */ Duration.ofSeconds(50)));
    }

    @Test
    public void testComputePeriodInfo_disableStaggering() {
        Instant eventTime = Instant.ofEpochSecond(101);
        Duration period = Duration.ofSeconds(100);
        PeriodInfo actual =
                DeviceSelectionLogic.computePeriodInfo(eventTime, SELECTION_ID_ONE, period, period);

        expect.that(actual)
                .isEqualTo(PeriodInfo.create(/* periodNumber= */ 1, Instant.ofEpochSecond(200)));

        PeriodInfo actual2 =
                DeviceSelectionLogic.computePeriodInfo(eventTime, SELECTION_ID_TWO, period, period);

        expect.that(actual2)
                .isEqualTo(PeriodInfo.create(/* periodNumber= */ 1, Instant.ofEpochSecond(200)));
    }

    @Test
    public void testComputePeriodInfo_enableStaggering() {
        Instant eventTime = Instant.ofEpochSecond(150);
        Duration rotationPeriod = Duration.ofSeconds(100);
        Duration staggerPeriod = Duration.ofSeconds(10);

        PeriodInfo actual =
                DeviceSelectionLogic.computePeriodInfo(
                        eventTime, SELECTION_ID_ONE, staggerPeriod, rotationPeriod);
        expect.that(actual)
                .isEqualTo(PeriodInfo.create(/* periodNumber= */ 1, Instant.ofEpochSecond(210)));

        PeriodInfo actual2 =
                DeviceSelectionLogic.computePeriodInfo(
                        eventTime, SELECTION_ID_TWO, staggerPeriod, rotationPeriod);

        expect.that(actual2)
                .isEqualTo(PeriodInfo.create(/* periodNumber= */ 1, Instant.ofEpochSecond(220)));
    }

    @Test
    public void testComputePeriodInfo_enableStaggering_previousEpoch() {
        Instant eventTime = Instant.ofEpochSecond(101);
        Duration rotationPeriod = Duration.ofSeconds(100);
        Duration staggerPeriod = Duration.ofSeconds(10);

        PeriodInfo actual =
                DeviceSelectionLogic.computePeriodInfo(
                        eventTime, SELECTION_ID_ONE, staggerPeriod, rotationPeriod);
        expect.that(actual)
                .isEqualTo(PeriodInfo.create(/* periodNumber= */ 1, Instant.ofEpochSecond(110)));

        PeriodInfo actual2 =
                DeviceSelectionLogic.computePeriodInfo(
                        eventTime, SELECTION_ID_TWO, staggerPeriod, rotationPeriod);

        expect.that(actual2)
                .isEqualTo(PeriodInfo.create(/* periodNumber= */ 1, Instant.ofEpochSecond(120)));
    }
}
