/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.debuggablects;

import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.fromJson;
import static android.adservices.debuggablects.CustomAudienceSubject.assertThat;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.utils.CustomAudienceTestFixture;
import android.adservices.utils.DevContextUtils;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.shell.CommandResult;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetPpapiAppAllowList
public final class CustomAudienceShellCommandsE2ETest extends AdServicesDebuggableTestCase {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");

    @Rule(order = 11)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_FLEDGE);

    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    private CustomAudience mShirtsCustomAudience;
    private CustomAudience mShoesCustomAudience;
    private CustomAudienceTestFixture mCustomAudienceTestFixture;


    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(mContext);

        mCustomAudienceTestFixture = new CustomAudienceTestFixture(mContext);
        mShirtsCustomAudience =
                mCustomAudienceTestFixture.createCustomAudience(
                        "shirts",
                        BUYER,
                        List.of(1D),
                        null,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME);
        mShoesCustomAudience =
                mCustomAudienceTestFixture.createCustomAudience(
                        "shoes",
                        BUYER,
                        List.of(1D),
                        null,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    @After
    public void tearDown() throws Exception {
        mCustomAudienceTestFixture.leaveJoinedCustomAudiences();
    }

    @Test
    public void testRun_listCustomAudience_happyPath() throws Exception {
        mCustomAudienceTestFixture.joinCustomAudience(mShirtsCustomAudience);
        mCustomAudienceTestFixture.joinCustomAudience(mShoesCustomAudience);

        JSONArray customAudiences =
                runAndParseShellCommandJson(
                                "custom-audience list --owner %s --buyer %s", mPackageName, BUYER)
                        .getJSONArray("custom_audiences");
        mCustomAudienceTestFixture.leaveCustomAudience(mShirtsCustomAudience);
        JSONArray customAudiencesAfterLeaving =
                runAndParseShellCommandJson(
                                "custom-audience list --owner %s --buyer %s", mPackageName, BUYER)
                        .getJSONArray("custom_audiences");

        assertThat(
                        List.of(
                                fromJson(customAudiences.getJSONObject(0)),
                                fromJson(customAudiences.getJSONObject(1))))
                .containsExactly(mShirtsCustomAudience, mShoesCustomAudience);
        assertThat(fromJson(customAudiencesAfterLeaving.getJSONObject(0)))
                .isEqualTo(mShoesCustomAudience);
        JSONObject customAudience1 = customAudiences.getJSONObject(0);
        JSONObject customAudience2 = customAudiences.getJSONObject(1);
        JSONObject customAudience3 = customAudiencesAfterLeaving.getJSONObject(0);
        assertThat(customAudience1).hasValidActivationTime();
        assertThat(customAudience1).hasValidationFailures(0);
        assertThat(customAudience1).hasTimeoutFailures(0);
        assertThat(customAudience2).hasValidActivationTime();
        assertThat(customAudience2).hasValidationFailures(0);
        assertThat(customAudience2).hasTimeoutFailures(0);
        assertThat(customAudience3).hasValidActivationTime();
        assertThat(customAudience3).hasValidationFailures(0);
        assertThat(customAudience3).hasTimeoutFailures(0);
    }

    @Test
    public void testRun_viewCustomAudience_happyPath() throws Exception {
        mCustomAudienceTestFixture.joinCustomAudience(mShirtsCustomAudience);

        JSONObject customAudience =
                runAndParseShellCommandJson(
                        "custom-audience view --owner %s --buyer %s --name %s",
                        mPackageName, BUYER, mShirtsCustomAudience.getName());

        CustomAudience parsedCustomAudience = fromJson(customAudience);
        assertThat(mShirtsCustomAudience).isEqualTo(parsedCustomAudience);
        assertThat(customAudience).hasValidActivationTime();
        assertThat(customAudience).hasValidationFailures(0);
        assertThat(customAudience).hasTimeoutFailures(0);
        assertThat(customAudience.getBoolean("is_eligible_for_on_device_auction")).isEqualTo(true);
        assertThat(customAudience.getBoolean("is_eligible_for_server_auction")).isEqualTo(false);
    }

    @Test
    public void testRun_refreshCustomAudiences_verifyNoCustomAudienceChanged() {
        CommandResult commandResult =
                mShellCommandHelper.runCommandRwe(
                        "custom-audience refresh --owner %s --buyer %s --name %s",
                        mPackageName, BUYER, mShirtsCustomAudience.getName());

        assertThat(commandResult.getOut()).isEmpty();
        assertThat(commandResult.getErr()).contains("No custom audience found");
    }

    @FormatMethod
    private JSONObject runAndParseShellCommandJson(
            @FormatString String template, Object... commandArgs) throws JSONException {
        return new JSONObject(mShellCommandHelper.runCommand(template, commandArgs));
    }
}
