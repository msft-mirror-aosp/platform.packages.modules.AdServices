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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.runAsync;
import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.sleep;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.util.Log;

import com.android.adservices.shared.testing.AbstractProcessLifeguardRule.UncaughtBackgroundException;
import com.android.adservices.shared.testing.ProcessLifeguardTestSuite.Test1ThrowsInBg;
import com.android.adservices.shared.testing.ProcessLifeguardTestSuite.Test2RuleCatchesIt;
import com.android.adservices.shared.testing.ProcessLifeguardTestSuite.Test3MakesSureProcessDidntCrash;
import com.android.adservices.shared.testing.concurrency.SimpleSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Suite;
import org.junit.runners.model.Statement;

/**
 * Test suite that asserts an exception thrown in the background in one thread doesn't crash another
 * test.
 */
@Ignore("TODO(b/340959631): failing on pre-submit / cloud")
@RunWith(Suite.class)
@Suite.SuiteClasses({
    Test1ThrowsInBg.class,
    Test2RuleCatchesIt.class,
    Test3MakesSureProcessDidntCrash.class
})
public final class ProcessLifeguardTestSuite {

    private static final String TAG = ProcessLifeguardTestSuite.class.getSimpleName();

    private static final int NAP_TIME_MS = 1_000;
    private static final int SELF_DESTROY_TIMEOUT_MS = 200;
    private static final int WAITING_TIMEOUT_MS =
            SELF_DESTROY_TIMEOUT_MS * 2 // 2 delayed runnables on test 1
                    + NAP_TIME_MS // sleep time on test 2
                    + 2_000; // extra time, just in case

    @SuppressWarnings("StaticAssignmentOfThrowable")
    private static final SecurityException SELF_DESTROYING_EXCEPTION =
            new SecurityException("BG THREAD, Y U NO SURVIVE?");

    // Callback used to make sure the exception from step1 was caught by the rule.
    private static final SimpleSyncCallback sCallback =
            new SimpleSyncCallback(
                    SyncCallbackFactory.newSettingsBuilder()
                            .setMaxTimeoutMs(WAITING_TIMEOUT_MS)
                            .build());

    private static Thread sThreadThatThrows;

    @RunWith(JUnit4.class)
    public static final class Test1ThrowsInBg {

        @Rule
        public final ProcessLifeguardRule rule =
                new ProcessLifeguardRule(ProcessLifeguardRule.Mode.FAIL);

        @Test
        public void doIt() throws Exception {
            Log.i(
                    TAG,
                    "Test1ThrowsInBg: Posting self-destroying (in "
                            + SELF_DESTROY_TIMEOUT_MS
                            + "ms) while running on thread "
                            + Thread.currentThread());
            sThreadThatThrows =
                    runAsync(
                            SELF_DESTROY_TIMEOUT_MS,
                            () -> {
                                Log.i(
                                        TAG,
                                        "Throwing "
                                                + SELF_DESTROYING_EXCEPTION
                                                + " on "
                                                + Thread.currentThread());
                                throw SELF_DESTROYING_EXCEPTION;
                            });
            Log.i(
                    TAG,
                    "Test1ThrowsInBg: Posting another delayed runnable to inject the result in "
                            + SELF_DESTROY_TIMEOUT_MS
                            + " ms");
            runAsync(
                    SELF_DESTROY_TIMEOUT_MS * 2,
                    () -> {
                        Log.i(TAG, "Calling sCallback on " + Thread.currentThread());
                        sCallback.setCalled();
                    });
            Log.i(TAG, "Test1ThrowsInBg: leaving");
        }
    }

    @RunWith(JUnit4.class)
    public static final class Test2RuleCatchesIt {

        // Rule used to make sure ProcessLifeguardRule failed with the exception throwing by test1
        @Rule(order = 0)
        public final UncaughtBackgroundExceptionCheckerRule uncaughtBackgroundExceptionChecker =
                new UncaughtBackgroundExceptionCheckerRule();

        @Rule(order = 1)
        public final ProcessLifeguardRule rule =
                new ProcessLifeguardRule(ProcessLifeguardRule.Mode.FAIL);

        @Test
        public void doIt() throws Exception {
            Log.i(TAG, "Test2RuleCatchesIt: callback=" + sCallback);
            sCallback.assertCalled();
            sleep(NAP_TIME_MS, "to make sure the rule caught the exception");
            Log.i(TAG, "Test2RuleCatchesIt(): leaving");
        }
    }

    @RunWith(JUnit4.class)
    public static final class Test3MakesSureProcessDidntCrash {

        @Rule
        public final ProcessLifeguardRule rule =
                new ProcessLifeguardRule(ProcessLifeguardRule.Mode.FAIL);

        @Test
        public void doIt() {
            Log.i(TAG, "Test3MakesSureProcessDidntCrash: Good News, Everyone! Process is alive");
            assertWithMessage("Thread's dead baby, thread's dead!")
                    .that(sThreadThatThrows.isAlive())
                    .isFalse();
        }
    }
    private static final class UncaughtBackgroundExceptionCheckerRule implements TestRule {

        private static final String TAG =
                UncaughtBackgroundExceptionCheckerRule.class.getSimpleName();

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    try {
                        Log.d(TAG, "Calling " + description);
                        base.evaluate();
                        fail("Test should have thrown a " + UncaughtBackgroundException.class);
                    } catch (UncaughtBackgroundException t) {
                        Log.d(TAG, "Caught '" + t + "' as expected");
                        assertWithMessage("%s", UncaughtBackgroundException.class.getSimpleName())
                                .that(t)
                                .hasCauseThat()
                                .isSameInstanceAs(SELF_DESTROYING_EXCEPTION);
                    } catch (Throwable t) {
                        Log.e(TAG, "Caught unexpected exception: " + t);
                        throw t;
                    }
                }
            };
        }
    }
}
