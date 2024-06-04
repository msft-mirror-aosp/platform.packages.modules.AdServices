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

import static android.adservices.utils.Scenarios.FAKE_ADDRESS_1;
import static android.adservices.utils.Scenarios.FAKE_ADDRESS_2;
import static android.adservices.utils.Scenarios.TIMEOUT_SEC;

import com.android.adservices.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.testng.util.Strings;

import java.io.IOException;
import java.net.MalformedURLException;
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

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String X_FLEDGE_BUYER_BIDDING_LOGIC_VERSION =
            "x_fledge_buyer_bidding_logic_version";

    private final ImmutableMap<Scenario.Request, Scenario.MockResponse> mRequestToMockMap;
    private final String mPrefix;
    private final ImmutableMap<String, String> mSubstitutionVariables;

    // The value is not used and we always insert a fixed value of 1.
    private final ConcurrentHashMap<String, Integer> mCalledPaths;

    private final CountDownLatch mUniqueCallCount;
    private final URL mServerBaseURL;

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
        mRequestToMockMap.forEach(
                (s, mock) -> {
                    if (mock.getShouldVerifyCalled()) {
                        builder.add("/" + s.getRelativePath());
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
        mRequestToMockMap.forEach(
                (s, mock) -> {
                    if (mock.getShouldVerifyNotCalled()) {
                        builder.add("/" + s.getRelativePath());
                    }
                });
        return builder.build();
    }

    /**
     * Return the base URL for the server.
     *
     * @return base URL for the server.
     */
    public URL getBaseAddressWithPrefix() {
        try {
            return new URL(mServerBaseURL + mPrefix);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
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

    ScenarioDispatcher(String scenarioPath, String prefix, URL serverBaseAddress)
            throws JSONException, IOException {
        mPrefix = prefix;
        sLogger.v(String.format("Setting up scenario with file: %s", scenarioPath));
        mCalledPaths = new ConcurrentHashMap<>();
        mServerBaseURL = serverBaseAddress;
        // Needs HTTPS for real tests and HTTP for ScenarioDispatcher tests.
        mSubstitutionVariables =
                ImmutableMap.of(
                        "{base_url_with_prefix}",
                        getBaseAddressWithPrefix().toString(),
                        "{adtech1_url}",
                        getBaseAddressWithPrefix().toString(),
                        "{adtech2_url}",
                        FAKE_ADDRESS_1 + mPrefix,
                        "{adtech3_url}",
                        FAKE_ADDRESS_2 + mPrefix);
        mRequestToMockMap =
                ScenarioLoader.load(scenarioPath, mSubstitutionVariables).getScenarioMap();
        mUniqueCallCount = new CountDownLatch(mRequestToMockMap.size());
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        boolean hasSetBaseAddress = mServerBaseURL != null;
        if (!hasSetBaseAddress) {
            throw new IllegalStateException(
                    "Cannot serve request as setServerBaseAddress() has not been called.");
        }

        String path = pathWithoutPrefix(request.getPath());

        for (Scenario.Request mockRequest : mRequestToMockMap.keySet()) {
            String mockPath = mockRequest.getRelativePath();
            if (isMatchingPath(request.getPath(), mockPath)) {
                Scenario.MockResponse mock = mRequestToMockMap.get(mockRequest);
                Scenario.Response mockResponse = Objects.requireNonNull(mock).getDefaultResponse();
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
                MockResponse response = new MockResponse().setBody(body).setResponseCode(200);
                for (Map.Entry<String, String> mockHeader : mockResponse.getHeaders().entrySet()) {
                    sLogger.v(
                            "Adding header %s with value %s",
                            mockHeader.getKey(), mockHeader.getValue());
                    response.addHeader(mockHeader.getKey(), mockHeader.getValue());
                }
                sLogger.v("serving path at %s with response %s", path, response.toString());
                return response;
            }
        }

        // For any requests that weren't specifically overloaded with query params to be handled,
        // always strip them when adding them to the log.
        // This behaviour matches the existing test server functionality.
        recordCalledPath("/" + pathWithoutQueryParams(path));
        sLogger.v("serving path at %s (404)", path);
        return new MockResponse().setResponseCode(404);
    }


    private synchronized void recordCalledPath(String path) {
        if (mCalledPaths.containsKey(path)) {
            sLogger.v(
                    "Not recording path called at %s as already hit, latch count is %d/%d.",
                    path, mUniqueCallCount.getCount(), mRequestToMockMap.size());
        } else {
            mCalledPaths.put(path, 1);
            mUniqueCallCount.countDown();
            sLogger.v(
                    "Recorded path called at %s, latch count is %d/%d.",
                    path, mUniqueCallCount.getCount(), mRequestToMockMap.size());
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
}
