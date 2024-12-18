/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.common.CommonFixture;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.CtsWebViewSupportUtil;
import android.adservices.utils.DevContextUtils;
import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.net.Uri;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetFlagEnabled(KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED) // Enabled retry for java script engine
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@RequiresSdkLevelAtLeastT
public final class SignalsCtsDebuggableTest extends ForegroundDebuggableCtsTest {
    private static final String POSTFIX = "/signals";
    private static final String FIRST_POSTFIX = "/signalsFirst";
    private static final String SECOND_POSTFIX = "/signalsSecond";

    @Rule(order = 11)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_FLEDGE);

    @Rule(order = 12)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            CtsWebViewSupportUtil.createJSSandboxAvailableRule(mContext);

    @Rule(order = 13)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    mContext, "adservices_untrusted_test_server.p12", "adservices_test");

    private String mServerBaseAddress;

    private ProtectedSignalsClient mProtectedSignalsClient;

    @Before
    public void setUp() throws Exception {
        flags.setFlag(KEY_PAS_APP_ALLOW_LIST, CommonFixture.TEST_PACKAGE_NAME);

        AdservicesTestHelper.killAdservicesProcess(mContext);
        ExecutorService executor = Executors.newCachedThreadPool();
        mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(mContext)
                        .setExecutor(executor)
                        .build();
    }

    @Test
    public void testUpdateSignals_success() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/signals-default.json"));
        Uri firstUri = Uri.parse(mServerBaseAddress + FIRST_POSTFIX);
        Uri secondUri = Uri.parse(mServerBaseAddress + SECOND_POSTFIX);
        UpdateSignalsRequest firstRequest = new UpdateSignalsRequest.Builder(firstUri).build();
        UpdateSignalsRequest secondRequest = new UpdateSignalsRequest.Builder(secondUri).build();
        mProtectedSignalsClient.updateSignals(firstRequest).get();
        mProtectedSignalsClient.updateSignals(secondRequest).get();

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    @SetFlagDisabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
    public void testUpdateSignals_badUri_failure() throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFile("scenarios/signals-default.json"));
        Uri uri = Uri.EMPTY;
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(uri).build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.updateSignals(request).get());
        assertThat(e.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testUpdateSignals_badJson_failure() throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFile(
                        "scenarios/signals-bad-json.json"));
        Uri uri = Uri.parse(mServerBaseAddress + POSTFIX);
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(uri).build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.updateSignals(request).get());
        assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    private ScenarioDispatcher setupDispatcher(ScenarioDispatcherFactory scenarioDispatcherFactory)
            throws Exception {
        ScenarioDispatcher scenarioDispatcher =
                mMockWebServerRule.startMockWebServer(scenarioDispatcherFactory);
        mServerBaseAddress = scenarioDispatcher.getBaseAddressWithPrefix().toString();
        return scenarioDispatcher;
    }
}
