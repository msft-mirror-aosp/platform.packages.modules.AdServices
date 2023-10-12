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
import com.android.adservices.service.js.JSScriptEngine;
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

public class SignalsEncodingTest extends ForegroundDebuggableCtsTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String SIGNALS_VALID_ENCODER = "signalsWithValidEncoder";
    private static final String SIGNALS_INVALID_ENCODER = "signalsWithInvalidEncoder";

    private String mServerBaseAddress;
    private ProtectedSignalsClient mProtectedSignalsClient;
    private String mPreviousAppAllowList;
    private MockWebServer mMockWebServer;

    @Rule
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    CONTEXT, "adservices_untrusted_test_server.p12", "adservices_test");

    @Before
    public void setup() {
        // Skip the test if it runs on unsupported platforms
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        if (SdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        } else {
            mPreviousAppAllowList =
                    CompatAdServicesTestUtils.getAndOverridePpapiAppAllowList(
                            CONTEXT.getPackageName());
            CompatAdServicesTestUtils.setFlags();
        }

        AdservicesTestHelper.killAdservicesProcess(CONTEXT);
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
    public void testUpdateSignalsFetcherEncoderEndPoint_success() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-valid-encoder.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri updateRequestUri = Uri.parse(mServerBaseAddress + SIGNALS_VALID_ENCODER);
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(updateRequestUri).build();
        mProtectedSignalsClient.fetchSignalUpdates(request);

        // TODO(b/304857566) We can replace this test once integrated with B&A
        // For subsequent runs of this test on same device, the encoder logic is not fetched again
        // as the buyer already has the downloaded logic. So far, the call to encoder is expected
        // but not enforced.
        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testUpdateSignalsFetcherEncoderEndPoint_failure() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario("scenarios/signals-invalid-encoder.json", "");
        setupDefaultMockWebServer(dispatcher);
        Uri updateRequestUri = Uri.parse(mServerBaseAddress + SIGNALS_INVALID_ENCODER);
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(updateRequestUri).build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> mProtectedSignalsClient.fetchSignalUpdates(request).get());
        assertTrue(e.getCause() instanceof IllegalArgumentException);

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

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
