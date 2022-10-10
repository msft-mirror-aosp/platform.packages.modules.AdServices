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
package com.android.adservices.tests.adid;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class AdIdManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        overrideAdservicesGlobalKillSwitch(true);
    }

    @After
    public void teardown() {
        overrideAdservicesGlobalKillSwitch(false);
    }

    @Test
    public void testAdIdManager() throws Exception {
        AdIdManager adIdManager = sContext.getSystemService(AdIdManager.class);
        CompletableFuture<AdId> future = new CompletableFuture<>();
        OutcomeReceiver<AdId, Exception> callback =
                new OutcomeReceiver<AdId, Exception>() {
                    @Override
                    public void onResult(AdId result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };
        adIdManager.getAdId(CALLBACK_EXECUTOR, callback);
        AdId resultAdId = future.get();
        Assert.assertNotNull(resultAdId.getAdId());
        Assert.assertNotNull(resultAdId.isLimitAdTrackingEnabled());
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    // If isOverride = true, override global_kill_switch to OFF to allow adservices
    // If isOverride = false, override global_kill_switch to meaningless value so that PhFlags will
    // use the default value
    private void overrideAdservicesGlobalKillSwitch(boolean isOverride) {
        String overrideString = isOverride ? "false" : "null";
        ShellUtils.runShellCommand("setprop debug.adservices.global_kill_switch " + overrideString);
    }
}
