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

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
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

/** In the ad_services_config.xml file, API access is revoked for all, for this test. */
@RunWith(AndroidJUnit4.class)
// TODO: Add tests for measurement (b/238194122).
public class PermissionsAppOptOutTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String CALLER_NOT_AUTHORIZED =
            "java.lang.SecurityException: Caller is not authorized to call this API. "
                    + "Caller is not allowed.";

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    @Test
    public void testAppOptOut_topics() throws Exception {
        overrideDisableTopicsEnrollmentCheck("0");
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient1.getTopics().get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
        overrideDisableTopicsEnrollmentCheck("1");
    }

    @Test
    public void testNoEnrollment_fledgeJoinCustomAudience() {
        PhFlagsFixture.overrideFledgeEnrollmentCheck(true);
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setOwnerPackageName(sContext.getPackageName())
                        .setBuyer(AdTechIdentifier.fromString("buyer.example.com"))
                        .setName("exampleCustomAudience")
                        .setDailyUpdateUrl(Uri.parse("https://buyer.example.com/daily-update"))
                        .setBiddingLogicUrl(Uri.parse("https://buyer.example.com/bidding-logic"))
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> customAudienceClient.joinCustomAudience(customAudience).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
        PhFlagsFixture.overrideFledgeEnrollmentCheck(false);
    }

    @Test
    public void testNoEnrollment_fledgeLeaveCustomAudience() {
        PhFlagsFixture.overrideFledgeEnrollmentCheck(true);
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                customAudienceClient
                                        .leaveCustomAudience(
                                                sContext.getPackageName(),
                                                AdTechIdentifier.fromString("buyer.example.com"),
                                                "exampleCustomAudience")
                                        .get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
        PhFlagsFixture.overrideFledgeEnrollmentCheck(false);
    }

    @Test
    public void testNoEnrollment_selectAds() {
        PhFlagsFixture.overrideFledgeEnrollmentCheck(true);

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.selectAds(adSelectionConfig).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
        PhFlagsFixture.overrideFledgeEnrollmentCheck(false);
    }

    @Test
    public void testNoEnrollment_reportImpression() {
        PhFlagsFixture.overrideFledgeEnrollmentCheck(true);

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        long adSelectionId = 1;

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ReportImpressionRequest request =
                new ReportImpressionRequest(adSelectionId, adSelectionConfig);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.reportImpression(request).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
        PhFlagsFixture.overrideFledgeEnrollmentCheck(false);
    }

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val) {
        // Setting it to 1 here disables the Topics enrollment check.
        ShellUtils.runShellCommand(
                "setprop debug.adservices.disable_topics_enrollment_check " + val);
    }
}
