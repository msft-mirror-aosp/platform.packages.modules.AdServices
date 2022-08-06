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

import android.adservices.http.MockWebServerRule;
import android.provider.DeviceConfig;

import androidx.test.filters.SmallTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.mockwebserver.MockResponse;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Unit tests for {@link MeasurementHttpClient} */
@SmallTest
public final class MeasurementHttpClientTest {
    private static final String KEY_CONNECT_TIMEOUT = "measurement_network_connect_timeout_ms";
    private static final String KEY_READ_TIMEOUT = "measurement_network_read_timeout_ms";
    private static final String MOCK_PATH = "/mock";
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

    @Test
    public void testCall_noEndpoint_returnEmptyOptional() throws Exception {
        final MockWebServerRule serverRule = MockWebServerRuleFactory.createForHttps();
        serverRule.startMockWebServer(List.of(new MockResponse()));
        Optional<MeasurementHttpResponse> optionalResponse =
                mNetworkConnection.call(
                        null,
                        MeasurementHttpClient.HttpMethod.POST,
                        /* headers = */ null,
                        /* payload = */ null,
                        /* followRedirects = */ false);

        Assert.assertFalse(optionalResponse.isPresent());
    }

    @Test
    public void testCall_noHttpMethod_returnEmptyOptional() throws Exception {
        final MockWebServerRule serverRule = MockWebServerRuleFactory.createForHttps();
        serverRule.startMockWebServer(List.of(new MockResponse()));
        Optional<MeasurementHttpResponse> optionalResponse =
                mNetworkConnection.call(
                        serverRule.uriForPath(MOCK_PATH).toString(),
                        /* httpMethod = */ null,
                        /* headers = */ null,
                        /* payload = */ null,
                        /* followRedirects = */ false);

        Assert.assertFalse(optionalResponse.isPresent());
    }

    @Test
    public void testCall_malformedUrl_returnEmptyOptional() throws Exception {
        final MockWebServerRule serverRule = MockWebServerRuleFactory.createForHttps();
        serverRule.startMockWebServer(List.of(new MockResponse()));
        Optional<MeasurementHttpResponse> optionalResponse =
                mNetworkConnection.call(
                        "",
                        MeasurementHttpClient.HttpMethod.POST,
                        /* headers = */ null,
                        /* payload = */ null,
                        /* followRedirects = */ false);

        Assert.assertFalse(optionalResponse.isPresent());
    }

    @Test
    public void testCall_unableToOpenConnection_returnEmptyOptional() throws Exception {
        final MockWebServerRule serverRule = MockWebServerRuleFactory.createForHttps();
        serverRule.startMockWebServer(List.of(new MockResponse()));
        Mockito.doThrow(new IOException()).when(mNetworkConnection).setup(ArgumentMatchers.any());

        Optional<MeasurementHttpResponse> optionalResponse =
                mNetworkConnection.call(
                        serverRule.uriForPath(MOCK_PATH).toString(),
                        MeasurementHttpClient.HttpMethod.POST,
                        /* headers = */ null,
                        /* payload = */ null,
                        /* followRedirects = */ false);

        Mockito.verify(mNetworkConnection, Mockito.times(1)).setup(ArgumentMatchers.any());
        Assert.assertFalse(optionalResponse.isPresent());
    }

    @Test
    public void testCall_callSuccessful() throws Exception {
        final JSONObject object = new JSONObject();
        object.put("payload_foo", "payoad_bar");

        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody(object.toString());
        mockResponse.setHeader("header_foo", "header_bar");
        mockResponse.setResponseCode(200);

        final MockWebServerRule serverRule = MockWebServerRuleFactory.createForHttps();
        serverRule.startMockWebServer(List.of(mockResponse));

        Optional<MeasurementHttpResponse> optionalResponse =
                mNetworkConnection.call(
                        serverRule.uriForPath(MOCK_PATH).toString(),
                        MeasurementHttpClient.HttpMethod.POST,
                        /* headers = */ null,
                        /* payload = */ null,
                        /* followRedirects = */ false);

        Assert.assertTrue(optionalResponse.isPresent());

        MeasurementHttpResponse response = optionalResponse.get();
        Assert.assertEquals(200, response.getStatusCode());
        Assert.assertEquals(object.toString(), response.getPayload());
        Assert.assertTrue(response.getHeaders().containsKey("header_foo"));
        Assert.assertEquals("header_bar", response.getHeaders().get("header_foo").get(0));
    }

    @Test
    public void testCall_callFailure() throws Exception {
        final JSONObject object = new JSONObject();
        object.put("error_message", "invalid parameter");

        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody(object.toString());
        mockResponse.setResponseCode(400);

        final MockWebServerRule serverRule = MockWebServerRuleFactory.createForHttps();
        serverRule.startMockWebServer(List.of(mockResponse));

        Optional<MeasurementHttpResponse> optionalResponse =
                mNetworkConnection.call(
                        serverRule.uriForPath(MOCK_PATH).toString(),
                        MeasurementHttpClient.HttpMethod.POST,
                        /* headers = */ null,
                        /* payload = */ null,
                        /* followRedirects = */ false);

        Assert.assertTrue(optionalResponse.isPresent());

        MeasurementHttpResponse response = optionalResponse.get();
        Assert.assertEquals(400, response.getStatusCode());
        Assert.assertEquals(object.toString(), response.getPayload());
    }

    @Test
    public void testCall_callSuccessful_testParameters() throws Exception {
        final MockWebServerRule serverRule = MockWebServerRuleFactory.createForHttps();
        serverRule.startMockWebServer(List.of(new MockResponse()));
        HttpURLConnection mockUrlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.doReturn(mockUrlConnection).when(mNetworkConnection).setup(ArgumentMatchers.any());
        Mockito.doReturn(new ByteArrayOutputStream()).when(mockUrlConnection).getOutputStream();

        Optional<MeasurementHttpResponse> optionalResponse =
                mNetworkConnection.call(
                        serverRule.uriForPath(MOCK_PATH).toString(),
                        MeasurementHttpClient.HttpMethod.POST,
                        /* headers = */ Map.of("k1", "v1", "k2", "v2"),
                        /* payload = */ new JSONObject(),
                        /* followRedirects = */ true);

        Assert.assertTrue(optionalResponse.isPresent());
        Mockito.verify(mockUrlConnection, Mockito.times(1)).setRequestProperty("k1", "v1");
        Mockito.verify(mockUrlConnection, Mockito.times(1)).setRequestProperty("k2", "v2");
        Mockito.verify(mockUrlConnection, Mockito.times(1)).setDoOutput(true);
        Mockito.verify(mockUrlConnection, Mockito.times(1)).getOutputStream();
        Mockito.verify(mockUrlConnection, Mockito.times(1)).setInstanceFollowRedirects(true);
    }
}
