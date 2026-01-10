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

package android.adservices.debuggablects;

import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;

import static org.junit.Assert.assertThrows;

import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.utils.ScenarioDispatcherFactory;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import com.google.common.truth.Truth;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

@SetFlagEnabled(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public class FetchAndJoinCustomAudienceTest extends FledgeDebuggableScenarioTest {

    @Test
    public void testFetchAndJoinCustomAudience_validFetchUri_validRequest() throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-default.json"));

        FetchAndJoinCustomAudienceRequest request = makeFetchAndJoinCustomAudienceRequest().build();

        testFetchAndJoinCustomAudience_validRequest_helper(request);
    }

    @Test
    public void testFetchAndJoinCustomAudience_validName_validRequest() throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-default.json"));

        FetchAndJoinCustomAudienceRequest request =
                makeFetchAndJoinCustomAudienceRequest().setName(HATS_CA).build();

        testFetchAndJoinCustomAudience_validRequest_helper(request);
    }

    @Test
    public void testFetchAndJoinCustomAudience_validUserBiddingSignals_validRequest()
            throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-default.json"));

        FetchAndJoinCustomAudienceRequest request =
                makeFetchAndJoinCustomAudienceRequest()
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();

        testFetchAndJoinCustomAudience_validRequest_helper(request);
    }

    @Test
    public void testFetchAndJoinCustomAudience_validActivationTime_validRequest() throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-default.json"));

        FetchAndJoinCustomAudienceRequest request =
                makeFetchAndJoinCustomAudienceRequest()
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .build();

        testFetchAndJoinCustomAudience_validRequest_helper(request);
    }

    @Test
    public void testFetchAndJoinCustomAudience_validExpirationTime_validRequest() throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-default.json"));

        FetchAndJoinCustomAudienceRequest request =
                makeFetchAndJoinCustomAudienceRequest()
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .build();

        testFetchAndJoinCustomAudience_validRequest_helper(request);
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B, value = 1)
    public void testFetchAndJoinCustomAudience_tooLongName_invalidRequest() throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-fetchCA.json"));
        FetchAndJoinCustomAudienceRequest request = makeFetchAndJoinCustomAudienceRequest().build();

        testFetchAndJoinCustomAudience_inValidRequest_helper(request);
    }

    @Test
    public void testFetchAndJoinCustomAudience_activationExceedsDelay_invalidRequest()
            throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-fetchCA.json"));
        FetchAndJoinCustomAudienceRequest request =
                makeFetchAndJoinCustomAudienceRequest()
                        .setActivationTime(INVALID_DELAYED_ACTIVATION_TIME)
                        .build();

        testFetchAndJoinCustomAudience_inValidRequest_helper(request);
    }

    @Test
    public void testFetchAndJoinCustomAudience_beyondMaxExpiration_invalidRequest()
            throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-fetchCA.json"));
        FetchAndJoinCustomAudienceRequest request =
                makeFetchAndJoinCustomAudienceRequest()
                        .setExpirationTime(INVALID_BEYOND_MAX_EXPIRATION_TIME)
                        .build();

        testFetchAndJoinCustomAudience_inValidRequest_helper(request);
    }

    @Test
    @SetIntegerFlag(
            name = KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
            value = 1)
    public void testFetchAndJoinCustomAudience_tooBigUserBiddingSignals_invalidRequest()
            throws Exception {
        setupDispatcher(
                ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                        "scenarios/remarketing-cuj-fetchCA.json"));
        FetchAndJoinCustomAudienceRequest request =
                makeFetchAndJoinCustomAudienceRequest()
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();

        testFetchAndJoinCustomAudience_inValidRequest_helper(request);
    }

    private void testFetchAndJoinCustomAudience_validRequest_helper(
            FetchAndJoinCustomAudienceRequest request) {
        Exception exception =
                assertThrows(ExecutionException.class, () -> doFetchAndJoinCustomAudience(request));
        Truth.assertWithMessage("Expected IllegalStateException")
                .that(exception)
                .hasCauseThat()
                .isInstanceOf(IllegalStateException.class);
    }

    private void testFetchAndJoinCustomAudience_inValidRequest_helper(
            FetchAndJoinCustomAudienceRequest request) {
        Exception exception =
                assertThrows(ExecutionException.class, () -> doFetchAndJoinCustomAudience(request));
        Truth.assertWithMessage("Expected IllegalArgumentException")
                .that(exception)
                .hasCauseThat()
                .isInstanceOf(IllegalArgumentException.class);
    }
}
