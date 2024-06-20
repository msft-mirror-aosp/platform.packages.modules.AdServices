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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * The Scenario class represents a configuration for a mock HTTP server used in scenario-based
 * testing. It encapsulates a mapping of requests to corresponding mock responses, along with
 * options for verification.
 *
 * <p>You should ideally not construct this class directly, instead use {@link ScenarioLoader} and
 * {@link ScenarioDispatcherFactory} to load scenarios from JSON.
 */
public class Scenario {

    private final ImmutableMap<Request, MockResponse> mScenarioMap;

    /**
     * Constructs a new Scenario instance.
     *
     * @param scenarioMap An immutable map containing Request-Mock pairs, defining the scenario's
     *     behavior.
     */
    Scenario(ImmutableMap<Request, MockResponse> scenarioMap) {
        this.mScenarioMap = scenarioMap;
    }

    /**
     * Returns the immutable map defining the request-response associations for this scenario.
     *
     * @return An ImmutableMap containing the scenario's configuration.
     */
    public ImmutableMap<Request, MockResponse> getScenarioMap() {
        return mScenarioMap;
    }

    /** Represents a mock HTTP request within a scenario. */
    @AutoValue
    public abstract static class Request {
        /**
         * Returns the relative path of the HTTP request (e.g., "/api/data")
         *
         * @return the relative path as a String
         */
        abstract String getRelativePath();

        /**
         * Returns an immutable map of HTTP headers associated with the request.
         *
         * @return an ImmutableMap of headers
         */
        abstract ImmutableMap<String, String> getHeaders();

        /**
         * Returns a new Builder instance for constructing Request objects.
         *
         * @return a Request.Builder instance
         */
        static Builder newBuilder() {
            return new AutoValue_Scenario_Request.Builder();
        }

        /** Builder class for creating Request instances. */
        @AutoValue.Builder
        public abstract static class Builder {
            /**
             * Sets the relative path of the request.
             *
             * @param path the relative path to set
             * @return the Builder instance for chaining
             */
            abstract Builder setRelativePath(String path);

            /**
             * Sets the HTTP headers for the request.
             *
             * @param headers an ImmutableMap containing headers
             * @return the Builder instance for chaining
             */
            abstract Builder setHeaders(Map<String, String> headers);

            /**
             * Builds a new Request instance based on the configured values.
             *
             * @return a Request instance
             */
            abstract Request build();
        }
    }

    /** Represents a mock HTTP response within a scenario. */
    @AutoValue
    public abstract static class MockResponse {
        /**
         * Returns the default response to be used for matching requests.
         *
         * @return a Response object representing the default response
         */
        abstract Response getDefaultResponse();

        /**
         * Indicates whether a call to this mock should be verified during testing.
         *
         * @return true if the call should be verified, false otherwise
         */
        abstract boolean getShouldVerifyCalled();

        /**
         * Indicates whether a call to this mock should *not* be verified during testing.
         *
         * @return true if absence of a call should be verified, false otherwise
         */
        abstract boolean getShouldVerifyNotCalled();

        /**
         * Returns a new Builder instance for constructing Mock objects.
         *
         * @return a Mock.Builder instance
         */
        static Builder newBuilder() {
            return new AutoValue_Scenario_MockResponse.Builder();
        }

        /** Builder class for creating {@link MockResponse} instances. */
        @AutoValue.Builder
        public abstract static class Builder {

            /**
             * Sets the default response that the mock should return when a matching request is
             * received.
             *
             * @param response The {@link Response} object representing the default response.
             * @return This builder instance for method chaining.
             */
            abstract Builder setDefaultResponse(Response response);

            /**
             * t Sets a flag indicating whether calls to this mock should be verified during
             * testing. This typically means ensuring the mock was invoked during the test scenario.
             *
             * @param verifyCalled true if calls to this mock should be verified, false otherwise.
             * @return This builder instance for method chaining.
             */
            abstract Builder setShouldVerifyCalled(boolean verifyCalled);

            /**
             * Sets a flag indicating whether the *absence* of calls to this mock should be verified
             * during testing. This is useful when you want to ensure certain interactions with the
             * mock HTTP server did *not* occur.
             *
             * @param verifyNotCalled true if the lack of calls to the mock should be verified,
             *     false otherwise.
             * @return This builder instance for method chaining.
             */
            abstract Builder setShouldVerifyNotCalled(boolean verifyNotCalled);

            /**
             * Builds a new {@link MockResponse} instance using the values configured in this
             * builder.
             *
             * @return A newly constructed Mock object.
             */
            abstract MockResponse build();
        }
    }

    /**
     * Represents a mock HTTP response within a scenario. This class encapsulates the response body,
     * headers, and an optional delay to simulate network latency.
     */
    @AutoValue
    public abstract static class Response {

        /**
         * Returns the body content of the HTTP response.
         *
         * @return The response body as a String.
         */
        abstract String getBody();

        /**
         * Returns an immutable map of HTTP headers associated with the response.
         *
         * @return An immutable map of headers.
         */
        abstract ImmutableMap<String, String> getHeaders();

        /**
         * Returns the delay in seconds to be applied to the response, simulating network latency.
         *
         * @return The delay in seconds.
         */
        abstract int getDelaySeconds();

        /**
         * Returns a new Builder instance for constructing Mock objects.
         *
         * @return a Response.Builder instance
         */
        static Response.Builder newBuilder() {
            return new AutoValue_Scenario_Response.Builder();
        }

        /**
         * Builder class for creating {@link Response} instances. Use this class to fluently
         * configure the response body, headers, and delay for your mock HTTP server scenarios.
         */
        @AutoValue.Builder
        public abstract static class Builder {

            /**
             * Sets the response body content.
             *
             * @param body The response body as a String.
             * @return This builder instance for method chaining.
             */
            abstract Builder setBody(String body);

            /**
             * Sets the HTTP headers for the response.
             *
             * @param headers An immutable map containing headers.
             * @return This builder instance for method chaining.
             */
            abstract Builder setHeaders(Map<String, String> headers);

            /**
             * Sets an artificial delay (in seconds) for the response to simulate network
             * conditions.
             *
             * @param delay The delay in seconds.
             * @return This builder instance for method chaining.
             */
            abstract Builder setDelaySeconds(int delay);

            /**
             * Builds a new {@link Response} instance using the configured values.
             *
             * @return A newly constructed Response object.
             */
            abstract Response build();
        }
    }
}
