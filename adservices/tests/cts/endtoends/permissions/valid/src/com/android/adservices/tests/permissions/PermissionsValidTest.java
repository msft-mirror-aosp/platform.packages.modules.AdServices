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

package com.android.adservices.tests.permissions;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
// TODO: Add tests for measurement (b/238194122) and FLEDGE (b/238195126).
public class PermissionsValidTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testValidPermissions_topics() throws Exception {
        overrideDisableTopicsEnrollmentCheck("1");

        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        // Not getting an error here indicates that permissions are valid. The valid case is also
        // tested in TopicsManagerTest.
        assertThat(sdk1Result.getTopics()).isEmpty();
        overrideDisableTopicsEnrollmentCheck("0");
    }

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val) {
        // Setting it to 1 here disables the Topics enrollment check.
        ShellUtils.runShellCommand(
                "setprop debug.adservices.disable_topics_enrollment_check " + val);
    }
}
