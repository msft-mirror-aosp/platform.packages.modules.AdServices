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
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for running test scenarios.
 *
 * <p>The scenario files are stored in assets/data/scenarios/*.json as well as any supporting files
 * in the data folder. Each scenario defines a set of request / response pairings which are used to
 * configure a {@link MockWebServer} instance. This class is thread-local safe.
 */
public class ScenarioDispatcher extends Dispatcher {

    private static final String FAKE_ADDRESS_1 = "https://localhost:38384";
    private static final String FAKE_ADDRESS_2 = "https://localhost:38385";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String DEFAULT_RESPONSE_BODY = "200 OK";
    public static final String SCENARIOS_DATA_JARPATH = "scenarios/data/";
    public static final String X_FLEDGE_BUYER_BIDDING_LOGIC_VERSION =
            "x_fledge_buyer_bidding_logic_version";
    public static final int TIMEOUT_SEC = 8;

    private ImmutableMap<Request, Response> mRequestToResponseMap;
    private final String mPrefix;
    private ImmutableMap<String, String> mSubstitutionMap;
    private ImmutableMap<String, String> mSubstitutionVariables;

    // The value is not used and we always insert a fixed value of 1.
    private final ConcurrentHashMap<String, Integer> mCalledPaths;

    private CountDownLatch mUniqueCallCount;
    private URL mServerBaseURL;
    private final String mScenarioPath;

    /**
     * Setup the dispatcher for a given scenario.
     *
     * @param scenarioPath path to scenario json within the JAR resources folder.
     * @param prefix       path prefix to prepend to all URLs.
     */
    public static ScenarioDispatcher fromScenario(String scenarioPath, String prefix)
            throws Exception {
        return new ScenarioDispatcher(scenarioPath, prefix);
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

    /**
     * Get all paths of calls to this server that were NOT expected.
     *
     * @return String list of paths.
     */
    public ImmutableSet<String> getVerifyNotCalledPaths() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        mRequestToResponseMap.forEach(
                (s, response) -> {
                    if (response.getVerifyNotCalled()) {
                        builder.add("/" + s.getPath());
                    }
                });
        return builder.build();
    }

    /**
     * Get all paths of calls to this server that were expected.
     *
     * <p>These are defined by the `verify_called` and `verify_not_called` fields in the test
     * scenario JSON files.
     *
     * @return String list of paths.
     */
    public ImmutableSet<String> getCalledPaths() throws InterruptedException {
        sLogger.w("getCalledPaths() called");
        if (!mUniqueCallCount.await(TIMEOUT_SEC, TimeUnit.SECONDS)) {
            sLogger.w("Timeout reached in getCalledPaths()");
        }
        sLogger.w("getCalledPaths() returning with  size: %s", mCalledPaths.size());
        return ImmutableSet.copyOf(mCalledPaths.keySet());
    }

    private ScenarioDispatcher(String scenarioPath, String prefix) {
        mPrefix = prefix;
        sLogger.v(String.format("Setting up scenario with file: %s", scenarioPath));
        mCalledPaths = new ConcurrentHashMap<>();
        mScenarioPath = scenarioPath;
    }

    private static ImmutableMap<String, String> parseSubstitutions(JSONObject json) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        JSONObject substitutions;
        try {
            substitutions = json.getJSONObject("substitutions");
        } catch (JSONException e) {
            return builder.build();
        }

        for (String key : substitutions.keySet()) {
            try {
                builder.put(key, substitutions.getString(key));
            } catch (JSONException e) {
                continue;
            }
        }
        return builder.build();
    }

    private ImmutableMap<Request, Response> parseMocks(JSONObject json) throws JSONException {
        ImmutableMap.Builder<Request, Response> builder = ImmutableMap.builder();
        JSONArray mocks = json.getJSONArray("mocks");
        for (int i = 0; i < mocks.length(); i++) {
            JSONObject mockObject = mocks.getJSONObject(i);
            Pair<Request, Response> mock = parseMock(mockObject);
            builder.put(mock.first, mock.second);
        }
        return builder.build();
    }

    private Pair<Request, Response> parseMock(JSONObject mock) throws JSONException {
        if (Objects.isNull(mock)) {
            throw new IllegalArgumentException("mock JSON object is null.");
        }

        JSONObject requestJson = mock.getJSONObject("request");
        JSONObject responseJson = mock.getJSONObject("response");
        if (Objects.isNull(requestJson) || Objects.isNull(responseJson)) {
            throw new IllegalArgumentException("request or response JSON object is null.");
        }

        Request request = parseRequest(requestJson);
        Response response =
                parseResponse(responseJson)
                        .setVerifyCalled(getBooleanOptional("verify_called", mock))
                        .setVerifyNotCalled(getBooleanOptional("verify_not_called", mock))
                        .build();
        sLogger.v("Setting up mock at path: " + request.getPath());
        return Pair.create(request, response);
    }

    private static boolean getBooleanOptional(String field, JSONObject json) {
        try {
            return json.getBoolean(field);
        } catch (JSONException e) {
            return false;
        }
    }

    private static int getIntegerOptional(String field, JSONObject json) {
        try {
            return json.getInt(field);
        } catch (JSONException e) {
            return 0;
        }
    }

    private Request parseRequest(JSONObject json) {
        Request.Builder builder = Request.newBuilder();
        try {
            builder.setPath(getStringWithSubstitutions(json.getString("path")));
        } catch (JSONException e) {
            throw new IllegalArgumentException("could not extract `path` from request", e);
        }

        try {
            builder.setHeaders(parseHeaders(json.getJSONObject("header")));
        } catch (JSONException e) {
            builder.setHeaders(ImmutableMap.of());
        }

        return builder.build();
    }

    private static Map<String, String> parseHeaders(JSONObject json) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (String key : json.keySet()) {
            try {
                builder.put(key, json.getString(key));
            } catch (JSONException ignored) {
                // continue.
            }
        }
        return builder.build();
    }

    private Response.Builder parseResponse(JSONObject responseObject) {
        Response.Builder builder = Response.newBuilder().setBody(DEFAULT_RESPONSE_BODY);
        try {
            String fileName = responseObject.getString("body");
            String filePath = SCENARIOS_DATA_JARPATH + fileName;
            if (!fileName.equals("null")) {
                builder.setBody(loadTextResourceWithSubstitutions(filePath));
            }
        } catch (JSONException e) {
            // continue.
        }

        try {
            builder.setHeaders(parseHeaders(responseObject.getJSONObject("header")));
        } catch (JSONException e) {
            builder.setHeaders(ImmutableMap.of());
        }

        builder.setDelaySeconds(getIntegerOptional("delay_sec", responseObject));

        return builder;
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        boolean hasSetBaseAddress = mServerBaseURL != null;
        if (!hasSetBaseAddress) {
            throw new IllegalStateException(
                    "Cannot serve request as setServerBaseAddress() has not been called.");
        }

        String path = pathWithoutPrefix(request.getPath());

        for (Request mockRequest : mRequestToResponseMap.keySet()) {
            String mockPath = mockRequest.getPath();
            if (isMatchingPath(request.getPath(), mockPath)) {
                Response mockResponse = mRequestToResponseMap.get(mockRequest);
                String body = mockResponse.getBody();
                for (Map.Entry<String, String> keyValuePair : mSubstitutionVariables.entrySet()) {
                    body = body.replace(keyValuePair.getKey(), keyValuePair.getValue());
                }

                // Sleep if necessary. Will default to 0 if not provided.
                Thread.sleep(mockResponse.getDelaySeconds() * 1000L);

                // If the mock path specifically has query params, then add that, otherwise strip
                // them before adding them to the log.
                // This behaviour matches the existing test server functionality.
                recordCalledPath(
                        String.format(
                                "/%s",
                                hasQueryParams(mockPath)
                                        ? mockPath
                                        : pathWithoutQueryParams(path)));
                sLogger.v("serving path at %s (200)", path);

                return maybeApplyFledgeV3Header(
                        new MockResponse().setBody(body).setResponseCode(200),
                        request,
                        mockRequest,
                        mockResponse);
            }
        }

        // For any requests that weren't specifically overloaded with query params to be handled,
        // always strip them when adding them to the log.
        // This behaviour matches the existing test server functionality.
        recordCalledPath("/" + pathWithoutQueryParams(path));
        sLogger.v("serving path at %s (404)", path);
        return new MockResponse().setResponseCode(404);
    }

    private String getStringWithSubstitutions(String string) {
        // Apply substitutions to string.
        for (Map.Entry<String, String> keyValuePair : mSubstitutionVariables.entrySet()) {
            string = string.replace(keyValuePair.getKey(), keyValuePair.getValue());
        }
        return string;
    }

    private synchronized void recordCalledPath(String path) {
        if (mCalledPaths.containsKey(path)) {
            sLogger.v(
                    "Not recording path called at %s as already hit, latch count is %d/%d.",
                    path, mUniqueCallCount.getCount(), mRequestToResponseMap.size());
        } else {
            mCalledPaths.put(path, 1);
            mUniqueCallCount.countDown();
            sLogger.v(
                    "Recorded path called at %s, latch count is %d/%d.",
                    path, mUniqueCallCount.getCount(), mRequestToResponseMap.size());
        }
    }

    private MockResponse maybeApplyFledgeV3Header(
            MockResponse response, RecordedRequest request, Request mockRequest,
            Response mockResponse) {
        if (!Strings.isNullOrEmpty(request.getHeader(X_FLEDGE_BUYER_BIDDING_LOGIC_VERSION))
                && mockRequest.getHeaders().containsKey(X_FLEDGE_BUYER_BIDDING_LOGIC_VERSION)) {
            sLogger.v("Setting FLEDGE bidding logic header.");
            return response.setHeader(
                    X_FLEDGE_BUYER_BIDDING_LOGIC_VERSION,
                    mockResponse.getHeaders().get(X_FLEDGE_BUYER_BIDDING_LOGIC_VERSION));
        } else {
            sLogger.v("Not setting FLEDGE V3 bidding logic header.");
            return response;
        }
    }

    /**
     * Set the server base address.
     *
     * <p>This method must be called for the {@link MockWebServer} to be properly used for scenario
     * testing, as it populates the variable substitutions (e.g. base_url) to be available to the
     * URL templates.
     *
     * @param serverBaseURL address to map.
     */
    // TODO(b/333403102): Improve the interface for this class by removing this method.
    public void setServerBaseURL(URL serverBaseURL) throws JSONException, IOException {
        mServerBaseURL = serverBaseURL;
        // Needs HTTPS for real tests and HTTP for ScenarioDispatcher tests.
        String host = mServerBaseURL.getProtocol() + "://localhost:" + mServerBaseURL.getPort();
        mSubstitutionVariables =
                ImmutableMap.of(
                        "{base_url}", host + mPrefix,
                        "{base_host}", host,
                        "{adtech2_url}", FAKE_ADDRESS_1 + mPrefix,
                        "{adtech3_url}", FAKE_ADDRESS_2 + mPrefix);
        // Parsing paths requires server URL upfront.
        JSONObject json = new JSONObject(loadTextResource(mScenarioPath));
        mSubstitutionMap = parseSubstitutions(json);
        mRequestToResponseMap = parseMocks(json);
        mUniqueCallCount = new CountDownLatch(mRequestToResponseMap.size());
    }

    @AutoValue
    abstract static class Request {
        abstract String getPath();

        abstract ImmutableMap<String, String> getHeaders();

        static Builder newBuilder() {
            return new AutoValue_ScenarioDispatcher_Request.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            abstract Builder setPath(String path);

            abstract Builder setHeaders(Map<String, String> headers);

            abstract Request build();
        }
    }

    @AutoValue
    abstract static class Response {
        abstract String getBody();

        abstract boolean getVerifyCalled();

        abstract boolean getVerifyNotCalled();

        abstract ImmutableMap<String, String> getHeaders();

        abstract int getDelaySeconds();

        static Builder newBuilder() {
            return new AutoValue_ScenarioDispatcher_Response.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            abstract Builder setBody(String body);

            abstract Builder setVerifyCalled(boolean verifyCalled);

            abstract Builder setVerifyNotCalled(boolean verifyNotCalled);

            abstract Builder setHeaders(Map<String, String> headers);

            abstract Builder setDelaySeconds(int delay);

            abstract Response build();
        }
    }

    private boolean isMatchingPath(String path, String mockPath) {
        return pathWithoutQueryParams(pathWithoutPrefix(path)).equals(mockPath)
                || pathWithoutPrefix(path).equals(mockPath);
    }

    private boolean hasQueryParams(String path) {
        return path.contains("?");
    }

    private String pathWithoutQueryParams(String path) {
        return path.split("\\?")[0];
    }

    private String pathWithoutPrefix(String path) {
        if (Strings.isNullOrEmpty(mPrefix)) {
            // Only remove the first redundant "/" if no prefix is explicitly defined.
            return path.substring(1);
        }
        return path.replaceFirst(mPrefix + "/", "");
    }

    private String loadTextResourceWithSubstitutions(String fileName) {
        String responseBody = null;
        if (!Strings.isNullOrEmpty(fileName)) {
            try {
                responseBody = loadTextResource(fileName);
                sLogger.v("loading file: " + fileName);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "failed to load fake response body: " + fileName, e);
            }
        }

        if (!Strings.isNullOrEmpty(responseBody)) {
            for (Map.Entry<String, String> keyValuePair : mSubstitutionMap.entrySet()) {
                responseBody = responseBody.replace(keyValuePair.getKey(), keyValuePair.getValue());
            }
        }

        return responseBody;
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
}
