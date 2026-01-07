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

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.cts.ScheduleCustomAudienceUpdateRequestTest.VALID_DELAY;
import static android.adservices.cts.ScheduleCustomAudienceUpdateRequestTest.VALID_UPDATE_URI_1;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.net.Uri;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.shared.common.exception.AdServicesDeprecationConstants;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

@DisableGlobalKillSwitch
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
@SetAllLogcatTags
@SetFlagDisabled(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@SetFlagEnabled(KEY_ENABLE_ENROLLMENT_TEST_SEED)
@SetPpapiAppAllowList
public final class CustomAudienceApiCtsTest extends ForegroundCtsTestCase {
    private AdvertisingCustomAudienceClient mClient;
    private TestAdvertisingCustomAudienceClient mTestClient;

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer");
    private static final String NAME = "name";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    private boolean mIsDebugMode;

    @Before
    public void setup() throws Exception {
        if (sdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        }

        mClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mTestClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        DevContext devContext =
                DevContextFilter.create(sContext, /* developerModeFeatureEnabled= */ false)
                        .createDevContext(Process.myUid());
        mIsDebugMode = devContext.getDeviceDevOptionsEnabled();

        // Needed to test different custom audience limits
        String[] deviceConfigPermissions;
        if (sdkLevel.isAtLeastU()) {
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
    public void testJoinCustomAudience_shouldThrowDeprecatedException() {
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mClient.joinCustomAudience(
                                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                                VALID_BUYER_1)
                                                        .build())
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testLeaveCustomAudience_shouldThrowDeprecatedException() {
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mClient.leaveCustomAudience(
                                                VALID_BUYER_1, CustomAudienceFixture.VALID_NAME)
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testFetchAndJoinCustomAudience_shouldThrowDeprecatedException() {
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).build();
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                Uri.parse(customAudience.getBuyer() + "/fetchCA"))
                        .setName(customAudience.getName())
                        .setActivationTime(customAudience.getActivationTime())
                        .setExpirationTime(customAudience.getExpirationTime())
                        .setUserBiddingSignals(customAudience.getUserBiddingSignals())
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mClient.fetchAndJoinCustomAudience(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testScheduleCustomAudienceUpdate_shouldThrowDeprecatedException() {
        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(VALID_UPDATE_URI_1, VALID_DELAY)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mClient.scheduleCustomAudienceUpdate(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfo_shouldThrowDeprecatedException() {
        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_DATA)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mTestClient.overrideCustomAudienceRemoteInfo(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverride_shouldThrowDeprecatedException() {
        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mTestClient.removeCustomAudienceRemoteInfoOverride(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testResetAllOverridesFailsWithDebugModeDisabled() {
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mTestClient.resetAllCustomAudienceOverrides().get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(
                        AdServicesDeprecationConstants.CUSTOM_AUDIENCE_SERVICE_DEPRECATION_MESSAGE);
    }
}
