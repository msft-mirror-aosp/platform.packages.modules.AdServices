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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AddAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionOverrideRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.adselection.TestAdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** In the manifest file, no API permissions are declared for this test. */
@RunWith(AndroidJUnit4.class)
// TODO: Add tests for measurement (b/238194122).
public class PermissionsNoPermTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String CALLER_NOT_AUTHORIZED =
            "java.lang.SecurityException: Caller is not authorized to call this API. "
                    + "Permission was not requested.";

    @Test
    public void testNoPerm_topics() {
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
    }

    @Test
    public void testPermissionNotRequested_fledgeJoinCustomAudience() {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setBuyer(AdTechIdentifier.fromString("buyer.example.com"))
                        .setName("exampleCustomAudience")
                        .setDailyUpdateUri(Uri.parse("https://buyer.example.com/daily-update"))
                        .setBiddingLogicUri(Uri.parse("https://buyer.example.com/bidding-logic"))
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> customAudienceClient.joinCustomAudience(customAudience).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeLeaveCustomAudience() {
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
                                                AdTechIdentifier.fromString("buyer.example.com"),
                                                "exampleCustomAudience")
                                        .get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeOverrideCustomAudienceRemoteInfo() {
        TestAdvertisingCustomAudienceClient testCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(AdTechIdentifier.fromString("buyer.example.com"))
                        .setName("exampleCustomAudience")
                        .setBiddingLogicJs("function test() { return \"hello, world!\"; }")
                        .setTrustedBiddingSignals(AdSelectionSignals.fromString("{\"test\":1}"))
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                testCustomAudienceClient
                                        .overrideCustomAudienceRemoteInfo(request)
                                        .get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeRemoveCustomAudienceRemoteInfoOverride() {
        TestAdvertisingCustomAudienceClient testCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setBuyer(AdTechIdentifier.fromString("buyer.example.com"))
                        .setName("exampleCustomAudience")
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                testCustomAudienceClient
                                        .removeCustomAudienceRemoteInfoOverride(request)
                                        .get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeResetAllCustomAudienceOverrides() {
        TestAdvertisingCustomAudienceClient testCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> testCustomAudienceClient.resetAllCustomAudienceOverrides().get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_selectAds_adSelectionConfig() {
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
    }

    @Test
    public void testPermissionNotRequested_selectAds_adSelectionFromOutcomesConfig() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> mAdSelectionClient.selectAds(config).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_reportImpression() {
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
    }

    @Test
    public void testPermissionNotRequested_fledgeOverrideAdSelectionConfigRemoteInfo() {
        TestAdSelectionClient testAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        String decisionLogicJs = "function test() { return \"hello world\"; }";
        AdSelectionSignals trustedScoringSignals =
                AdSelectionSignals.fromString(
                        "{\n"
                                + "\t\"render_uri_1\": \"signals_for_1\",\n"
                                + "\t\"render_uri_2\": \"signals_for_2\"\n"
                                + "}");

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest(
                        adSelectionConfig, decisionLogicJs, trustedScoringSignals);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            testAdSelectionClient
                                    .overrideAdSelectionConfigRemoteInfo(request)
                                    .get();
                        });
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeRemoveAdSelectionConfigRemoteInfo() {
        TestAdSelectionClient testAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        RemoveAdSelectionOverrideRequest request =
                new RemoveAdSelectionOverrideRequest(adSelectionConfig);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            testAdSelectionClient
                                    .removeAdSelectionConfigRemoteInfoOverride(request)
                                    .get();
                        });
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeResetAllAdSelectionConfigRemoteOverrides() {
        TestAdSelectionClient testAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            testAdSelectionClient.resetAllAdSelectionConfigRemoteOverrides().get();
                        });
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeOverrideAdSelectionFromOutcomesConfigRemoteInfo() {
        TestAdSelectionClient testAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        String selectionLogicJs = "function test() { return \"hello world\"; }";
        AdSelectionSignals selectionSignals = AdSelectionSignals.EMPTY;

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        config, selectionLogicJs, selectionSignals);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            testAdSelectionClient
                                    .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                                    .get();
                        });
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeRemoveAdSelectionFromOutcomesConfigRemoteInfo() {
        TestAdSelectionClient testAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        RemoveAdSelectionFromOutcomesOverrideRequest request =
                new RemoveAdSelectionFromOutcomesOverrideRequest(config);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            testAdSelectionClient
                                    .removeAdSelectionFromOutcomesConfigRemoteInfoOverride(request)
                                    .get();
                        });
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeResetAllAdSelectionFromOutcomesConfigRemoteOverrides() {
        TestAdSelectionClient testAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            testAdSelectionClient
                                    .resetAllAdSelectionFromOutcomesConfigRemoteOverrides()
                                    .get();
                        });
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }
}
