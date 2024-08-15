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

import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;

import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public final class ScenarioDispatcherTest {

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRule.forHttp();

    @Test
    public void testScenarioDispatcher_happyPath_httpGetSuccess() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-001.json"));

        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        URL url = new URL(baseAddress + "/bidding");
        makeSimpleGetRequest(url);

        assertThat(dispatcher.getCalledPaths()).containsExactlyElementsIn(List.of("/bidding"));
    }

    @Test
    public void testScenarioDispatcher_withPrefix_httpGetSuccess() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/scenario-test-001.json"));

        URL url = new URL(dispatcher.getBaseAddressWithPrefix() + "/bidding");
        makeSimpleGetRequest(url);

        assertThat(dispatcher.getCalledPaths()).containsExactlyElementsIn(List.of("/bidding"));
    }

    @Test
    public void testScenarioDispatcher_withVerifyCalled_success() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-002.json"));

        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/scoring"));

        assertThat(dispatcher.getCalledPaths())
                .containsExactlyElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testScenarioDispatcher_withVerifyNotCalled_success() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-003.json"));

        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding")); // Call something else.

        assertThat(dispatcher.getCalledPaths())
                .doesNotContain(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testScenarioDispatcher_withTwoSecondDelay_success() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-004.json"));

        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        long startTime = System.currentTimeMillis();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding")); // Call something else.
        long endTime = System.currentTimeMillis();

        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
        assertThat(endTime - startTime).isAtLeast(2000);
    }

    @Test
    public void testScenarioDispatcher_withMultiplePathSegments_httpGetSuccess() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-005.json"));

        makeSimpleGetRequest(new URL(dispatcher.getBaseAddressWithPrefix() + "/hello/world"));

        assertThat(dispatcher.getCalledPaths()).containsExactlyElementsIn(List.of("/hello/world"));
    }

    @Test
    public void testScenarioDispatcher_withDuplicatePathCalls_doesNotReturnEarly()
            throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-006.json"));

        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/scoring"));

        assertThat(dispatcher.getCalledPaths())
                .containsExactlyElementsIn(List.of("/bidding", "/scoring"));
    }

    // Regression test for bug b/325677040.
    @Test
    public void testScenarioDispatcher_withComplexUrlStructure_success() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-007.json"));

        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        makeSimpleGetRequest(new URL(baseAddress + "/bidding"));
        makeSimpleGetRequest(new URL(baseAddress + "/scoring"));
        makeSimpleGetRequest(new URL(baseAddress + "/seller/reportImpression"));
        makeSimpleGetRequest(
                new URL(
                        String.format(
                                "%s/seller/reportImpression?render_uri=%s/render/shirts/0?bid=5",
                                baseAddress, baseAddress)));

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(List.of("/seller/reportImpression"));
    }

    @Test
    public void testScenarioDispatcher_withQueryParamPath_success() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-008.json"));
        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        String path =
                "/seller/reportImpression?render_uri=https://localhost:38383/render/shoes/0?bid=10";

        makeSimpleGetRequest(new URL(baseAddress + path));

        assertThat(dispatcher.getCalledPaths()).containsExactly(path);
    }

    @Test
    public void testScenarioDispatcher_withQueryParamPath_doesNotFallback() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-009.json"));
        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        String path =
                "/seller/reportImpression?render_uri=https://localhost:38383/render/shoes/0?bid=10";

        // Same structure as testScenarioDispatcher_withQueryParamPath_success except adding some
        // more data into the path.
        makeSimpleGetRequest(new URL(baseAddress + path));

        assertThat(dispatcher.getCalledPaths()).doesNotContain("/seller/reportImpression");
    }

    @Test
    public void testScenarioDispatcher_withServerPathInQueryParams_success() throws Exception {
        ScenarioDispatcher dispatcher =
                mMockWebServerRule.startMockWebServer(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/scenario-test-010.json"));
        String baseAddress = dispatcher.getBaseAddressWithPrefix().toString();
        String path =
                "/seller/reportImpression?render_uri=http://localhost:"
                        + mMockWebServerRule.getMockWebServer().getPort()
                        + "/render/shoes/0?bid=10";

        makeSimpleGetRequest(new URL(baseAddress + path));

        assertThat(dispatcher.getCalledPaths()).containsExactly(path);
    }

    @Test
    public void testScenarioDispatcher_withMissingSlashPrefix_throwsException() {
        ScenarioDispatcherFactory scenarioDispatcherFactory =
                ScenarioDispatcherFactory.createFromScenarioFile(
                        "scenarios/scenario-test-011.json");

        assertThrows(
                IllegalStateException.class,
                () -> scenarioDispatcherFactory.getDispatcher(new URL("http://localhost:8080")));
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String makeSimpleGetRequest(URL url) throws Exception {
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        InputStreamReader isReader = new InputStreamReader(conn.getInputStream());
        try (BufferedReader reader = new BufferedReader(isReader)) {
            String line;
            do {
                line = reader.readLine();
                result.append(line);
            } while (line != null);
        }
        return result.toString();
    }
}
