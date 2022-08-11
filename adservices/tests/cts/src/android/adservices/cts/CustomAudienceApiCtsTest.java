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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;
import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.service.PhFlagsFixture;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CustomAudienceApiCtsTest {

    private static final String WRITE_DEVICE_CONFIG_PERMISSION =
            "android.permission.WRITE_DEVICE_CONFIG";

    private AdvertisingCustomAudienceClient mClient;
    private TestAdvertisingCustomAudienceClient mTestClient;

    private static final String OWNER = "owner";
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer");
    private static final String NAME = "name";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    private boolean mIsDebugMode;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        mClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(context)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mTestClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(context)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        DevContext devContext = DevContextFilter.create(context).createDevContext(Process.myUid());
        mIsDebugMode = devContext.getDevOptionsEnabled();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG_PERMISSION);
        // This test is running in background
        PhFlagsFixture.overrideForegroundStatusForFledgeCustomAudience(false);
    }

    @Test
    public void testJoinCustomAudience_validCustomAudience_success()
            throws ExecutionException, InterruptedException {
        mClient.joinCustomAudience(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .build())
                .get();
    }

    @Test
    public void testJoinCustomAudience_ownerIsNotCallingApp_fail() {
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mClient.joinCustomAudience(
                                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                                CommonFixture.VALID_BUYER)
                                                        .setOwnerPackageName("Invalid_owner")
                                                        .build())
                                        .get());
        assertTrue(exception.getCause() instanceof SecurityException);
    }

    @Test
    public void testJoinCustomAudience_illegalExpirationTime_fail() {
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                        .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                        .build();
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mClient.joinCustomAudience(customAudience).get());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testLeaveCustomAudience_joinedCustomAudience_success()
            throws ExecutionException, InterruptedException {
        mClient.joinCustomAudience(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .build())
                .get();
        mClient.leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME)
                .get();
    }

    @Test
    public void testLeaveCustomAudience_notJoinedCustomAudience_doesNotFail()
            throws ExecutionException, InterruptedException {
        mClient.leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER,
                        "not_exist_name")
                .get();
    }

    @Test
    public void testLeaveCustomAudience_ownerNotCallingApp_fail() {
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mClient.leaveCustomAudience(
                                                "Invalid_owner",
                                                CommonFixture.VALID_BUYER,
                                                CustomAudienceFixture.VALID_NAME)
                                        .get());
        assertTrue(exception.getCause() instanceof SecurityException);
    }

    @Test
    public void testAddOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setOwnerPackageName(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_DATA)
                        .build();

        ListenableFuture<Void> result = mTestClient.overrideCustomAudienceRemoteInfo(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testRemoveOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setOwnerPackageName(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ListenableFuture<Void> result = mTestClient.removeCustomAudienceRemoteInfoOverride(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testResetAllOverridesFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        ListenableFuture<Void> result = mTestClient.resetAllCustomAudienceOverrides();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }
}
