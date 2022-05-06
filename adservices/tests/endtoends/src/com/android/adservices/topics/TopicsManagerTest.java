/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices.topics;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TopicsManagerTest {
    private static final String TAG = "TopicsManagerTest";
    private static final String SERVICE_APK_NAME = "com.android.adservices.api";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private void measureGetTopics(String label) throws Exception {
        Log.i(TAG, "Calling getTopics()");
        final long start = System.currentTimeMillis();

        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        GetTopicsResponse result = advertisingTopicsClient.getTopics().get();
        assertThat(result.getTaxonomyVersions()).isEmpty();
        assertThat(result.getModelVersions()).isEmpty();
        assertThat(result.getTopics()).isEmpty();

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "getTopics() took " + duration + " ms: " + label);
    }

    @Test
    public void testTopicsManager() throws Exception {
        measureGetTopics("no-kill, 1st call");
        measureGetTopics("no-kill, 2nd call");
    }

    /**
     * Test to measure an "end-to-end" latency of getTopics(), when the service process isn't
     * running.
     *
     * <p>To run this test alone, use the following command. atest
     * com.android.adservices.topics.TopicsManagerTest#testGetTopicsAfterKillingService
     *
     * <p>Note the performance varies depending on various factors (examples below), so getting the
     * "real world number" is really hard. - What other processes are running, what they're doing,
     * and the temperature of the device, which affects the CPU clock, disk I/O performance, etc...
     * The busy the CPU is, the higher the clock gets, but that causes the CPU to become hot, which
     * then will lower the CPU clock. For micro-benchmarks, we fixate to a lower clock speed to
     * avoid fluctuation, which works okay for comparing multiple algorithms, but not a good way to
     * get the "actual" number.
     */
    @Test
    public void testGetTopicsAfterKillingService() throws Exception {
        // Kill the service process, if it's already running.
        // Give the system time to calm down.
        // If we know process isn't running for sure, then we don't need it.
        Thread.sleep(1000);
        // Kill the service process.
        ShellUtils.runShellCommand("su 0 killall -9 " + SERVICE_APK_NAME);
        Thread.sleep(1000);

        measureGetTopics("with-kill, 1st call");
        measureGetTopics("with-kill, 2nd call");
    }
}
