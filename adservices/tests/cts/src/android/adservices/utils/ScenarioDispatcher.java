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

package android.adservices.utils;

import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LoggerFactory;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.util.Strings;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class ScenarioDispatcher extends Dispatcher {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String DEFAULT_RESPONSE_BODY = "200 OK";

    private final Map<Request, Response> mRequestToResponseMap;
    private final String mPrefix;
    private final ImmutableSet.Builder<String> mCalledPaths;

    /**
     * Setup the dispatcher for a given scenario.
     *
     * @param scenarioPath path to scenario json within the JAR resources folder.
     * @param prefix path prefix to prepend to all URLs.
     */
    public static ScenarioDispatcher fromScenario(String scenarioPath, String prefix)
            throws Exception {
        return new ScenarioDispatcher(scenarioPath, prefix);
    }

    /**
     * Get all paths of calls to this server.
     *
     * @return String list of called paths.
     */
    public ImmutableSet<String> getCalledPaths() {
        return mCalledPaths.build();
    }

    /**
     * Get all paths of calls to this server that were expected.
     *
     * <p>These are defined by the `verify_called` and `verify_not_called` fields in the test
     * scenario JSON files.
     *
     * @return String list of paths.
     */
    public ImmutableSet<String> getVerifyCalledPaths() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        mRequestToResponseMap.forEach(
                (s, response) -> {
                    if (response.getVerifyCalled()) {
                        builder.add("/" + s.getPath());
                    }
                });
        return builder.build();
    }

    private ScenarioDispatcher(String scenarioPath, String prefix) throws Exception {
        mPrefix = prefix;
        sLogger.v(String.format("Setting up scenario with file: %s", scenarioPath));

        JSONObject json = new JSONObject(loadTextResource(scenarioPath));

        mRequestToResponseMap = parseMocks(json);
        mCalledPaths = new ImmutableSet.Builder<>();
    }

    private static Map<Request, Response> parseMocks(JSONObject json) throws Exception {
        ImmutableMap.Builder<Request, Response> builder = ImmutableMap.builder();

        JSONArray mocks = json.getJSONArray("mocks");
        for (int i = 0; i < mocks.length(); i++) {
            try {
                JSONObject mockObject = mocks.getJSONObject(i);
                Pair<Request, Response> mock = parseMock(mockObject);
                builder.put(mock.first, mock.second);
            } catch (Exception e) {
                continue;
            }
        }
        return builder.build();
    }

    private static Pair<Request, Response> parseMock(JSONObject mock) throws JSONException {
        if (Objects.isNull(mock)) {
            throw new IllegalArgumentException("mock JSON object is null.");
        }

        JSONObject requestJson = mock.getJSONObject("request");
        JSONObject responseJson = mock.getJSONObject("response");
        if (Objects.isNull(requestJson) || Objects.isNull(responseJson)) {
            throw new IllegalArgumentException("request or response JSON object is null.");
        }

        boolean verifyCalled;
        try {
            verifyCalled = mock.getBoolean("verify_called");
        } catch (JSONException e) {
            verifyCalled = false;
        }

        Request request = parseRequest(requestJson);
        Response response = parseResponse(responseJson).setVerifyCalled(verifyCalled).build();
        return Pair.create(request, response);
    }

    private static Request parseRequest(JSONObject requestObject) {
        String requestPath = null;
        try {
            requestPath = requestObject.getString("path");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Request.newBuilder().setPath(requestPath).build();
    }

    private static Response.Builder parseResponse(JSONObject responseObject) {
        String responseBodyPath;
        try {
            responseBodyPath = responseObject.getString("body");
        } catch (Exception e) {
            responseBodyPath = null;
        }

        String responseBody = null;
        if (!Strings.isNullOrEmpty(responseBodyPath)) {
            try {
                responseBody = loadTextResource("scenarios/data/" + responseBodyPath);
            } catch (IOException e) {
                responseBody = DEFAULT_RESPONSE_BODY;
            }
        }

        return Response.newBuilder().setBody(responseBody);
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        String requestWithoutPrefix = request.getPath().replace(mPrefix, "");
        mCalledPaths.add(requestWithoutPrefix);
        sLogger.v("Serving path at %s", requestWithoutPrefix);

        for (Request mockRequest : mRequestToResponseMap.keySet()) {
            String responseBody = mRequestToResponseMap.get(mockRequest).getBody();
            if (isMatchingPath(request.getPath(), mockRequest.getPath())) {
                return new MockResponse().setBody(responseBody).setResponseCode(200);
            }
        }
        return new MockResponse().setResponseCode(404);
    }

    @AutoValue
    abstract static class Request {
        abstract String getPath();

        static Request.Builder newBuilder() {
            return new AutoValue_ScenarioDispatcher_Request.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            abstract Builder setPath(String path);

            abstract Request build();
        }
    }

    @AutoValue
    abstract static class Response {
        abstract String getBody();

        abstract boolean getVerifyCalled();

        static Response.Builder newBuilder() {
            return new AutoValue_ScenarioDispatcher_Response.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            abstract Builder setBody(String body);

            abstract Builder setVerifyCalled(boolean verifyCalled);

            abstract Response build();
        }
    }

    private boolean isMatchingPath(String requestPath, String potentialResponsePath) {
        String pathWithPrefix = mPrefix + "/" + potentialResponsePath;
        String requestPathWithoutQuery = requestPath.split("\\?")[0];
        return requestPathWithoutQuery.equals(pathWithPrefix);
    }

    private static String loadTextResource(String fileName) throws IOException {
        InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
        is.reset();
        byte[] bytes = new byte[is.available()];
        DataInputStream dataInputStream = new DataInputStream(is);
        dataInputStream.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
