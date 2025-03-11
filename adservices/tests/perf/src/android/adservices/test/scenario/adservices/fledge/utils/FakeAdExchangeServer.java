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

package android.adservices.test.scenario.adservices.fledge.utils;

import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.io.BaseEncoding;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import com.squareup.okhttp.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Utility class holding methods that enable Unified Flow auction */
public class FakeAdExchangeServer {
    private static final String TAG = "AdSelectionDataE2ETest";

    private static final Gson sGson =
            new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create();

    /** Runs server auction with empty http headers */
    public static SelectAdResponse runServerAuction(
            String contextualSignalFileName,
            byte[] adSelectionData,
            String sfeAddress,
            boolean loggingEnabled)
            throws IOException {
        return runServerAuction(
                contextualSignalFileName,
                adSelectionData,
                sfeAddress,
                loggingEnabled,
                Collections.emptyMap());
    }

    /** Runs server auction */
    public static SelectAdResponse runServerAuction(
            String contextualSignalFileName,
            byte[] adSelectionData,
            String sfeAddress,
            boolean loggingEnabled,
            Map<String, String> httpHeaders)
            throws IOException {
        // Add contextual data
        SelectAdRequest selectAdRequest =
                getSelectAdRequestWithContextualSignals(contextualSignalFileName);

        if (loggingEnabled) {
            Log.d(TAG, "get ad selection data : " + BaseEncoding.base64().encode(adSelectionData));
        }

        // Because we are making a HTTPS call, we need to encode the ciphertext byte array
        selectAdRequest.setProtectedAudienceCiphertext(
                BaseEncoding.base64().encode(adSelectionData));

        return makeSelectAdsCall(selectAdRequest, sfeAddress, loggingEnabled, httpHeaders);
    }

    private static SelectAdResponse makeSelectAdsCall(
            SelectAdRequest request,
            String sfeAddress,
            boolean loggingEnabled,
            Map<String, String> httpHeaders)
            throws IOException {
        String requestPayload = getSelectAdPayload(request);
        String response = makeHttpPostCall(sfeAddress, requestPayload, loggingEnabled, httpHeaders);
        if (loggingEnabled) {
            Log.d(TAG, "Response from b&a : " + response);
        }
        return parseSelectAdResponse(response);
    }

    private static SelectAdRequest getSelectAdRequestWithContextualSignals(String fileName) {
        String jsonString = getJsonFromAssets(fileName);

        return sGson.fromJson(jsonString, SelectAdRequest.class);
    }

    private static SelectAdResponse parseSelectAdResponse(String jsonString) {
        return new GsonBuilder().create().fromJson(jsonString, SelectAdResponse.class);
    }

    private static String getSelectAdPayload(SelectAdRequest selectAdRequest) {
        return sGson.toJson(selectAdRequest);
    }

    private static String makeHttpPostCall(
            String address,
            String jsonInputString,
            boolean loggingEnabled,
            Map<String, String> httpHeaders)
            throws IOException {

        OkHttpClient client = new OkHttpClient();
        client.setProtocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1)); // Enable HTTP/2
        client.setConnectTimeout(10, TimeUnit.SECONDS);
        client.setReadTimeout(30, TimeUnit.SECONDS);
        client.setWriteTimeout(10, TimeUnit.SECONDS);

        OkUrlFactory urlFactory = new OkUrlFactory(client);
        URL url = new URL(address);
        HttpURLConnection con = urlFactory.open(url);

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        httpHeaders.forEach(con::setRequestProperty);
        con.setDoOutput(true);

        Log.d(TAG, "Call to url : " + address);
        if (loggingEnabled) {
            Log.d(TAG, "HTTP Post call made with payload : ");
            largeLog(TAG, jsonInputString);
        }

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = con.getResponseCode();
        InputStream stream;
        if (responseCode >= 200 && responseCode < 300) {
            stream = con.getInputStream();
        } else {
            stream = con.getErrorStream();
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        if (responseCode >= 200 && responseCode < 300) {
            if (loggingEnabled) {
                Log.d(TAG, "Response from server: " + response.toString());
            }
            return response.toString();
        } else {
            String errorMessage =
                    "Server call failed with status code : "
                            + responseCode
                            + " error : "
                            + con.getResponseMessage();
            if (loggingEnabled) {
                Log.d(TAG, errorMessage);
            }
            throw new IOException(errorMessage);
        }
    }

    private static void largeLog(String tag, String content) {
        if (content.length() > 4000) {
            Log.d(tag, content.substring(0, 4000));
            largeLog(tag, content.substring(4000));
        } else {
            Log.d(tag, content);
        }
    }

    private static String getJsonFromAssets(String fileName) {
        String jsonString;
        try {
            InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            jsonString = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return jsonString;
    }
}
