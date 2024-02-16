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

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.PPAPI_AND_SYSTEM_SERVER;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.utils.CustomAudienceTestFixture;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.common.annotations.SetFlagEnabled;
import com.android.adservices.common.annotations.SetIntegerFlag;
import com.android.compatibility.common.util.ShellUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = PPAPI_AND_SYSTEM_SERVER)
@SetFlagEnabled(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED)
@RequiresSdkLevelAtLeastT
public final class CustomAudienceShellCommandsE2ETest extends ForegroundDebuggableCtsTest {
    private static final String OWNER = "android.adservices.debuggablects";
    private CustomAudience mShirtsCustomAudience;
    private CustomAudience mShoesCustomAudience;
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private CustomAudienceTestFixture mCustomAudienceTestFixture;

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(sContext);
        assertForegroundActivityStarted();

        mCustomAudienceTestFixture = new CustomAudienceTestFixture(sContext);
        mShirtsCustomAudience =
                mCustomAudienceTestFixture.createCustomAudience(
                        "shirts",
                        BUYER,
                        List.of(1D),
                        null,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME);
        mShoesCustomAudience =
                mCustomAudienceTestFixture.createCustomAudience(
                        "shoes",
                        BUYER,
                        List.of(1D),
                        null,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    @After
    public void tearDown() throws Exception {
        mCustomAudienceTestFixture.leaveJoinedCustomAudiences();
    }

    @Test
    public void testRun_listCustomAudience_happyPath() throws Exception {
        mCustomAudienceTestFixture.joinCustomAudience(mShirtsCustomAudience);
        mCustomAudienceTestFixture.joinCustomAudience(mShoesCustomAudience);

        JSONArray customAudiences =
                runAndParseShellCommand(
                                "cmd adservices_manager %s %s --owner %s --buyer %s",
                                "custom-audience", "list", OWNER, BUYER.toString())
                        .getJSONArray("custom_audiences");
        mCustomAudienceTestFixture.leaveCustomAudience(mShirtsCustomAudience);
        JSONArray customAudiencesAfterLeaving =
                runAndParseShellCommand(
                                "cmd adservices_manager %s %s --owner %s --buyer %s",
                                "custom-audience", "list", OWNER, BUYER.toString())
                        .getJSONArray("custom_audiences");

        assertThat(
                        List.of(
                                CustomAudienceHelper.fromJson(customAudiences.getJSONObject(0)),
                                CustomAudienceHelper.fromJson(customAudiences.getJSONObject(1))))
                .containsExactly(mShirtsCustomAudience, mShoesCustomAudience);
        assertThat(
                        List.of(
                                CustomAudienceHelper.fromJson(
                                        customAudiencesAfterLeaving.getJSONObject(0))))
                .containsExactly(mShoesCustomAudience);
    }

    @Test
    public void testRun_viewCustomAudience_happyPath() throws Exception {
        mCustomAudienceTestFixture.joinCustomAudience(mShirtsCustomAudience);

        JSONObject customAudience =
                runAndParseShellCommand(
                        "cmd adservices_manager %s %s --owner %s --buyer %s --name %s",
                        "custom-audience",
                        "view",
                        OWNER,
                        BUYER.toString(),
                        mShirtsCustomAudience.getName());

        CustomAudience parsedCustomAudience = CustomAudienceHelper.fromJson(customAudience);
        assertThat(mShirtsCustomAudience).isEqualTo(parsedCustomAudience);
    }

    private static JSONObject runAndParseShellCommand(String template, String... commandArgs)
            throws JSONException {
        return new JSONObject(ShellUtils.runShellCommand(template, (Object[]) commandArgs));
    }

    private static final class CustomAudienceHelper {
        private static final String NAME = "name";
        private static final String BUYER = "buyer";
        private static final String ACTIVATION_TIME = "activation_time";
        private static final String EXPIRATION_TIME = "expiration_time";
        private static final String BIDDING_LOGIC_URI = "bidding_logic_uri";
        private static final String USER_BIDDING_SIGNALS = "user_bidding_signals";
        private static final String TRUSTED_BIDDING_DATA = "trusted_bidding_data";
        private static final String DAILY_UPDATE = "daily_update";
        private static final String DAILY_UPDATE_URI = "uri";
        private static final String ADS = "ads";
        private static final String ADS_URI = "uri";
        private static final String ADS_KEYS = "keys";
        private static final String ADS_AD_COUNTER_KEYS = "ad_counter_keys";
        private static final String ADS_AD_FILTERS = "ad_filters";
        private static final String AD_AD_RENDER_URI = "render_uri";
        private static final String AD_METADATA = "metadata";
        private static final String AD_AD_RENDER_ID = "ad_render_id";
        private static final String ELIGIBLE_UPDATE_TIME = "eligible_update_time";
        private static final String NUM_VALIDATION_FAILURES = "num_validation_failures";
        private static final String NUM_TIMEOUT_FAILURES = "num_timeout_failures";

        static CustomAudience fromJson(@NonNull JSONObject jsonObject) throws JSONException {
            verifyActivationTime(jsonObject); // This is here as activation time is inconsistent.
            verifyBackgroundFetchData(jsonObject.getJSONObject(DAILY_UPDATE));
            return new CustomAudience.Builder()
                    .setName(jsonObject.getString(NAME))
                    .setBuyer(AdTechIdentifier.fromString(jsonObject.getString(BUYER)))
                    .setExpirationTime(Instant.parse(jsonObject.getString(EXPIRATION_TIME)))
                    .setBiddingLogicUri(Uri.parse(jsonObject.getString(BIDDING_LOGIC_URI)))
                    .setTrustedBiddingData(
                            getTrustedBiddingDataFromJson(
                                    jsonObject.getJSONObject(TRUSTED_BIDDING_DATA)))
                    .setUserBiddingSignals(
                            AdSelectionSignals.fromString(
                                    jsonObject.getString(USER_BIDDING_SIGNALS)))
                    .setAds(getAdsFromJsonArray(jsonObject.getJSONArray(ADS)))
                    .setDailyUpdateUri(
                            Uri.parse(
                                    jsonObject
                                            .getJSONObject(DAILY_UPDATE)
                                            .getString(DAILY_UPDATE_URI)))
                    .build();
        }

        private static void verifyActivationTime(JSONObject customAudience) throws JSONException {
            Instant.parse(customAudience.getString(ACTIVATION_TIME));
        }

        // Background fetch data is not part of the public API except as exposed via CLI commands,
        // therefore specific assertions cannot be made as to the expected state beyond expecting a
        // certain format (valid date and integers).
        private static void verifyBackgroundFetchData(JSONObject customAudienceBackgroundFetchData)
                throws JSONException {
            Instant.parse(customAudienceBackgroundFetchData.getString(ELIGIBLE_UPDATE_TIME));
            assertThat(customAudienceBackgroundFetchData.getInt(NUM_TIMEOUT_FAILURES)).isEqualTo(0);
            assertThat(customAudienceBackgroundFetchData.getInt(NUM_VALIDATION_FAILURES))
                    .isEqualTo(0);
        }

        private static TrustedBiddingData getTrustedBiddingDataFromJson(JSONObject jsonObject)
                throws JSONException {
            return new TrustedBiddingData.Builder()
                    .setTrustedBiddingUri(Uri.parse(jsonObject.getString(ADS_URI)))
                    .setTrustedBiddingKeys(
                            getStringsFromJsonArray(jsonObject.getJSONArray(ADS_KEYS)))
                    .build();
        }

        private static ImmutableList<String> getStringsFromJsonArray(JSONArray jsonArray)
                throws JSONException {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            for (int i = 0; i < jsonArray.length(); i++) {
                builder.add(jsonArray.getString(i));
            }
            return builder.build();
        }

        private static ImmutableList<AdData> getAdsFromJsonArray(JSONArray jsonArray)
                throws JSONException {
            ImmutableList.Builder<AdData> builder = ImmutableList.builder();
            for (int i = 0; i < jsonArray.length(); i++) {
                builder.add(getAdFromJson(jsonArray.getJSONObject(i)));
            }
            return builder.build();
        }

        private static AdData getAdFromJson(JSONObject jsonObject) throws JSONException {
            AdData.Builder builder =
                    new AdData.Builder()
                            .setRenderUri(Uri.parse(jsonObject.getString(AD_AD_RENDER_URI)))
                            .setMetadata(jsonObject.getString(AD_METADATA));
            if (jsonObject.has(AD_AD_RENDER_ID)) {
                builder.setAdRenderId(jsonObject.getString(AD_AD_RENDER_ID));
            }
            if (jsonObject.has(ADS_AD_COUNTER_KEYS)) {
                builder.setAdCounterKeys(
                        getIntegersFromJsonArray(jsonObject.getJSONArray(ADS_AD_COUNTER_KEYS)));
            }
            if (jsonObject.has(ADS_AD_FILTERS)) {
                builder.setAdFilters(
                        AdFilters.fromJson(new JSONObject(jsonObject.getString(ADS_AD_FILTERS))));
            }
            return builder.build();
        }

        private static ImmutableSet<Integer> getIntegersFromJsonArray(@NonNull JSONArray jsonArray)
                throws JSONException {
            ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
            for (int i = 0; i < jsonArray.length(); i++) {
                builder.add(jsonArray.getInt(i));
            }
            return builder.build();
        }
    }
}
