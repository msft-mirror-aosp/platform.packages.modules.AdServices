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

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.annotations.SetStringArrayFlag;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// TODO: Add tests for measurement (b/238194122).

@EnableAllApis
@SetCompatModeFlags
@SetStringArrayFlag(name = FlagsConstants.KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST, value = "empty")
public final class NotInAllowListTest
        extends CtsAdServicesPermissionsNotInAllowListEndToEndTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String CALLER_NOT_ALLOWED =
            "java.lang.SecurityException: Caller is not authorized to call this API. "
                    + "Caller is not allowed.";

    @Before
    public void setup() {
        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testNotInAllowList_fledgeJoinCustomAudience() {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("exampleCustomAudience")
                        .setDailyUpdateUri(
                                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/daily-update"))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/bidding-logic"))
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> customAudienceClient.joinCustomAudience(customAudience).get());
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_ALLOWED);
    }

    @Test
    public void testNotInAllowList_fledgeFetchAndJoinCustomAudience() {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/fetch/ca"))
                        .setName("exampleCustomAudience")
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> customAudienceClient.fetchAndJoinCustomAudience(request).get());
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_ALLOWED);
    }

    @Test
    public void testNotInAllowList_fledgeLeaveCustomAudience() {
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
                                                CommonFixture.VALID_BUYER_1,
                                                "exampleCustomAudience")
                                        .get());
        assertThat(exception).hasMessageThat().isEqualTo(CALLER_NOT_ALLOWED);
    }
}
