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

import static org.mockito.Mockito.when;

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.metriclogger.logsampler.LogSampler;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;
import com.android.adservices.shared.util.Clock;

import com.google.common.util.concurrent.Futures;

import org.junit.Test;
import org.mockito.Mock;

public final class PerDeviceLogSamplerTest extends SharedMockitoTestCase {
    private static final long SECONDS_IN_A_DAY = 86400;
    private static final String EXAMPLE_GROUP_NAME = "example";
    private static final LogSamplingConfig.PerDeviceSampling SAMPLING_CONFIG_PROTO =
            LogSamplingConfig.PerDeviceSampling.newBuilder()
                    .setSamplingRate(0.05)
                    .setRotationPeriodDays(7)
                    .setStaggeringPeriodDays(1)
                    .setGroupName(EXAMPLE_GROUP_NAME)
                    .build();

    private @Mock UniqueDeviceIdHelper mMockDeviceId;
    private @Mock Clock mClock;

    @Test
    public void testShouldLog_configIsNull_alwaysLog() {
        LogSampler<ExampleStats> sampler =
                new PerDeviceLogSampler<>(
                        MetricId.EXAMPLE_STATS, /* config= */ null, mMockDeviceId, mClock);

        expect.withMessage("shouldLog()").that(sampler.shouldLog()).isTrue();
    }

    @Test
    public void testShouldLog_samplingRateOne_alwaysLog() {
        PerDeviceSamplingConfig config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                        LogSamplingConfig.PerDeviceSampling.newBuilder()
                                .setSamplingRate(1.0)
                                .build());
        LogSampler<ExampleStats> sampler =
                new PerDeviceLogSampler<>(MetricId.EXAMPLE_STATS, config, mMockDeviceId, mClock);

        expect.withMessage("shouldLog()").that(sampler.shouldLog()).isTrue();
    }

    @Test
    public void testShouldLog_samplingRateZero_doNotLog() {
        PerDeviceSamplingConfig config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                        LogSamplingConfig.PerDeviceSampling.newBuilder()
                                .setSamplingRate(0)
                                .build());
        LogSampler<ExampleStats> sampler =
                new PerDeviceLogSampler<>(MetricId.EXAMPLE_STATS, config, mMockDeviceId, mClock);

        expect.withMessage("shouldLog()").that(sampler.shouldLog()).isFalse();
    }

    @Test
    public void testShouldLog_sameStaggerGroup_differentDeviceId_someDeviceLogs() {
        mockCurrentTimeSeconds(0);
        // The devices present in deviceIdsLogs and deviceIdsDoesNotLog belong to the stagger group
        // but only some are picked for logging at a given point of time.

        // All the devices belong to stagger group 1. 15 % 7 == 127 % 7 == 218 % 7 == 1
        long[] deviceIdsLogs = new long[] {15, 127, 218};

        for (long deviceId : deviceIdsLogs) {
            LogSampler<ExampleStats> sampler = getDeviceLogSampler(deviceId, SAMPLING_CONFIG_PROTO);
            expect.withMessage("deviceId=" + deviceId).that(sampler.shouldLog()).isTrue();
        }

        // All the devices belong to stagger group 1. 1 % 7 == 8 % 7 == 22 % 7 == 1
        long[] deviceIdsDoesNotLog = new long[] {1, 8, 22};

        for (long deviceId : deviceIdsDoesNotLog) {
            LogSampler<ExampleStats> sampler = getDeviceLogSampler(deviceId, SAMPLING_CONFIG_PROTO);
            expect.withMessage("deviceId=" + deviceId).that(sampler.shouldLog()).isFalse();
        }
    }

    @Test
    public void testShouldLog_sameStaggerGroup_eventTimeChanges() {

        final long eventTimeDays1 = 10L;
        long deviceId = 8;
        mockCurrentTimeSeconds(eventTimeDays1 * SECONDS_IN_A_DAY);
        LogSampler<ExampleStats> sampler = getDeviceLogSampler(deviceId, SAMPLING_CONFIG_PROTO);
        expect.withMessage("deviceId=" + deviceId).that(sampler.shouldLog()).isFalse();

        // The devices 8 is picked when eventTime occurred is at 356 days.
        final long eventTimeDays2 = 356L;
        mockCurrentTimeSeconds(eventTimeDays2 * SECONDS_IN_A_DAY);
        sampler = getDeviceLogSampler(deviceId, SAMPLING_CONFIG_PROTO);
        expect.withMessage("deviceId=" + deviceId).that(sampler.shouldLog()).isTrue();
    }

    @Test
    public void testShouldLog_sameStaggerGroup_sameEventTime_differentGroup() {
        // "example" group name picked
        final long eventTimeDays2 = 356L;
        long deviceId = 8;
        mockCurrentTimeSeconds(eventTimeDays2 * SECONDS_IN_A_DAY);
        LogSampler<ExampleStats> sampler = getDeviceLogSampler(deviceId, SAMPLING_CONFIG_PROTO);
        expect.withMessage("deviceId=" + deviceId).that(sampler.shouldLog()).isTrue();

        // "otherExample" group name is not picked.
        sampler =
                getDeviceLogSampler(
                        deviceId,
                        SAMPLING_CONFIG_PROTO.toBuilder().setGroupName("otherExample").build());
        expect.withMessage("deviceId=" + deviceId).that(sampler.shouldLog()).isFalse();
    }

    @Test
    public void testShouldLog_sameSamplingGroup_differentStaggerGroup_checkPatternFor20Days() {
        // Each character in the pattern string represents whether that particular device should
        // be chosen or not. '0' indicates that the device should be not chosen and '1' indicates
        // that the device should be chosen.
        //
        // For a given device id, this test compares the pattern string with the device selection
        // state for the first 20 days.

        // staggergroup 0
        // The period number for the first 10 days remains the same, so the hash for the device
        // remains the same and there is no change in the device selection state for those 10 days.
        //
        // Since the sampling rate is 0.1, approximately 10% of the devices are selected for logging
        // in  any given period.
        assertSelection20DayPatternIsTrue(0, "00000000000000000000");
        assertSelection20DayPatternIsTrue(10, "00000000000000000000");
        assertSelection20DayPatternIsTrue(20, "00000000000000000000");
        assertSelection20DayPatternIsTrue(30, "00000000000000000000");
        assertSelection20DayPatternIsTrue(40, "00000000000000000000");
        assertSelection20DayPatternIsTrue(50, "00000000000000000000");
        assertSelection20DayPatternIsTrue(60, "00000000000000000000");
        assertSelection20DayPatternIsTrue(70, "00000000000000000000");
        assertSelection20DayPatternIsTrue(80, "00000000000000000000");
        assertSelection20DayPatternIsTrue(90, "00000000001111111111");

        // staggergroup 1
        // period number for the first 9 days remains the same.
        assertSelection20DayPatternIsTrue(1, "11111111100000000000");
        assertSelection20DayPatternIsTrue(11, "00000000000000000000");
        assertSelection20DayPatternIsTrue(21, "11111111100000000000");
        assertSelection20DayPatternIsTrue(31, "00000000000000000000");
        assertSelection20DayPatternIsTrue(41, "00000000000000000000");
        assertSelection20DayPatternIsTrue(51, "00000000000000000000");
        assertSelection20DayPatternIsTrue(61, "00000000000000000000");
        assertSelection20DayPatternIsTrue(71, "00000000011111111110");
        assertSelection20DayPatternIsTrue(81, "00000000011111111110");
        assertSelection20DayPatternIsTrue(91, "00000000000000000000");

        // staggergroup 2
        // period number for the first 8 days remains the same.
        assertSelection20DayPatternIsTrue(2, "00000000000000000000");
        assertSelection20DayPatternIsTrue(12, "00000000000000000011");
        assertSelection20DayPatternIsTrue(22, "00000000000000000000");
        assertSelection20DayPatternIsTrue(32, "00000000000000000011");
        assertSelection20DayPatternIsTrue(42, "00000000000000000000");
        assertSelection20DayPatternIsTrue(52, "00000000000000000000");
        assertSelection20DayPatternIsTrue(62, "00000000000000000000");
        assertSelection20DayPatternIsTrue(72, "00000000000000000000");
        assertSelection20DayPatternIsTrue(82, "00000000000000000000");
        assertSelection20DayPatternIsTrue(92, "00000000000000000000");

        // staggergroup 3
        // period number for the first 7 days remains the same.
        assertSelection20DayPatternIsTrue(3, "00000000000000000000");
        assertSelection20DayPatternIsTrue(13, "00000000000000000000");
        assertSelection20DayPatternIsTrue(23, "00000001111111111000");
        assertSelection20DayPatternIsTrue(33, "00000001111111111000");
        assertSelection20DayPatternIsTrue(43, "00000000000000000000");
        assertSelection20DayPatternIsTrue(53, "11111110000000000000");
        assertSelection20DayPatternIsTrue(63, "00000000000000000000");
        assertSelection20DayPatternIsTrue(73, "00000000000000000000");
        assertSelection20DayPatternIsTrue(83, "00000000000000000000");
        assertSelection20DayPatternIsTrue(93, "00000000000000000111");

        // staggergroup 4
        // period number for the first 6 days remains the same.
        assertSelection20DayPatternIsTrue(4, "00000000000000000000");
        assertSelection20DayPatternIsTrue(14, "00000011111111111111");
        assertSelection20DayPatternIsTrue(24, "11111100000000000000");
        assertSelection20DayPatternIsTrue(34, "00000000000000000000");
        assertSelection20DayPatternIsTrue(44, "00000000000000000000");
        assertSelection20DayPatternIsTrue(54, "00000000000000000000");
        assertSelection20DayPatternIsTrue(64, "00000000000000000000");
        assertSelection20DayPatternIsTrue(74, "00000000000000000000");
        assertSelection20DayPatternIsTrue(84, "00000000000000000000");
        assertSelection20DayPatternIsTrue(94, "00000000000000000000");

        // staggergroup 5
        // period number for the first 5 days remains the same.
        assertSelection20DayPatternIsTrue(5, "00000000000000000000");
        assertSelection20DayPatternIsTrue(15, "11111000000000000000");
        assertSelection20DayPatternIsTrue(25, "00000000000000000000");
        assertSelection20DayPatternIsTrue(35, "00000000000000000000");
        assertSelection20DayPatternIsTrue(45, "00000000000000000000");
        assertSelection20DayPatternIsTrue(55, "00000000000000000000");
        assertSelection20DayPatternIsTrue(65, "00000000000000000000");
        assertSelection20DayPatternIsTrue(75, "00000000000000000000");
        assertSelection20DayPatternIsTrue(85, "00000000000000000000");
        assertSelection20DayPatternIsTrue(95, "00000000000000000000");

        // staggergroup 6
        // period number for the first 4 days remains the same.
        assertSelection20DayPatternIsTrue(6, "00000000000000000000");
        assertSelection20DayPatternIsTrue(16, "00000000000000000000");
        assertSelection20DayPatternIsTrue(26, "00000000000000111111");
        assertSelection20DayPatternIsTrue(36, "00000000000000000000");
        assertSelection20DayPatternIsTrue(46, "00000000000000000000");
        assertSelection20DayPatternIsTrue(56, "00000000000000000000");
        assertSelection20DayPatternIsTrue(66, "00000000000000111111");
        assertSelection20DayPatternIsTrue(76, "00000000000000111111");
        assertSelection20DayPatternIsTrue(86, "00001111111111000000");
        assertSelection20DayPatternIsTrue(96, "00000000000000000000");

        // staggergroup 7
        // period number for the first 3 days remains the same.
        assertSelection20DayPatternIsTrue(7, "00000000000000000000");
        assertSelection20DayPatternIsTrue(17, "00000000000000000000");
        assertSelection20DayPatternIsTrue(27, "00000000000000000000");
        assertSelection20DayPatternIsTrue(37, "00000000000001111111");
        assertSelection20DayPatternIsTrue(47, "00000000000000000000");
        assertSelection20DayPatternIsTrue(57, "00000000000000000000");
        assertSelection20DayPatternIsTrue(67, "00000000000000000000");
        assertSelection20DayPatternIsTrue(77, "00000000000000000000");
        assertSelection20DayPatternIsTrue(87, "11100000000001111111");
        assertSelection20DayPatternIsTrue(97, "00000000000000000000");

        // staggergroup 8
        // period number for the first 2 days remains the same.
        assertSelection20DayPatternIsTrue(8, "00000000000000000000");
        assertSelection20DayPatternIsTrue(18, "00000000000000000000");
        assertSelection20DayPatternIsTrue(28, "00000000000000000000");
        assertSelection20DayPatternIsTrue(38, "00000000000000000000");
        assertSelection20DayPatternIsTrue(48, "00000000000000000000");
        assertSelection20DayPatternIsTrue(58, "00000000000000000000");
        assertSelection20DayPatternIsTrue(68, "00111111111100000000");
        assertSelection20DayPatternIsTrue(78, "00000000000000000000");
        assertSelection20DayPatternIsTrue(88, "00000000000000000000");
        assertSelection20DayPatternIsTrue(98, "00000000000000000000");

        // staggergroup 9
        // period number for the first 1 day remains the same.
        assertSelection20DayPatternIsTrue(9, "00000000000000000000");
        assertSelection20DayPatternIsTrue(19, "00000000000000000000");
        assertSelection20DayPatternIsTrue(29, "00000000000000000000");
        assertSelection20DayPatternIsTrue(39, "10000000000000000000");
        assertSelection20DayPatternIsTrue(49, "00000000000000000000");
        assertSelection20DayPatternIsTrue(59, "00000000000111111111");
        assertSelection20DayPatternIsTrue(69, "00000000000000000000");
        assertSelection20DayPatternIsTrue(79, "00000000000000000000");
        assertSelection20DayPatternIsTrue(89, "00000000000000000000");
        assertSelection20DayPatternIsTrue(99, "00000000000000000000");
    }

    private void setMockDeviceId(long deviceId) {
        when(mMockDeviceId.getDeviceId()).thenReturn(Futures.immediateFuture(deviceId));
    }

    private static final class ExampleStats {
        private ExampleStats() {}
    }

    private <L> LogSampler<L> getDeviceLogSampler(
            long deviceId, LogSamplingConfig.PerDeviceSampling samplingConfigProto) {
        setMockDeviceId(deviceId);
        PerDeviceSamplingConfig config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(samplingConfigProto);
        return new PerDeviceLogSampler<>(MetricId.EXAMPLE_STATS, config, mMockDeviceId, mClock);
    }

    private String getDeviceSelectionPattern(
            LogSamplingConfig.PerDeviceSampling samplingConfigProto, long deviceId, int totalDays) {
        StringBuilder patternBuilder = new StringBuilder();
        for (int days = 0; days < totalDays; days++) {
            int finalDays = days;
            mockCurrentTimeSeconds(finalDays * SECONDS_IN_A_DAY);
            LogSampler<ExampleStats> sampler = getDeviceLogSampler(deviceId, samplingConfigProto);
            if (sampler.shouldLog()) {
                patternBuilder.append("1");
            } else {
                patternBuilder.append("0");
            }
        }
        return patternBuilder.toString();
    }

    private void assertSelection20DayPatternIsTrue(long deviceId, String pattern) {
        LogSamplingConfig.PerDeviceSampling samplingConfigProto =
                LogSamplingConfig.PerDeviceSampling.newBuilder()
                        .setSamplingRate(0.1F)
                        .setRotationPeriodDays(10)
                        .setStaggeringPeriodDays(1)
                        .setGroupName(EXAMPLE_GROUP_NAME)
                        .build();
        expect.withMessage("deviceId=" + deviceId)
                .that(getDeviceSelectionPattern(samplingConfigProto, deviceId, 20))
                .isEqualTo(pattern);
    }

    private void mockCurrentTimeSeconds(long curTimeSeconds) {
        when(mClock.currentTimeMillis()).thenReturn(curTimeSeconds * 1000);
    }
}
