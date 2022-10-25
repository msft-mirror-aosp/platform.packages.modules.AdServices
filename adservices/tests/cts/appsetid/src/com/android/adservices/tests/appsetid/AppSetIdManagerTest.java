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
package com.android.adservices.tests.appsetid;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdManager;
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
public class AppSetIdManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        overrideAppSetIdKillSwitch(true);
    }

    @After
    public void tearDown() throws Exception {
        overrideAppSetIdKillSwitch(false);
    }

    // Override appsetid related kill switch to ignore the effect of actual PH values.
    // If shouldOverride = true, override appsetid related kill switch to OFF to allow adservices
    // If shouldOverride = false, override appsetid related kill switch to meaningless value so that
    // PhFlags will use the default value.
    private void overrideAppSetIdKillSwitch(boolean shouldOverride) {
        String overrideString = shouldOverride ? "false" : "null";
        ShellUtils.runShellCommand(
                "setprop debug.adservices.appsetid_kill_switch " + overrideString);
    }

    @Test
    public void testAppSetIdManager() throws Exception {
        AppSetIdManager appSetIdManager = sContext.getSystemService(AppSetIdManager.class);
        CompletableFuture<AppSetId> future = new CompletableFuture<>();
        OutcomeReceiver<AppSetId, Exception> callback =
                new OutcomeReceiver<AppSetId, Exception>() {
                    @Override
                    public void onResult(AppSetId result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };
        appSetIdManager.getAppSetId(CALLBACK_EXECUTOR, callback);
        AppSetId resultAppSetId = future.get();
        Assert.assertNotNull(resultAppSetId.getId());
        Assert.assertNotNull(resultAppSetId.getScope());
    }
}
