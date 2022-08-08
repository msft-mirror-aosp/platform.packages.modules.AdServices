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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import com.google.common.base.Charsets;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class related to network related activities
 *
 * @hide
 */
public class MeasurementHttpClient {

    enum HttpMethod {
        GET,
        POST
    }

    /**
     * Opens a {@link URLConnection} and sets the network connection & read timeout. The timeout
     * values are configurable using the name "measurement_network_connect_timeout_ms" and
     * "measurement_network_read_timeout_ms"
     */
    @NonNull
    public URLConnection setup(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);

        final URLConnection urlConnection = url.openConnection();
        final Flags flags = FlagsFactory.getFlags();
        urlConnection.setConnectTimeout(flags.getMeasurementNetworkConnectTimeoutMs());
        urlConnection.setReadTimeout(flags.getMeasurementNetworkReadTimeoutMs());

        return urlConnection;
    }

    /**
     * Rest call execution, if an error is encountered before performing the network call or an
     * {@link IOException} is thrown, an empty {@link Optional} will be returned.
     */
    @NonNull
    public Optional<MeasurementHttpResponse> call(
            @NonNull String endpoint,
            @NonNull HttpMethod httpMethod,
            @Nullable Map<String, String> headers,
            @Nullable JSONObject payload,
            boolean followRedirects) {
        if (endpoint == null || httpMethod == null) {
            LogUtil.d("Endpoint or http method is empty");
            return Optional.empty();
        }

        final URL url;
        try {
            url = new URL(endpoint);
        } catch (MalformedURLException e) {
            LogUtil.d("Malformed registration target URL %s", e);
            return Optional.empty();
        }

        final HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) setup(url);
        } catch (IOException e) {
            LogUtil.e("Failed to open target URL %s", e);
            return Optional.empty();
        }

        try {

            urlConnection.setRequestMethod(httpMethod.name());

            if (headers != null && !headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (payload != null) {
                urlConnection.setDoOutput(true);
                try (BufferedOutputStream out =
                        new BufferedOutputStream(urlConnection.getOutputStream())) {
                    out.write(payload.toString().getBytes());
                    out.flush();
                }
            }

            urlConnection.setInstanceFollowRedirects(followRedirects);

            int responseCode = urlConnection.getResponseCode();
            if (responseCode / 100 == 2) {
                return Optional.of(
                        new MeasurementHttpResponse.Builder()
                                .setPayload(convert(urlConnection.getInputStream()))
                                .setHeaders(urlConnection.getHeaderFields())
                                .setStatusCode(responseCode)
                                .build());
            } else {
                return Optional.of(
                        new MeasurementHttpResponse.Builder()
                                .setPayload(convert(urlConnection.getErrorStream()))
                                .setHeaders(urlConnection.getHeaderFields())
                                .setStatusCode(responseCode)
                                .build());
            }
        } catch (IOException e) {
            LogUtil.e("Failed to get registration response %s", e);
            return Optional.empty();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private String convert(@NonNull InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        return new String(in.readAllBytes(), Charsets.UTF_8);
    }
}
