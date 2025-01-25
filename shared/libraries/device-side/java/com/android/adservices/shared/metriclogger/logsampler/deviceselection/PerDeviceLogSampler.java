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

import static com.android.adservices.shared.metriclogger.AbstractMetricLogger.TAG;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.DeviceSelectionLogic.computePeriodInfo;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.DeviceSelectionLogic.getHasher;

import android.annotation.Nullable;
import android.content.Context;
import android.util.Log;

import com.android.adservices.shared.metriclogger.logsampler.LogSampler;
import com.android.adservices.shared.proto.MetricId;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.hash.Hasher;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public final class PerDeviceLogSampler<L> implements LogSampler<L> {

    private static final String DEVICE_SAMPLER = "PerDeviceSampler";

    private final MetricId mMetricId;
    private final @Nullable PerDeviceSamplingConfig mConfig;
    private final UniqueDeviceIdHelper mUniqueDeviceIdHelper;
    private final Clock mClock;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    public boolean mShouldSelectDevice;

    // Store the end time of the current staggering period. This ensures that device selection
    // logging results are computed only once per period, after the period has ended.
    @GuardedBy("mLock")
    private Instant mCurrentStaggerPeriodEndTime;

    public PerDeviceLogSampler(
            Context context,
            MetricId metricId,
            @Nullable PerDeviceSamplingConfig config,
            Executor backgroundExecutor,
            Executor lightweightExecutor) {
        this(
                metricId,
                config,
                new UniqueDeviceIdHelper(context, backgroundExecutor, lightweightExecutor),
                Clock.getInstance());
    }

    @VisibleForTesting
    PerDeviceLogSampler(
            MetricId metricId,
            @Nullable PerDeviceSamplingConfig config,
            UniqueDeviceIdHelper uniqueDeviceIdHelper,
            Clock clock) {
        mMetricId = metricId;
        mConfig = config;
        mUniqueDeviceIdHelper = uniqueDeviceIdHelper;
        mClock = clock;
    }

    private long getDeviceId() {
        long deviceId = 0;
        try {
            return mUniqueDeviceIdHelper.getDeviceId().get();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error getting id for device selection");
        }
        return deviceId;
    }

    @Override
    public boolean shouldLog() {
        if (mConfig == null) {
            Log.v(
                    TAG,
                    String.format(
                            "%s %s: Per-device sampling config is missing, always log",
                            mMetricId.name(), DEVICE_SAMPLER));
            return true;
        }

        if (mConfig.getSamplingRate() == 1.0) {
            Log.v(
                    TAG,
                    String.format(
                            "%s %s: Sampling rate is 1, always log",
                            mMetricId.name(), DEVICE_SAMPLER));
            return true;
        }

        if (mConfig.getSamplingRate() == 0) {
            Log.v(
                    TAG,
                    String.format(
                            "%s %s: Sampling rate is 0, do not log",
                            mMetricId.name(), DEVICE_SAMPLER));
            return false;
        }

        return shouldLog(Instant.ofEpochMilli(mClock.currentTimeMillis()));
    }

    private boolean shouldLog(Instant eventTime) {
        synchronized (mLock) {
            // Use the stored sampling decision if the logging decision is already computed and
            // is before the staggering end time.
            if (mCurrentStaggerPeriodEndTime != null
                    && eventTime.isBefore(mCurrentStaggerPeriodEndTime)) {
                if (mShouldSelectDevice) {
                    Log.v(
                            TAG,
                            String.format(
                                    "%s %s: Cached sampling decision is positive, accepting event.",
                                    mMetricId.name(), DEVICE_SAMPLER));
                } else {
                    Log.v(
                            TAG,
                            String.format(
                                    "%s %s: Cached sampling decision is negative, rejecting event.",
                                    mMetricId.name(), DEVICE_SAMPLER));
                }
                return mShouldSelectDevice;
            }

            // Compute the sampling decision
            Log.v(
                    TAG,
                    String.format(
                            "%s %s: Computing sampling decision",
                            mMetricId.name(), DEVICE_SAMPLER));

            long selectionId = getDeviceId();
            DeviceSelectionLogic.PeriodInfo periodInfo =
                    computePeriodInfo(
                            eventTime,
                            selectionId,
                            mConfig.getStaggeringPeriod(),
                            mConfig.getRotationPeriod());
            mCurrentStaggerPeriodEndTime = periodInfo.getStaggerPeriodEndTime();
            long periodNumber = periodInfo.getPeriodNumber();

            long hashForDeviceMetric = getHash(selectionId, periodNumber, mConfig.getGroupName());

            long hashWindow = (long) (Long.MAX_VALUE * mConfig.getSamplingRate());

            // The device is chosen for logging if it falls within the hash window range.
            mShouldSelectDevice = Math.abs(hashForDeviceMetric) < hashWindow;

            Log.v(
                    TAG,
                    String.format(
                            "%s %s: Computed device selection decision for device: %d periodNumber:"
                                    + " %d (whether this device will log for this metric): %b",
                            mMetricId.name(),
                            DEVICE_SAMPLER,
                            selectionId,
                            periodNumber,
                            mShouldSelectDevice));
            return mShouldSelectDevice;
        }
    }

    private long getHash(long selectionId, long periodNumber, String groupName) {
        Hasher hasher = getHasher();
        return hasher.putLong(selectionId)
                .putLong(periodNumber)
                .putString(groupName, Charset.defaultCharset())
                .hash()
                .asLong();
    }
}
