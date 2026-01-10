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

import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;
import android.adservices.signals.UpdateSignalsRequest;
import android.net.Uri;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.DevContextUtils;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** In the manifest file, no API permissions are declared for this test. */
// TODO: Add tests for measurement (b/238194122).
public final class PermissionsNoPermTest extends CtsAdServicesPermissionsNoPermEndToEndTestCase {
    private static final String TAG = "PermissionsNoPermTest";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String CALLER_NOT_AUTHORIZED =
            "java.lang.SecurityException: Caller is not authorized to call this API. "
                    + "Permission was not requested.";

    private boolean mHasAccessToDevOverrides;

    private String mAccessStatus;

    @Before
    public void setup() {
        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
        mHasAccessToDevOverrides = DevContextUtils.isDevOptionsEnabled(sContext, TAG);
        mAccessStatus = String.format("mHasAccessToDevOverrides is %b", mHasAccessToDevOverrides);
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
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeFetchAndJoinCustomAudience() {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                Uri.parse("https://buyer.example.com/fetch/ca"))
                        .setName("exampleCustomAudience")
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> customAudienceClient.fetchAndJoinCustomAudience(request).get());
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_AUTHORIZED);
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
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeOverrideCustomAudienceRemoteInfo() {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

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
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeRemoveCustomAudienceRemoteInfoOverride() {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

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
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testPermissionNotRequested_fledgeResetAllCustomAudienceOverrides() {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        TestAdvertisingCustomAudienceClient testCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> testCustomAudienceClient.resetAllCustomAudienceOverrides().get());
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    @SuppressWarnings("NewApi")
    @SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
    @RequiresSdkLevelAtLeastT
    public void testPermissionNotRequested_updateSignals() {
        UpdateSignalsRequest request =
                new UpdateSignalsRequest.Builder(Uri.parse("https://fakeServer/signals")).build();
        ProtectedSignalsClient mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.updateSignals(request).get());

        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_AUTHORIZED);
    }
}
