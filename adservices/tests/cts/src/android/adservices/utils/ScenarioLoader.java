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

package android.adservices.utils;

import android.util.ArrayMap;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Objects;

public class ScenarioLoader {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String FIELD_MOCKS = "mocks";
    public static final String FIELD_REQUEST = "request";
    public static final String FIELD_RESPONSE = "response";
    public static final String FIELD_VERIFY_CALLED = "verify_called";
    public static final String FIELD_VERIFY_NOT_CALLED = "verify_not_called";
    public static final String FIELD_BODY = "body";
    public static final String FIELD_VALUE_NULL = "null";
    public static final String FIELD_BODY_STR = "body_str";
    public static final String FIELD_DELAY_SEC = "delay_sec";
    public static final String FIELD_SUBSTITUTIONS = "substitutions";

    static Scenario load(String scenarioPath, Map<String, String> substitutionVariables)
            throws JSONException, IOException {
        // Parsing paths requires server URL upfront.
        JSONObject json = new JSONObject(ScenarioLoader.loadTextResource(scenarioPath));
        Map<String, String> variables = new ArrayMap<>();
        // These variables come from two places. First, the user-configured variables supplied in
        // the JSON config.
        // Secondly, there are also variables that are specific to the address of the dispatcher,
        // such as base_url and adtech1_url. These are built-in and provided by the system, and here
        // are specifically supplied into this method.
        variables.putAll(substitutionVariables);
        variables.putAll(ScenarioLoader.parseSubstitutions(json, substitutionVariables));
        return ScenarioLoader.parseScenario(json, variables);
    }

    private static Scenario parseScenario(JSONObject json, Map<String, String> variables)
            throws JSONException {
        ImmutableMap.Builder<Scenario.Request, Scenario.MockResponse> builder =
                ImmutableMap.builder();
        JSONArray mocks = json.getJSONArray(FIELD_MOCKS);
        for (int i = 0; i < mocks.length(); i++) {
            JSONObject mockObject = mocks.getJSONObject(i);
            Pair<Scenario.Request, Scenario.MockResponse> mock = parseMock(mockObject, variables);
            builder.put(mock.first, mock.second);
        }
        return new Scenario(builder.build());
    }

    private static Pair<Scenario.Request, Scenario.MockResponse> parseMock(
            JSONObject mock, Map<String, String> variables) throws JSONException {
        if (Objects.isNull(mock)) {
            throw new IllegalArgumentException("mock JSON object is null.");
        }

        JSONObject requestJson = mock.getJSONObject(FIELD_REQUEST);
        JSONObject responseJson = mock.getJSONObject(FIELD_RESPONSE);
        if (Objects.isNull(requestJson) || Objects.isNull(responseJson)) {
            throw new IllegalArgumentException("request or response JSON object is null.");
        }

        Scenario.Request request = parseRequest(requestJson, variables);
        Scenario.MockResponse parsedMock =
                Scenario.MockResponse.newBuilder()
                        .setShouldVerifyCalled(
                                ScenarioLoader.parseBooleanOptionalOrDefault(
                                        FIELD_VERIFY_CALLED, mock))
                        .setShouldVerifyNotCalled(
                                ScenarioLoader.parseBooleanOptionalOrDefault(
                                        FIELD_VERIFY_NOT_CALLED, mock))
                        .setDefaultResponse(parseResponse(responseJson, variables).build())
                        .build();
        sLogger.v("Setting up mock at path: " + request.getRelativePath());
        return Pair.create(request, parsedMock);
    }

    private static Scenario.Request parseRequest(JSONObject json, Map<String, String> variables) {
        Scenario.Request.Builder builder = Scenario.Request.newBuilder();
        try {
            String rawPath = getStringWithSubstitutions(json.getString("path"), variables);
            if (!rawPath.startsWith("/")) {
                throw new IllegalStateException("path should start with '/' prefix: " + rawPath);
            }
            builder.setRelativePath(rawPath.substring(1));
        } catch (JSONException e) {
            throw new IllegalArgumentException("could not extract `path` from request", e);
        }

        try {
            builder.setHeaders(ScenarioLoader.parseHeaders(json.getJSONObject("header")));
        } catch (JSONException e) {
            builder.setHeaders(ImmutableMap.of());
        }

        return builder.build();
    }

    private static Scenario.Response.Builder parseResponse(
            JSONObject json, Map<String, String> variables) {
        Scenario.Response.Builder builder =
                Scenario.Response.newBuilder().setBody(Scenarios.DEFAULT_RESPONSE_BODY);
        try {
            if (json.has(FIELD_BODY)) {
                String fileName = json.getString(FIELD_BODY);
                String filePath = Scenarios.SCENARIOS_DATA_JARPATH + fileName;
                if (!fileName.equals(FIELD_VALUE_NULL)) {
                    builder.setBody(loadTextResourceWithSubstitutions(filePath, variables));
                }
            } else if (json.has(FIELD_BODY_STR)) {
                builder.setBody(json.getString(FIELD_BODY_STR));
            } else {
                throw new IllegalArgumentException(
                        "response must set `body` or `body_str`: " + json);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("could not parse response: " + json);
        }

        if (json.has("header")) {
            try {
                builder.setHeaders(ScenarioLoader.parseHeaders(json.getJSONObject("header")));
            } catch (JSONException e) {
                builder.setHeaders(ImmutableMap.of());
            }
        } else {
            builder.setHeaders(ImmutableMap.of());
        }

        builder.setDelaySeconds(
                ScenarioLoader.parseIntegerOptionalOrDefault(FIELD_DELAY_SEC, json));

        return builder;
    }

    private static ImmutableMap<String, String> parseSubstitutions(
            JSONObject json, Map<String, String> substitutionVariables) throws JSONException {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        if (!json.has(FIELD_SUBSTITUTIONS)) {
            return builder.build();
        }

        JSONObject substitutions = json.getJSONObject(FIELD_SUBSTITUTIONS);
        for (String key : substitutions.keySet()) {
            try {
                builder.put(
                        key,
                        getStringWithSubstitutions(
                                substitutions.getString(key), substitutionVariables));
            } catch (JSONException e) {
                throw new IllegalArgumentException("could not parse substitution with key: " + key);
            }
        }
        return builder.build();
    }

    private static boolean parseBooleanOptionalOrDefault(String field, JSONObject json) {
        try {
            return json.getBoolean(field);
        } catch (JSONException e) {
            return false;
        }
    }

    private static int parseIntegerOptionalOrDefault(String field, JSONObject json) {
        try {
            return json.getInt(field);
        } catch (JSONException e) {
            return 0;
        }
    }

    private static Map<String, String> parseHeaders(JSONObject json) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (String key : json.keySet()) {
            try {
                builder.put(key, json.getString(key));
            } catch (JSONException ignored) {
                throw new IllegalArgumentException("could not parse header with key: " + key);
            }
        }
        return builder.build();
    }

    private static String loadTextResource(String fileName) throws IOException {
        InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
        StringBuilder builder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                builder.append(line).append("\n");
            }
        }
        return builder.toString();
    }

    private static String getStringWithSubstitutions(String string, Map<String, String> variables) {
        // Apply substitutions to string.
        for (Map.Entry<String, String> keyValuePair : variables.entrySet()) {
            string = string.replace(keyValuePair.getKey(), keyValuePair.getValue());
        }
        return string;
    }

    private static String loadTextResourceWithSubstitutions(
            String fileName, Map<String, String> variables) {
        String responseBody;
        try {
            responseBody = ScenarioLoader.loadTextResource(fileName);
            sLogger.v("loading file: " + fileName);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to load fake response body: " + fileName, e);
        }

        if (Strings.isNullOrEmpty(responseBody)) {
            return responseBody;
        }
        for (Map.Entry<String, String> keyValuePair : variables.entrySet()) {
            responseBody = responseBody.replace(keyValuePair.getKey(), keyValuePair.getValue());
        }

        return responseBody;
    }
}
