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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.time.Duration;
import java.time.Instant;

/** Static methods with sampling logic. */
public final class DeviceSelectionLogic {
    private DeviceSelectionLogic() {}

    public static Hasher getHasher() {
        // Using Murmur3_128 as it  is a deterministic hashing algorithm and returns a 128-bit hash
        // value.
        return Hashing.murmur3_128().newHasher();
    }

    /**
     * Compute the period information for a given instant in time.
     *
     * <p>Sampling where only a percentage of the devices or users log at a given time. The
     * devices/users that log are rotated with a certain periodicity (configured via a rotation
     * period). That rotation can happen at the same time for all of them, or can be *staggered*,
     * which means we divide them into groups and each group rotates at a different time. For that a
     * staggering period can be specified; it means the time difference between staggered rotations.
     * For example, if we have rotation period 7 days, and staggering period 1 day, we will divide
     * all the users in 7 equal groups, and each group will rotate on a different day of the week.
     *
     * <p>In order to achieve all this, we divide the time into rotation periods, and give them a
     * number (starting at 0 a the beginning of the UNIX time). Then we hash together the
     * device/user id and the period number to make a pseudo-random, deterministic sampling decision
     * for that device/user and that period. Since the device/user id never changes, and the period
     * number doesn't change for the length of the period, we can, at any time, figure out what is
     * the period number in which we are, and produce a consistent sampling decision that will
     * respect the configured constraints (sampling rate, rotation).
     *
     * <p>This method, given an instant in time, computes the period number that instant belongs to,
     * and the time at which that period will finish (which can be used to cache sampling decisions,
     * as the period number will not change until that time). The beginning of the period can be
     * inferred by subtracting the rotation period length from the end of the period.
     *
     * <p>Note that which period an instant belongs may not be the same for all devices, as, due to
     * staggerings, they may have different rotation schedules.
     *
     * @param eventTime the time for which we want to compute the period
     * @param selectionId a number representing the device or user; it shouldn't change over time
     * @param staggeringPeriod the length for a rotation period, e.g. 30 days
     * @param rotationPeriod the length between staggered rotations, typically 1 day. to disable
     *     staggering, the staggering period should be equals to the rotation period.
     */
    public static PeriodInfo computePeriodInfo(
            Instant eventTime,
            long selectionId,
            Duration staggeringPeriod,
            Duration rotationPeriod) {
        checkArgument(!staggeringPeriod.isZero(), "staggeringPeriodSeconds should not be zero");
        checkArgument(!rotationPeriod.isZero(), "rotationPeriodSeconds should not be zero");
        checkArgument(
                rotationPeriod.compareTo(staggeringPeriod) >= 0,
                "rotationPeriod should be greater than stagger period");

        Duration shiftPeriod = getShiftPeriod(selectionId, staggeringPeriod, rotationPeriod);

        Instant currentStaggerPeriodEndTime =
                getCurrentStaggerPeriodEndTime(eventTime, rotationPeriod, shiftPeriod);

        // During each staggerPeriod the periodNumber for one of the stagger groups changes, making
        // some of the devices in the staggerGroup finish logging and some of them start logging.
        long periodNumber = getPeriodNumber(eventTime.plus(shiftPeriod), rotationPeriod);
        return PeriodInfo.create(periodNumber, currentStaggerPeriodEndTime);
    }

    private static Instant getCurrentStaggerPeriodEndTime(
            Instant eventTime, Duration rotationPeriod, Duration shiftPeriod) {
        // Regular period start, plus the shifts corresponding to this group.
        Instant currentStaggerPeriodStartTime =
                Instant.ofEpochMilli(
                        rotationPeriod
                                .multipliedBy(getPeriodNumber(eventTime, rotationPeriod))
                                .plus(shiftPeriod)
                                .toMillis());

        // The shift may push us beyond the present time, in that case go back to the previous
        // period.
        if (currentStaggerPeriodStartTime.isAfter(eventTime)) {
            currentStaggerPeriodStartTime = currentStaggerPeriodStartTime.minus(rotationPeriod);
        }

        return currentStaggerPeriodStartTime.plus(rotationPeriod);
    }

    private static Duration getShiftPeriod(
            long selectionId, Duration staggeringPeriod, Duration rotationPeriod) {
        // staggerFrequency denotes the number of times staggering needs to be done within a given
        // sampling period. If staggering is not enabled, it will be 1.
        int staggerFrequency = (int) (rotationPeriod.toMillis() / staggeringPeriod.toMillis());

        // Partition all devices into stagger groups. Stagger groups can range from 0 to
        // staggerFrequency-1. When staggering is not enabled, the stagger group is always 0.
        int staggerGroup = (int) (selectionId % staggerFrequency);

        // The period shifted to different degree for each staggerGroup.
        // Shift is zero when staggering is disabled.
        staggerGroup = Math.abs(staggerGroup);
        return staggeringPeriod.multipliedBy(staggerGroup);
    }

    /**
     * Compute the number of periods that has passed since the beginning of the UNIX time to given
     * epoch time.
     *
     * <p>The period number starts at 0 for the earliest period (at the beginning of the UNIX time)
     * and goes up from there.
     *
     * @param epochTime the time for which we want to compute the period number
     * @param periodDuration the length of the period
     */
    private static long getPeriodNumber(Instant epochTime, Duration periodDuration) {
        return epochTime.toEpochMilli() / periodDuration.toMillis();
    }

    /** Value class with the result of computing a sampling period. */
    @AutoValue
    public abstract static class PeriodInfo {
        /**
         * Return a number that identifies which period the object belongs to.
         *
         * <p>The period number starts at 0 for the earliest period (at the beginning of the UNIX
         * time) and goes up from there. The length of the period is based on the rotation period.
         */
        public abstract long getPeriodNumber();

        /**
         * Return the UNIX time in which the period represented by this object finishes.
         *
         * <p>Note that the period start can be inferred if you know the rotation period length.
         */
        public abstract Instant getStaggerPeriodEndTime();

        /** Creates an instance of {@link PeriodInfo}. */
        public static PeriodInfo create(long periodNumber, Instant staggerPeriodEndTime) {
            return new AutoValue_DeviceSelectionLogic_PeriodInfo(
                    periodNumber, staggerPeriodEndTime);
        }
    }
}
