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

import android.Manifest;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.service.PhFlagsFixture;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
// TODO: Add tests for measurement (b/238194122).
public class PermissionsValidTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
    }

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

    @Test
    public void testValidPermissions_fledgeJoinCustomAudience()
            throws ExecutionException, InterruptedException {
        PhFlagsFixture.overrideFledgeCustomAudienceEnrollmentCheck(true);

        try {
            AdvertisingCustomAudienceClient customAudienceClient =
                    new AdvertisingCustomAudienceClient.Builder()
                            .setContext(sContext)
                            .setExecutor(CALLBACK_EXECUTOR)
                            .build();

            CustomAudience customAudience =
                    new CustomAudience.Builder()
                            .setOwnerPackageName(sContext.getPackageName())
                            .setBuyer(AdTechIdentifier.fromString("test.com"))
                            .setName("exampleCustomAudience")
                            .setDailyUpdateUrl(Uri.parse("https://test.com/daily-update"))
                            .setBiddingLogicUrl(Uri.parse("https://test.com/bidding-logic"))
                            .build();

            customAudienceClient.joinCustomAudience(customAudience).get();
        } finally {
            PhFlagsFixture.overrideFledgeCustomAudienceEnrollmentCheck(false);
        }
    }

    @Test
    public void testValidPermissions_fledgeLeaveCustomAudience()
            throws ExecutionException, InterruptedException {
        PhFlagsFixture.overrideFledgeCustomAudienceEnrollmentCheck(true);

        try {
            AdvertisingCustomAudienceClient customAudienceClient =
                    new AdvertisingCustomAudienceClient.Builder()
                            .setContext(sContext)
                            .setExecutor(CALLBACK_EXECUTOR)
                            .build();

            customAudienceClient
                    .leaveCustomAudience(
                            sContext.getPackageName(),
                            AdTechIdentifier.fromString("test.com"),
                            "exampleCustomAudience")
                    .get();
        } finally {
            PhFlagsFixture.overrideFledgeCustomAudienceEnrollmentCheck(false);
        }
    }

    // TODO(b/238195126): Add FLEDGE ad selection and impression reporting tests

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val) {
        // Setting it to 1 here disables the Topics enrollment check.
        ShellUtils.runShellCommand(
                "setprop debug.adservices.disable_topics_enrollment_check " + val);
    }
}
