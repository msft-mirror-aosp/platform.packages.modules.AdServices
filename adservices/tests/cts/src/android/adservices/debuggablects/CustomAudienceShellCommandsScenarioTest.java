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

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.adservices.utils.Scenarios;
import android.net.Uri;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public final class CustomAudienceShellCommandsScenarioTest extends FledgeDebuggableScenarioTest {
    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Test
    public void testRun_refreshCustomAudiences_verifyCustomAudienceChanged() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-refresh-ca.json"));
        joinCustomAudience(SHOES_CA);
        AdTechIdentifier adTechIdentifier =
                AdTechIdentifier.fromString(dispatcher.getBaseAddressWithPrefix().getHost());
        String baseAddressWithPrefix = dispatcher.getBaseAddressWithPrefix().toString();

        CustomAudience customAudienceBefore = getCustomAudience(adTechIdentifier);
        mShellCommandHelper.runCommand(
                "custom-audience refresh --owner %s --buyer %s --name %s",
                mPackageName, adTechIdentifier, SHOES_CA);
        CustomAudience customAudienceAfter = getCustomAudience(adTechIdentifier);

        assertThat(customAudienceBefore).isNotEqualTo(customAudienceAfter);
        assertThat(customAudienceAfter.getTrustedBiddingData().getTrustedBiddingUri())
                .isEqualTo(Uri.parse(baseAddressWithPrefix + Scenarios.BIDDING_SIGNALS_PATH));
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
                                                        baseAddressWithPrefix
                                                                + Scenarios.AD_RENDER_1))
                                        .setMetadata("{\"valid\":1}")
                                        .build(),
                                new AdData.Builder()
                                        .setRenderUri(
                                                Uri.parse(
                                                        baseAddressWithPrefix
                                                                + Scenarios.AD_RENDER_2))
                                        .setMetadata("{\"valid\":2}")
                                        .build()));
        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        leaveCustomAudience(SHOES_CA);
    }

    private CustomAudience getCustomAudience(AdTechIdentifier adTechIdentifier)
            throws JSONException {
        return CustomAudienceShellCommandHelper.fromJson(
                new JSONObject(
                        mShellCommandHelper.runCommand(
                                "custom-audience view --owner %s --buyer %s --name %s",
                                mPackageName, adTechIdentifier, SHOES_CA)));
    }
}
