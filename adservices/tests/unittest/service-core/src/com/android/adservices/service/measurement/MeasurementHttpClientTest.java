/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement;

import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_READ_TIMEOUT_MS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;

import android.adservices.http.MockWebServerRule;
import android.net.Uri;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.SSLHandshakeException;

/** Unit tests for {@link MeasurementHttpClient} */
@SmallTest
public final class MeasurementHttpClientTest {
    private static final String KEY_CONNECT_TIMEOUT = "measurement_network_connect_timeout_ms";
    private static final String KEY_READ_TIMEOUT = "measurement_network_read_timeout_ms";
    private final MeasurementHttpClient mNetworkConnection =
            Mockito.spy(new MeasurementHttpClient());

    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    @Test
    public void testOpenAndSetupConnectionDefaultTimeoutValues_success() throws Exception {
        final URL url = new URL("https://google.com");
        final URLConnection urlConnection = mNetworkConnection.setup(url);

        Assert.assertEquals(
                MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS, urlConnection.getConnectTimeout());
        Assert.assertEquals(MEASUREMENT_NETWORK_READ_TIMEOUT_MS, urlConnection.getReadTimeout());
    }

    @Test
    public void testSetup_SetsMainlineModuleVersionAsHeader() throws IOException {
        final URL url = new URL("https://adtech.com");
        final URLConnection urlConnection = mNetworkConnection.setup(url);
        Assert.assertEquals(
                FlagsFactory.getFlagsForTest().getMainlineTrainVersion(),
                urlConnection.getRequestProperty("Version"));
    }

    @Test
    public void testSetup_headersLeakingInfoAreOverridden() throws Exception {
        final MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
        MockWebServer server = null;
        try {
            server =
                    mMockWebServerRule.startMockWebServer(
                            request -> {
                                Assert.assertNotNull(request);
                                final String userAgentHeader = request.getHeader("user-agent");
                                Assert.assertNotNull(userAgentHeader);
                                Assert.assertEquals("", userAgentHeader);
                                return new MockResponse().setResponseCode(200);
                            });

            final URL url = server.getUrl("/test");
            final HttpURLConnection urlConnection =
                    (HttpURLConnection) mNetworkConnection.setup(url);

            Assert.assertEquals(200, urlConnection.getResponseCode());
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    @Test
    public void testSetup_connectLocalhostToUntrustedServer_success() throws Exception {
        final MockWebServerRule mMockWebServerRule = createUntrustedForHttps();
        MockWebServer server = null;
        try {
            server =
                    mMockWebServerRule.startMockWebServer(
                            request -> {
                                Assert.assertNotNull(request);
                                final String userAgentHeader = request.getHeader("user-agent");
                                Assert.assertNotNull(userAgentHeader);
                                Assert.assertEquals("", userAgentHeader);
                                return new MockResponse().setResponseCode(200);
                            });

            final URL url = server.getUrl("/test");

            Assert.assertTrue(WebAddresses.isLocalhost(Uri.parse(url.toString())));

            final HttpURLConnection urlConnection =
                    (HttpURLConnection) mNetworkConnection.setup(url);

            Assert.assertEquals(200, urlConnection.getResponseCode());
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    @Test
    public void testSetup_connectNonLocalhostToUntrustedServer_throws() throws Exception {
        final MockWebServerRule mMockWebServerRule = createUntrustedForHttps();
        MockWebServer server = null;
        final MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(WebAddresses.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(false).when(() -> WebAddresses.isLocalhost(any(Uri.class)));
            server =
                    mMockWebServerRule.startMockWebServer(
                            request -> {
                                Assert.assertNotNull(request);
                                final String userAgentHeader = request.getHeader("user-agent");
                                Assert.assertNotNull(userAgentHeader);
                                Assert.assertEquals("", userAgentHeader);
                                return new MockResponse().setResponseCode(200);
                            });

            final URL url = server.getUrl("/test");

            Assert.assertFalse(WebAddresses.isLocalhost(Uri.parse(url.toString())));

            final HttpURLConnection urlConnection =
                    (HttpURLConnection) mNetworkConnection.setup(url);

            Assert.assertThrows(SSLHandshakeException.class, () -> urlConnection.getResponseCode());
        } finally {
            if (server != null) {
                server.shutdown();
            }
            mockitoSession.finishMocking();
        }
    }

    @Test
    public void testOpenAndSetupConnectionOverrideTimeoutValues_success() throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CONNECT_TIMEOUT,
                "123456",
                /* makeDefault */ false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_READ_TIMEOUT,
                "654321",
                /* makeDefault */ false);

        final URL url = new URL("https://google.com");
        final URLConnection urlConnection = mNetworkConnection.setup(url);

        Assert.assertEquals(123456, urlConnection.getConnectTimeout());
        Assert.assertEquals(654321, urlConnection.getReadTimeout());
    }

    @Test
    public void testOpenAndSetupConnectionONullUrl_failure() throws Exception {
        Assert.assertThrows(NullPointerException.class, () -> mNetworkConnection.setup(null));
    }

    @Test
    public void testOpenAndSetupConnectionOInvalidUrl_failure() throws Exception {
        Assert.assertThrows(
                MalformedURLException.class, () -> mNetworkConnection.setup(new URL("x")));
    }

    private static MockWebServerRule createUntrustedForHttps() {
        return MockWebServerRule.forHttps(
                ApplicationProvider.getApplicationContext(),
                "adservices_untrusted_test_server.p12",
                "adservices_test");
    }
}
