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

import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.OUTPUT_PROTO_FIELD_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.common.AdTechIdentifier;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.CommonFixture;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.DevContextUtils;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.net.Uri;
import android.util.Base64;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.shell.CommandResult;
import android.adservices.utils.MockWebServerRule;

import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_AD_SELECTION_CLI_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public class ViewAuctionResultCommandTest extends FledgeDebuggableScenarioTest {
    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Test
    public void testRun_onDeviceAuction_returnsFailure() throws Exception {
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
            assertThat(output.getOut()).isEmpty();
            assertThat(output.getErr())
                    .isEqualTo(
                            String.format(
                                    "no auction result found for ad selection id: %s",
                                    result.getAdSelectionId()));
        } finally {
            leaveCustomAudience(mCustomAudienceClient, SHIRTS_CA);
        }
    }
}
