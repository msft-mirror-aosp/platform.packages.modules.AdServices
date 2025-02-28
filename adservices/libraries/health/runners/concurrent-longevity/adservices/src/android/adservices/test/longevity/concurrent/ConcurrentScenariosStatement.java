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

package android.adservices.test.longevity.concurrent;

import android.util.Log;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class which evaluates a particular test method for a particular journey on a new thread for a
 * particular scenario. A scenario can have multiple journeys.
 */
public class ConcurrentScenariosStatement extends Statement {

    // Added some arbitrary time for the latch timeout.
    private static final int WAIT_TIME_SECONDS = 240;
    private static final String TAG = ConcurrentScenariosStatement.class.getSimpleName();
    private static final Executor sExecutor = Executors.newCachedThreadPool();
    private static final AtomicInteger sJourneysToFinish = new AtomicInteger(0);

    private final Class mStressClass;
    private final List<Object> mJourneys;
    private final List<FrameworkMethod> mMethods;

    public ConcurrentScenariosStatement(List<Object> journeys, List<FrameworkMethod> methods) {
        mJourneys = journeys;
        mMethods = methods;

        if (sJourneysToFinish.get() > 0) {
            throw new IllegalArgumentException(
                    "There shouldn't any running journeys on creating a new statement!");
        }

        sJourneysToFinish.set(0);

        mStressClass = android.adservices.test.longevity.concurrent.StressScenarioTestAction.class;

        for (Object object : journeys) {
            if (!isAssignableFromStressClass(object)) {
                sJourneysToFinish.incrementAndGet();
            }
        }
    }

    /**
     * Indicates if all CUJs for this scenario have been finished and stress tests can be cancelled.
     */
    public static boolean needToCancelStressTests() {
        return sJourneysToFinish.get() == 0;
    }

    private boolean isAssignableFromStressClass(Object object) {
        return mStressClass.isAssignableFrom(object.getClass());
    }

    @Override
    public void evaluate() throws Throwable {
        CountDownLatch latch = new CountDownLatch(mJourneys.size());
        for (int i = 0; i < mJourneys.size(); i++) {
            Object testClass = mJourneys.get(i);
            FrameworkMethod testMethod = mMethods.get(i);
            Runnable runnable =
                    () -> {
                        try {
                            testMethod.invokeExplosively(testClass);
                        } catch (Throwable tr) {
                            // TODO (b/297108433) : throw sub class RuntimeException in the
                            //  follow up cl. Currently when running getTopics concurrently, we
                            //  hit rate limit exception and test crashes as rate limit is set to
                            //  1 per second.
                            Log.e(
                                    TAG,
                                    String.format(
                                            "Failed to evaluate %s.%s",
                                            testClass.getClass().getSimpleName(),
                                            testMethod.getName()),
                                    tr);
                        } finally {
                            Log.d(
                                    TAG,
                                    String.format(
                                            "%s.%s finished. Decrementing latch count by 1",
                                            testClass.getClass().getSimpleName(),
                                            testMethod.getName()));
                            latch.countDown();

                            if (!isAssignableFromStressClass(testClass)) {
                                sJourneysToFinish.decrementAndGet();
                            }
                        }
                    };
            sExecutor.execute(runnable);
        }

        // Wait till all the journeys finishes.
        Log.d(
                TAG,
                String.format(
                        "Waiting max %d seconds for %d journeys to finish",
                        WAIT_TIME_SECONDS, mJourneys.size()));
        if (!latch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    String.format(
                            "Scenario time out after waiting for %d seconds", WAIT_TIME_SECONDS));
        }
    }
}
