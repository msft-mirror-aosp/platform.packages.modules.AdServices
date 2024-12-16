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

import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.PPAPI_AND_SYSTEM_SERVER;
import static com.android.adservices.service.signals.ProtectedSignalsArgumentImpl.validateAndSerializeBase64;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.DevContextUtils;
import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.net.Uri;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.shell.CommandResult;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = PPAPI_AND_SYSTEM_SERVER)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@RequiresSdkLevelAtLeastT(reason = "Protected App Signals is enabled for T+")
public final class GenerateInputForEncodingShellCommandTest extends AdServicesDebuggableTestCase {
    private static final String STATUS_FINISHED = "FINISHED";
    private static final int PAS_API_TIMEOUT_SEC = 10;

    @Rule(order = 11)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_FLEDGE);

    @Rule(order = 16)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    mContext, "adservices_untrusted_test_server.p12", "adservices_test");

    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    private ProtectedSignalsClient mProtectedSignalsClient;

    @Before
    public void setUp() throws Exception {
        flags.setFlag(KEY_PAS_APP_ALLOW_LIST, TEST_PACKAGE_NAME);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(mContext)
                        .setExecutor(Executors.newCachedThreadPool())
                        .build();
    }

    @Test
    public void testRun_generateInputForEncoding_happyPath() throws Exception {
        ScenarioDispatcherFactory scenarioDispatcherFactory =
                ScenarioDispatcherFactory.createFromScenarioFile(
                        "scenarios/pas-update-signals.json");
        ScenarioDispatcher scenarioDispatcher =
                mMockWebServerRule.startMockWebServer(scenarioDispatcherFactory);

        mProtectedSignalsClient
                .updateSignals(
                        new UpdateSignalsRequest.Builder(
                                        Uri.parse(
                                                scenarioDispatcher
                                                                .getBaseAddressWithPrefix()
                                                                .toString()
                                                        + "/signals"))
                                .build())
                .get(PAS_API_TIMEOUT_SEC, TimeUnit.SECONDS);
        CommandResult commandResult =
                mShellCommandHelper.runCommandRwe(
                        "app-signals generate-input-for-encoding --buyer %s",
                        AdTechIdentifier.fromString(
                                scenarioDispatcher.getBaseAddressWithPrefix().getHost()));

        // These values are from the server response.
        assertThat(commandResult.getCommandStatus()).isEqualTo(STATUS_FINISHED);
        assertThat(commandResult.getErr()).isEmpty();

        assertThat(commandResult.getOut()).contains(validateAndSerializeBase64("AAAAAQ=="));
        assertThat(commandResult.getOut()).contains(validateAndSerializeBase64("AAAAAg=="));
        assertThat(commandResult.getOut()).contains(TEST_PACKAGE_NAME);
    }
}
