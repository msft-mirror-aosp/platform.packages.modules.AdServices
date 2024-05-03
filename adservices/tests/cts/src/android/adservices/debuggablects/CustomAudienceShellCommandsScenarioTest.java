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

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.PPAPI_AND_SYSTEM_SERVER;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.customaudience.CustomAudience;
import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.adservices.utils.Scenarios;
import android.net.Uri;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = PPAPI_AND_SYSTEM_SERVER)
@SetFlagEnabled(KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED)
@RequiresSdkLevelAtLeastS(reason = "Custom Audience is enabled for S+")
public final class CustomAudienceShellCommandsScenarioTest extends FledgeScenarioTest {
    private static final String OWNER = sContext.getPackageName();

    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Before
    public void setDebugFlag() {
        flags.setDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED, true);
    }

    @Test
    public void testRun_refreshCustomAudiences_verifyCustomAudienceChanged() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.fromScenarioWithPrefix(
                                "scenarios/remarketing-cuj-refresh-ca.json",
                                getCacheBusterPrefix()));
        joinCustomAudience(SHOES_CA);

        CustomAudience customAudienceBefore = getCustomAudience();
        mShellCommandHelper.runCommand(
                "custom-audience refresh --owner %s --buyer %s --name %s",
                OWNER, mAdTechIdentifier, SHOES_CA);
        CustomAudience customAudienceAfter = getCustomAudience();

        assertThat(customAudienceBefore).isNotEqualTo(customAudienceAfter);
        assertThat(customAudienceAfter.getTrustedBiddingData().getTrustedBiddingUri())
                .isEqualTo(Uri.parse(getServerBaseAddress() + Scenarios.BIDDING_SIGNALS_PATH));
        assertThat(customAudienceAfter.getTrustedBiddingData().getTrustedBiddingKeys())
                .isEqualTo(List.of("key1", "key2"));
        assertThat(customAudienceAfter.getUserBiddingSignals())
                .isEqualTo(
                        AdSelectionSignals.fromString(
                                "{\"valid\":true,\"arbitrary\":\"yes\"}", true));
        assertThat(customAudienceAfter.getAds())
                .isEqualTo(
                        List.of(
                                new AdData.Builder()
                                        .setRenderUri(
                                                Uri.parse(
                                                        getServerBaseAddress()
                                                                + Scenarios.AD_RENDER_1))
                                        .setMetadata("{\"valid\":1}")
                                        .build(),
                                new AdData.Builder()
                                        .setRenderUri(
                                                Uri.parse(
                                                        getServerBaseAddress()
                                                                + Scenarios.AD_RENDER_2))
                                        .setMetadata("{\"valid\":2}")
                                        .build()));
        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        leaveCustomAudience(SHOES_CA);
    }

    private CustomAudience getCustomAudience() throws JSONException {
        return CustomAudienceShellCommandHelper.fromJson(
                new JSONObject(
                        mShellCommandHelper.runCommand(
                                "custom-audience view --owner %s --buyer %s --name %s",
                                OWNER, mAdTechIdentifier, SHOES_CA)));
    }
}
