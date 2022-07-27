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

package com.android.adservices.customaudience;

import static org.junit.Assert.assertTrue;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CustomAudienceManagerTest {
    private static final String TAG = "CustomAudienceManagerTest";
    private static final String SERVICE_APK_NAME = "com.android.adservices.api";
    private static final int MAX_RETRY = 50;

    protected static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final CustomAudience CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER).build();

    private void measureJoinCustomAudience(String label) throws Exception {
        Log.i(TAG, "Calling joinCustomAudience()");
        final long start = System.currentTimeMillis();

        AdvertisingCustomAudienceClient client = new AdvertisingCustomAudienceClient.Builder()
                .setContext(CONTEXT)
                .setExecutor(CALLBACK_EXECUTOR)
                .build();

        client.joinCustomAudience(CUSTOM_AUDIENCE).get();

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "joinCustomAudience() took "
                + duration + " ms: " + label);
    }

    private void measureLeaveCustomAudience(String label) throws Exception {
        Log.i(TAG, "Calling joinCustomAudience()");
        final long start = System.currentTimeMillis();

        AdvertisingCustomAudienceClient client = new AdvertisingCustomAudienceClient.Builder()
                .setContext(CONTEXT)
                .setExecutor(CALLBACK_EXECUTOR)
                .build();

        client.leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.getStringForm(),
                        CustomAudienceFixture.VALID_NAME)
                .get();

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "joinCustomAudience() took "
                + duration + " ms: " + label);
    }

    @Test
    public void testCustomAudienceManager() throws Exception {
        measureJoinCustomAudience("no-kill, 1st call");
        measureJoinCustomAudience("no-kill, 2nd call");
        measureLeaveCustomAudience("no-kill, 1st call");
        measureLeaveCustomAudience("no-kill, 2nd call");
    }

    /**
     * Test to measure an "end-to-end" latency of registerSource()
     * and registerTrigger, * when the service process isn't running.
     * <p>
     * To run this test alone, use the following command.
     * {@code atest com.android.adservices.customaudience
     * .CustomAudienceManagerTest#testCallCustomAudienceAPIAfterKillingService}
     *
     * Note the performance varies depending on various factors (examples below),
     * so getting the "real world number" is really hard.
     * - What other processes are running, what they're doing, and the temperature of the
     * device,
     * which affects the CPU clock, disk I/O performance, etc...
     * The busy the CPU is, the higher the clock gets, but that causes the CPU to become hot,
     * which then will lower the CPU clock.
     * For micro-benchmarks, we fixate to a lower clock speed to avoid fluctuation, which works
     * okay for comparing multiple algorithms, but not a good way to get the "actual" number.
     */
    @Test
    public void testCallCustomAudienceAPIAfterKillingService() throws Exception {
        // Kill the service process, if it's already running.
        // Give the system time to calm down.
        // If we know process isn't running for sure, then we don't need it.
        Thread.sleep(1000);
        // Kill the service process.
        ShellUtils.runShellCommand("su 0 killall -9 " + SERVICE_APK_NAME);

        // TODO(b/230873929): Extract to util method.
        int count = 0;
        boolean succeed = false;
        while (count < MAX_RETRY) {
            try {
                measureJoinCustomAudience("with-kill, 1st call");
                succeed = true;
                break;
            } catch (Exception exception) {
                Thread.sleep(1000);
                count++;
            }
        }
        assertTrue(succeed);

        measureJoinCustomAudience("with-kill, 2nd call");
        measureLeaveCustomAudience("with-kill, 1st call");
        measureLeaveCustomAudience("with-kill, 2nd call");
    }
}
