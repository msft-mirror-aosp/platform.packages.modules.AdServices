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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignalsTest extends ForegroundDebuggableCtsTest {

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String POSTFIX = "signals";
    private static final String FIRST_POSTFIX = "signalsFirst";
    private static final String SECOND_POSTFIX = "signalsSecond";

    private String mServerBaseAddress;
    private MockWebServer mMockWebServer;
    private String mPreviousAppAllowList;

    private ProtectedSignalsClient mProtectedSignalsClient;

    @Rule
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    CONTEXT, "adservices_untrusted_test_server.p12", "adservices_test");

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        if (SdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        } else {
            mPreviousAppAllowList =
                    CompatAdServicesTestUtils.getAndOverridePpapiAppAllowList(
                            sContext.getPackageName());
            CompatAdServicesTestUtils.setFlags();
        }

        AdservicesTestHelper.killAdservicesProcess(sContext);
        ExecutorService executor = Executors.newCachedThreadPool();
        mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(CONTEXT)
                        .setExecutor(executor)
                        .build();
    }

    @After
    public void tearDown() throws IOException {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        if (!AdservicesTestHelper.isDeviceSupported()) {
            return;
        }
        if (!SdkLevel.isAtLeastT()) {
            CompatAdServicesTestUtils.setPpapiAppAllowList(mPreviousAppAllowList);
            CompatAdServicesTestUtils.resetFlagsToDefault();
        }
    }

    @Test
    public void testUpdateSignals_success() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-default.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri firstUri = Uri.parse(mServerBaseAddress + FIRST_POSTFIX);
        Uri secondUri = Uri.parse(mServerBaseAddress + SECOND_POSTFIX);
        UpdateSignalsRequest firstRequest = new UpdateSignalsRequest.Builder(firstUri).build();
        UpdateSignalsRequest secondRequest = new UpdateSignalsRequest.Builder(secondUri).build();
        mProtectedSignalsClient.fetchSignalUpdates(firstRequest).get();
        mProtectedSignalsClient.fetchSignalUpdates(secondRequest).get();

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testUpdateSignals_badUri_failure() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-default.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri uri = Uri.parse("");
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(uri).build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.fetchSignalUpdates(request).get());
        assertTrue(e.getCause() instanceof SecurityException);
    }

    @Test
    public void testUpdateSignals_badJson_failure() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-bad-json.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri uri = Uri.parse(mServerBaseAddress + POSTFIX);
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(uri).build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.fetchSignalUpdates(request).get());
        assertTrue(e.getCause() instanceof IllegalArgumentException);
    }

    // TODO(b/299336069) Get rid of the repeated code here.
    private void setupDefaultMockWebServer(ScenarioDispatcher dispatcher) throws Exception {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        mMockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);
        mServerBaseAddress = getServerBaseAddress();
    }

    private String getServerBaseAddress() {
        return String.format(
                "https://%s:%s/", mMockWebServer.getHostName(), mMockWebServer.getPort());
    }
}
