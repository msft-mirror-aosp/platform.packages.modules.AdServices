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

package com.android.adservices.service.measurement.util;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.AGGREGATE_REPORTING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ASYNC_REGISTRATION_PROCESSING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ATTRIBUTION_PROCESSING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.DEBUG_REPORTING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.EVENT_REPORTING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.VERBOSE_DEBUG_REPORTING;

import android.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.LoggerFactory.Logger;

import com.google.common.annotations.VisibleForTesting;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the lock to be used by the background jobs. The locks will be used by multiple jobs,
 * fallback jobs, and similar functions that could perform the same action. However, only one of
 * these functions should be able to process at a time. This is to prevent conflicts and ensure that
 * the system runs smoothly.
 */
public final class JobLockHolder {
    public enum Type {
        AGGREGATE_REPORTING,
        ASYNC_REGISTRATION_PROCESSING,
        ATTRIBUTION_PROCESSING,
        DEBUG_REPORTING,
        EVENT_REPORTING,
        VERBOSE_DEBUG_REPORTING
    }

    private static final Map<Type, JobLockHolder> INSTANCES =
            Map.of(
                    AGGREGATE_REPORTING, new JobLockHolder(AGGREGATE_REPORTING),
                    ASYNC_REGISTRATION_PROCESSING, new JobLockHolder(ASYNC_REGISTRATION_PROCESSING),
                    ATTRIBUTION_PROCESSING, new JobLockHolder(ATTRIBUTION_PROCESSING),
                    DEBUG_REPORTING, new JobLockHolder(DEBUG_REPORTING),
                    EVENT_REPORTING, new JobLockHolder(EVENT_REPORTING),
                    VERBOSE_DEBUG_REPORTING, new JobLockHolder(VERBOSE_DEBUG_REPORTING));

    private final Type mType;

    /* Holds the lock that will be given per instance */
    private final ReentrantLock mLock;

    private JobLockHolder(Type type) {
        this(type, new ReentrantLock());
    }

    @VisibleForTesting
    JobLockHolder(Type type, ReentrantLock lock) {
        mType = type;
        mLock = lock;
    }

    /**
     * Retrieves an instance that has already been created based on its type.
     *
     * @param type of lock to be shared by similar tasks
     * @return lock instance
     */
    public static JobLockHolder getInstance(Type type) {
        return INSTANCES.get(type);
    }

    /**
     * Runs the given runnable after acquiring the lock.
     *
     * @param tag name of the caller (used for logging purposes)
     * @param runnable what to run
     */
    public void runWithLock(String tag, Runnable runnable) {
        Objects.requireNonNull(tag, "tag cannot be null");
        Objects.requireNonNull(runnable, "runnable cannot be null");

        Logger logger = LoggerFactory.getMeasurementLogger();
        logger.v("%s.runWithLock(%s) started", tag, mType);

        if (mLock.tryLock()) {
            try {
                runnable.run();
            } finally {
                mLock.unlock();
            }
            return;
        }

        logger.e("%s.runWithLock(%s) failed to acquire lock", tag, mType);
    }

    /**
     * Calls the given runnable after acquiring the lock.
     *
     * @param tag name of the caller (used for logging purposes)
     * @param callable what to call
     * @param failureResult what to return if log could not be acquired
     * @return result of callable, or {@code failureResult} if the lock could not be acquired.
     */
    public <T> T callWithLock(
            String tag, UncheckedCallable<T> callable, @Nullable T failureResult) {
        Objects.requireNonNull(tag, "tag cannot be null");
        Objects.requireNonNull(callable, "callable cannot be null");

        Logger logger = LoggerFactory.getMeasurementLogger();
        logger.v("%s.callWithLock(%s) started", tag, mType);

        if (mLock.tryLock()) {
            try {
                return callable.call();
            } finally {
                mLock.unlock();
            }
        }

        logger.e(
                "%s.callWithLock(%s) failed to acquire lock; returning %s",
                tag, mType, failureResult);
        return failureResult;
    }

    @Override
    public String toString() {
        return "JobLockHolder[mType=" + mType + ", mLock=" + mLock + "]";
    }

    /**
     * Same as {@link java.util.concurrent.Callable}, but it doesn't throw a checked exception.
     *
     * @param <T> type of returned object
     */
    public interface UncheckedCallable<T> {
        /** See {@link java.util.concurrent.Callable#call()} */
        T call();
    }
}
