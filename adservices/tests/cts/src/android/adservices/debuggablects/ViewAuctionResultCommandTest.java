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
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.OUTPUT_PROTO_FIELD_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.common.CommonFixture;
import android.adservices.utils.DevContextUtils;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.util.Base64;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.shell.CommandResult;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_AD_SELECTION_CLI_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public class ViewAuctionResultCommandTest extends FledgeDebuggableScenarioTest {
    private static final String STATUS_FINISHED = "FINISHED";

    @Rule(order = 11)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_FLEDGE);

    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Test
    public void testRun_withJoinCustomAudienceAndAuction_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-default.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        CommandResult output;
        try {
            joinCustomAudience(mCustomAudienceClient, SHIRTS_CA);
            AdSelectionOutcome result = doSelectAds(mAdSelectionClient, adSelectionConfig);
            output =
                    mShellCommandHelper.runCommandRwe(
                            "ad-selection view-auction-result --ad-selection-id %s",
                            Long.toString(result.getAdSelectionId()));
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(mCustomAudienceClient, SHIRTS_CA);
        }

        assertThat(output.getCommandStatus()).isEqualTo(STATUS_FINISHED);
        assertThat(output.getErr()).isEmpty();
        AuctionResult actual =
                AuctionResult.parseFrom(
                        Base64.decode(
                                new JSONObject(output.getOut()).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        assertThat(actual.getIsChaff()).isFalse();
        assertThat(actual.getBid()).isEqualTo(5.0f);
        assertThat(actual.getCustomAudienceOwner()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        assertThat(actual.getCustomAudienceName()).isEqualTo(SHIRTS_CA);
        assertThat(actual.getAdRenderUrl())
                .isEqualTo(
                        dispatcher.getBaseAddressWithPrefix().toString()
                                + "/render/"
                                + SHIRTS_CA
                                + "/0");
        assertThat(actual.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl())
                .isEmpty();
        assertThat(actual.getWinReportingUrls().getTopLevelSellerReportingUrls().getReportingUrl())
                .isEmpty();
        assertThat(
                        actual.getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getInteractionReportingUrls())
                .isEmpty();
        assertThat(
                        actual.getWinReportingUrls()
                                .getBuyerReportingUrls()
                                .getInteractionReportingUrls())
                .isEmpty();
        assertThat(dispatcher.getVerifyCalledPaths()).isEqualTo(dispatcher.getCalledPaths());
    }
}
