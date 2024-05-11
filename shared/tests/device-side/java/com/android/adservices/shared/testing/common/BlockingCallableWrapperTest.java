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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;

import com.android.adservices.shared.SharedUnitTestCase;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class BlockingCallableWrapperTest extends SharedUnitTestCase {
    private final ListeningExecutorService mLightWeightExecutor = sLightWeightExecutor;

    @Test
    public void testRunsDelegate() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        assertThat(asyncWork.get(100, TimeUnit.MILLISECONDS)).isEqualTo(1);
    }

    @Test
    public void testMarksTaskAsCompleted() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        asyncWork.get(100, TimeUnit.MILLISECONDS);

        assertThat(toTest.isWorkCompleted()).isTrue();
    }

    @Test
    public void testMarksCompletedIsFalseUntilWorkIsDone() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(() -> 1);

        assertThat(toTest.isWorkCompleted()).isFalse();

        toTest.call();

        assertThat(toTest.isWorkCompleted()).isTrue();
    }

    @Test
    public void testCommandExecutionDoesNotStartsIfNotTriggered() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        try {
            assertThrows(TimeoutException.class, () -> asyncWork.get(100, TimeUnit.MILLISECONDS));

            assertThat(asyncWork.isDone()).isFalse();
            assertThat(toTest.isWorkCompleted()).isFalse();
        } finally {
            asyncWork.cancel(true);
        }
    }

    @Test
    public void testCommandExecutionStartsWhenTriggered() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);
        try {
            assertThrows(TimeoutException.class, () -> asyncWork.get(100, TimeUnit.MILLISECONDS));
            assertThat(asyncWork.isDone()).isFalse();

            toTest.startWork();
            assertThat(asyncWork.get(100, TimeUnit.MILLISECONDS)).isEqualTo(1);
        } finally {
            asyncWork.cancel(true);
        }
    }

    @Test
    public void testBlocksCallerUntilCompleted() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(
                        () -> {
                            Thread.sleep(100);
                            return 1;
                        });

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        assertThat(toTest.waitForCompletion(Duration.ofHours(1))).isTrue();
        assertThat(asyncWork.isDone()).isTrue();
    }

    @Test
    public void testBlocksCallerUntilTimeoutIfNotCompleted() throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(
                        () -> {
                            completionLatch.await();
                            return 1;
                        });

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        try {
            assertThat(toTest.waitForCompletion(Duration.ofMillis(100))).isFalse();
            assertThat(asyncWork.isDone()).isFalse();
        } finally {
            asyncWork.cancel(true);
        }
    }

    // TODO(b/324919960): code below was copied from AdServicesExecutors - should use equivalent
    // code from shared instead (and refactor AdServicesExecutors as well)

    private static final int MIN_LIGHTWEIGHT_EXECUTOR_THREADS = 2;
    private static final String LIGHTWEIGHT_NAME = "lightweight";

    private static ThreadFactory createThreadFactory(
            String name, int priority, Optional<StrictMode.ThreadPolicy> policy) {
        return new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat(name + "-%d")
                .setThreadFactory(
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(final Runnable runnable) {
                                return new Thread(
                                        () -> {
                                            if (policy.isPresent()) {
                                                StrictMode.setThreadPolicy(policy.get());
                                            }
                                            // Process class operates on the current thread.
                                            Process.setThreadPriority(priority);
                                            runnable.run();
                                        });
                            }
                        })
                .build();
    }

    private static final ListeningExecutorService sLightWeightExecutor =
            MoreExecutors.listeningDecorator(
                    new ThreadPoolExecutor(
                            /* corePoolSize= */ Math.max(
                                    MIN_LIGHTWEIGHT_EXECUTOR_THREADS,
                                    Runtime.getRuntime().availableProcessors() - 2),
                            /* maximumPoolSize */
                            Math.max(
                                    MIN_LIGHTWEIGHT_EXECUTOR_THREADS,
                                    Runtime.getRuntime().availableProcessors() - 2),
                            /* keepAliveTime= */ 60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(),
                            createThreadFactory(
                                    LIGHTWEIGHT_NAME,
                                    Process.THREAD_PRIORITY_DEFAULT,
                                    Optional.of(getAsyncThreadPolicy()))));

    private static ThreadPolicy getAsyncThreadPolicy() {
        return new ThreadPolicy.Builder().detectAll().penaltyLog().build();
    }
}
