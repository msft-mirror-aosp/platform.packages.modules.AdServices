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
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED;
import static com.android.adservices.service.FlagsConstants.PPAPI_AND_SYSTEM_SERVER;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.CtsWebViewSupportUtil;
import android.adservices.utils.DevContextUtils;
import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.net.Uri;
import android.util.Log;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.BroadcastReceiverSyncCallback;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.shared.testing.shell.CommandResult;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executors;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED)
@SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = PPAPI_AND_SYSTEM_SERVER)
@SetStringFlag(name = KEY_PPAPI_APP_ALLOW_LIST, value = "*")
@SetStringFlag(name = KEY_PAS_APP_ALLOW_LIST, value = "*")
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED)
@EnableDebugFlag(KEY_AD_SELECTION_CLI_ENABLED)
@EnableDebugFlag(KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED)
@RequiresSdkLevelAtLeastT(reason = "Protected App Signals is enabled for T+")
public class TriggerEncodingShellCommandTest extends AdServicesDebuggableTestCase {
    public static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final String ACTION_REGISTER_ENCODER_LOGIC_COMPLETE =
            "android.adservices.debug.REGISTER_ENCODER_LOGIC_COMPLETE";

    @Rule(order = 11)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_FLEDGE);

    @Rule(order = 16)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    mContext, "adservices_untrusted_test_server.p12", "adservices_test");

    @Rule(order = 17)
    public SupportedByConditionRule mWebViewSupportedRule =
            CtsWebViewSupportUtil.createJSSandboxAvailableRule(mContext);

    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    private ProtectedSignalsClient mProtectedSignalsClient;

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(mContext);

        mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(mContext)
                        .setExecutor(Executors.newCachedThreadPool())
                        .build();
    }

    @Test
    public void testTriggerEncoding_happyPath_updatesSuccessfully() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/pas-update-signals.json"));
        joinSignals(dispatcher);

        CommandResult triggerEncodingOutput =
                mShellCommandHelper.runCommandRwe("app-signals trigger-encoding --buyer %s", BUYER);

        assertThat(triggerEncodingOutput.getErr()).isEmpty();
        assertThat(triggerEncodingOutput.getOut())
                .isEqualTo("successfully completed signals encoding");
    }

    // TODO(b/331285831): fix this (see go/rb-FutureReturnValueIgnored)
    @SuppressWarnings("FutureReturnValueIgnored")
    private void joinSignals(ScenarioDispatcher scenarioDispatcher) throws InterruptedException {
        Log.d(LOGCAT_TAG_FLEDGE, "Joining signals before running test.");
        mProtectedSignalsClient.updateSignals(
                new UpdateSignalsRequest.Builder(
                                Uri.parse(
                                        scenarioDispatcher.getBaseAddressWithPrefix().toString()
                                                + "/signals"))
                        .build());
        BroadcastReceiverSyncCallback broadcastReceiverSyncCallback =
                new BroadcastReceiverSyncCallback(mContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE);
        broadcastReceiverSyncCallback.assertResultReceived();
        Log.d(LOGCAT_TAG_FLEDGE, "Broadcast was properly received. Encoder logic registered.");
    }
}
