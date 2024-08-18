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
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.PPAPI_AND_SYSTEM_SERVER;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.shell.CommandResult;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = PPAPI_AND_SYSTEM_SERVER)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_AD_SELECTION_CLI_ENABLED)
@EnableDebugFlag(KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED)
@RequiresSdkLevelAtLeastT(reason = "Protected App Signals is enabled for T+")
public class TriggerEncodingShellCommandTest extends ForegroundDebuggableCtsTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final int PAS_API_TIMEOUT_SEC = 5;
    // Use a very low value as we are using a Room in-memory database here.
    public static final int PAS_DATABASE_WRITE_LATENCY_SLEEP_MS = 1000;
    private ProtectedSignalsClient mProtectedSignalsClient;

    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Rule(order = 6)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    sContext, "adservices_untrusted_test_server.p12", "adservices_test");

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(sContext);

        if (sdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        }

        mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(sContext)
                        .setExecutor(Executors.newCachedThreadPool())
                        .build();
    }

    @Test
    public void testTriggerEncoding_happyPath_updatesSuccessfully()
            throws GeneralSecurityException,
                    IOException,
                    JSONException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/pas-update-signals.json"));
        joinSignals(dispatcher);

        CommandResult triggerEncodingOutput =
                mShellCommandHelper.runCommandRwe("app-signals trigger-encoding --buyer %s", BUYER);

        assertThat(triggerEncodingOutput.getErr()).isEmpty();
        assertThat(triggerEncodingOutput.getOut())
                .isEqualTo("successfully completed signals encoding");
        assertThat(dispatcher.getVerifyCalledPaths()).isEqualTo(dispatcher.getCalledPaths());
    }

    private void joinSignals(ScenarioDispatcher scenarioDispatcher)
            throws ExecutionException, InterruptedException, TimeoutException {
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
        // Wait for database event before continuing. PAS updates are asynchronous so this is the
        // only way to consistently wait. Adding this after the updateSignals API returns lowers any
        // potential flakiness.
        sLogger.v("Sleeping while PAS update writes take place...");
        Thread.sleep(PAS_DATABASE_WRITE_LATENCY_SLEEP_MS);
        sLogger.v("Checking if encoding endpoint is registered...");
    }
}
