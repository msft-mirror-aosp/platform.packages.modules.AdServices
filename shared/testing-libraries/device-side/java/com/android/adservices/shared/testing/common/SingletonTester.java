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

package com.android.adservices.shared.testing.common;

import android.util.Log;

import com.google.common.truth.Expect;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Helper used to test factory methods that generate a singleton. */
public final class SingletonTester {

    private static final String TAG = SingletonTester.class.getSimpleName();

    private static final int DEFAULT_NUMBER_THREADS = 5;
    private static final int DEFAULT_NUMBER_CALLS = 10_000;

    private final int mNumberCalls;
    private final Executor mExecutor;
    private final Expect mExpect;

    /** Default constructor */
    public SingletonTester(Expect expect) {
        this(expect, DEFAULT_NUMBER_THREADS, DEFAULT_NUMBER_CALLS);
    }

    /** Constructor with custom number of threads and calls */
    public SingletonTester(Expect expect, int numberThreads, int numberCalls) {
        this(expect, Executors.newFixedThreadPool(numberThreads), numberCalls);
        Log.d(TAG, "Created newFixedThreadPoolExecutor with " + numberThreads + " threads");
    }

    /** Constructor with custom executor and number of calls */
    public SingletonTester(Expect expect, Executor executor, int numberCalls) {
        mExpect = Objects.requireNonNull(expect, "expect cannot be null");
        mExecutor = Objects.requireNonNull(executor, "executor cannot be null");
        mNumberCalls = numberCalls;
        Log.d(TAG, "SingletonTester: numberThreads=" + numberCalls + ", executor=" + executor);
    }

    /** Asserts that all instances returned by the supplier are the same object. */
    public <T> void assertAllInstancesAreTheSame(Supplier<T> supplier) throws InterruptedException {
        AtomicReference<T> singletonRef = new AtomicReference<T>();
        CountDownLatch latch = new CountDownLatch(mNumberCalls);

        for (int i = 1; i <= mNumberCalls; i++) {
            String callNumber = Integer.toString(i);
            mExecutor.execute(
                    () -> {
                        try {
                            String threadName = Thread.currentThread().getName();
                            T localInstance = supplier.get();
                            mExpect.withMessage("call #%s at thread %s", callNumber, threadName)
                                    .that(localInstance)
                                    .isNotNull();
                            if (singletonRef.compareAndSet(null, localInstance)) {
                                Log.v(
                                        TAG,
                                        "Set singleton at call #"
                                                + callNumber
                                                + " on thread "
                                                + threadName
                                                + ": "
                                                + localInstance);
                                return;
                            }
                            T singleton = singletonRef.get();
                            Log.v(
                                    TAG,
                                    "Call #"
                                            + callNumber
                                            + " at thread "
                                            + threadName
                                            + ": localInstance="
                                            + localInstance
                                            + " singleton="
                                            + singleton);
                            mExpect.withMessage("instance #%s at thread %s", callNumber, threadName)
                                    .that(localInstance)
                                    .isSameInstanceAs(singleton);
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        Log.d(TAG, "Waiting for all " + mNumberCalls + " calls to complete");
        latch.await();
    }
}
