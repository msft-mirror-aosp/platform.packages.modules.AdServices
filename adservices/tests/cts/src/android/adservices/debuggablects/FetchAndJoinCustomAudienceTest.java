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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.util.Log;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import com.google.common.truth.Truth;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

@SetFlagEnabled(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public class FetchAndJoinCustomAudienceTest extends FledgeDebuggableScenarioTest {

    /**
     * Test that custom audience can be successfully fetched from a server and joined to participate
     * in a successful ad selection (Remarketing CUJ 169).
     */
    @Test
    public void testAdSelection_withFetchCustomAudience_fetchesAndReturnsSuccessfully()
            throws Exception {
        testAdSelection_withFetchCustomAudience_Helper(mCustomAudienceClient);
    }

    /**
     * Test that custom audience can be successfully fetched from a server and joined to participate
     * in a successful ad selection (Remarketing CUJ 169) using a client built using get method
     */
    @Test
    public void
            testAdSelection_withFetchCustomAudience_fetchesAndReturnsSuccessfully_usingGetMethod()
                    throws Exception {
        testAdSelection_withFetchCustomAudience_Helper(mCustomAudienceClientUsingGetMethod);
    }

    private void testAdSelection_withFetchCustomAudience_Helper(
            AdvertisingCustomAudienceClient customAudienceClient) throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-fetchCA.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        CustomAudience customAudience = makeCustomAudience(HATS_CA).build();
        doFetchAndJoinCustomAudience(
                customAudienceClient,
                makeFetchAndJoinCustomAudienceRequest()
                        .setActivationTime(customAudience.getActivationTime())
                        .setExpirationTime(customAudience.getExpirationTime())
                        .setName(customAudience.getName())
                        .setUserBiddingSignals(customAudience.getUserBiddingSignals())
                        .build());
        Log.d(LOGCAT_TAG_FLEDGE, "Fetched and Joined Custom Audience: " + HATS_CA);
        AdSelectionOutcome result = doSelectAds(adSelectionConfig);
        assertThat(result.hasOutcome()).isTrue();
        assertThat(result.getRenderUri()).isNotNull();

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

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
