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

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceManager;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.JoinCustomAudienceRequestFixture;
import android.adservices.customaudience.LeaveCustomAudienceRequest;
import android.adservices.customaudience.LeaveCustomAudienceRequestFixture;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.AdServicesEndToEndTestCase;
import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.shared.common.exception.AdServicesDeprecationConstants;
import com.android.adservices.shared.testing.OutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresLowRamDevice;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

@SetFlagDisabled(KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE)
@SetFlagEnabled(KEY_ENABLE_ENROLLMENT_TEST_SEED)
@SetIntegerFlag(name = KEY_SDK_REQUEST_PERMITS_PER_SECOND, value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(name = KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND, value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetIntegerFlag(
        name = KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND,
        value = Integer.MAX_VALUE)
@SetPpapiAppAllowList
public final class CustomAudienceManagerTest extends AdServicesEndToEndTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Before
    public void setUp() throws TimeoutException {
        String[] deviceConfigPermissions;
        if (SdkLevel.isAtLeastU()) {
            deviceConfigPermissions =
                    new String[] {
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG
                    };
        } else {
            deviceConfigPermissions = new String[] {Manifest.permission.WRITE_DEVICE_CONFIG};
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(deviceConfigPermissions);

        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testJoinCustomAudience_shouldReceiveDeprecationError() throws Exception {
        OutcomeReceiverForTests<Object> receiver = new OutcomeReceiverForTests<>();

        CustomAudienceManager manager = CustomAudienceManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.joinCustomAudience(
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_1),
                CALLBACK_EXECUTOR,
                receiver);

        IllegalStateException exception = receiver.assertFailure(IllegalStateException.class);
        assertWithMessage("Verifies API deprecation message.")
                .that(exception.getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testFetchAndJoinCustomAudience_shouldReceiveDeprecationError() throws Exception {
        OutcomeReceiverForTests<Object> receiver = new OutcomeReceiverForTests<>();

        CustomAudienceManager manager = CustomAudienceManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.fetchAndJoinCustomAudience(
                new FetchAndJoinCustomAudienceRequest.Builder(
                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                        CommonFixture.VALID_BUYER_1, "1"))
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build(),
                CALLBACK_EXECUTOR,
                receiver);

        IllegalStateException exception = receiver.assertFailure(IllegalStateException.class);
        assertWithMessage("Verifies API deprecation message.")
                .that(exception.getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testLeaveCustomAudience_shouldReceiveDeprecationError() throws Exception {
        OutcomeReceiverForTests<Object> receiver = new OutcomeReceiverForTests<>();

        CustomAudienceManager manager = CustomAudienceManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.leaveCustomAudience(
                LeaveCustomAudienceRequestFixture.getLeaveCustomAudienceRequestWithBuyer(
                        CommonFixture.VALID_BUYER_1),
                CALLBACK_EXECUTOR,
                receiver);

        IllegalStateException exception = receiver.assertFailure(IllegalStateException.class);
        assertWithMessage("Verifies API deprecation message.")
                .that(exception.getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testScheduleCustomAudienceUpdate_shouldReceiveDeprecationError() throws Exception {
        AdServicesOutcomeReceiverForTests<Object> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        CustomAudienceManager manager = CustomAudienceManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.scheduleCustomAudienceUpdate(
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                        CommonFixture.VALID_BUYER_1, "1"),
                                Duration.ofMinutes(100),
                                ImmutableList.of(
                                        new PartialCustomAudience.Builder("fake_ca").build()))
                        .build(),
                CALLBACK_EXECUTOR,
                receiver);

        IllegalStateException exception = receiver.assertFailure(IllegalStateException.class);
        assertWithMessage("Verifies API deprecation message.")
                .that(exception.getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Ignore("TODO(b/295231590): remove annotation when bug is fixed")
    @Test
    @RequiresLowRamDevice
    public void testFetchAndJoinCustomAudience_lowRamDevice() throws Exception {
        OutcomeReceiverForTests<Object> receiver = new OutcomeReceiverForTests<>();

        CustomAudienceManager manager = CustomAudienceManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.fetchAndJoinCustomAudience(
                new FetchAndJoinCustomAudienceRequest.Builder(
                                Uri.parse("https://buyer.example.com/fetch/ca"))
                        .build(),
                CALLBACK_EXECUTOR,
                receiver);

        receiver.assertFailure(IllegalStateException.class);
    }

    @Ignore("TODO(b/295231590): remove annotation when bug is fixed")
    @Test
    @RequiresLowRamDevice
    public void testLeaveCustomAudienceRequest_lowRamDevice() throws Exception {
        OutcomeReceiverForTests<Object> receiver = new OutcomeReceiverForTests<>();
        CustomAudienceManager manager = CustomAudienceManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.leaveCustomAudience(
                new LeaveCustomAudienceRequest.Builder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("D.H.A.R.M.A.")
                        .build(),
                CALLBACK_EXECUTOR,
                receiver);

        receiver.assertFailure(IllegalStateException.class);
    }
}
