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

package android.adservices.customaudience;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

public class FetchAndJoinCustomAudienceInputTest extends AdServicesUnitTestCase {
    public static final Uri VALID_FETCH_URI_1 =
            CustomAudienceFixture.getValidFetchUriByBuyer(CommonFixture.VALID_BUYER_1, "1");

    @Test
    public void testBuildValidRequest_all_success() {
        FetchAndJoinCustomAudienceInput request =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        expect.withMessage("fetch uri").that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.withMessage("name")
                .that(request.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.withMessage("activation time")
                .that(request.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.withMessage("expiration time")
                .that(request.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.withMessage("bidding signals")
                .that(request.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.withMessage("caller package")
                .that(request.getCallerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
    }

    @Test
    public void testBuildValidRequest_onlyFetchUriAndCallerPackageName_success() {
        FetchAndJoinCustomAudienceInput request =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .build();

        expect.withMessage("fetch uri").that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.withMessage("caller package")
                .that(request.getCallerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
    }

    @Test
    public void testBuildValidRequest_withName_success() {
        FetchAndJoinCustomAudienceInput request =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .build();

        expect.withMessage("fetch uri").that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.withMessage("name")
                .that(request.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.withMessage("caller package")
                .that(request.getCallerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
    }

    @Test
    public void testBuildValidRequest_withActivationTime_success() {
        FetchAndJoinCustomAudienceInput request =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .build();

        expect.withMessage("fetch uri").that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.withMessage("activation time")
                .that(request.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.withMessage("caller package")
                .that(request.getCallerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
    }

    @Test
    public void testBuildValidRequest_withExpirationTime_success() {
        FetchAndJoinCustomAudienceInput request =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .build();

        expect.withMessage("fetch uri").that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.withMessage("expiration time")
                .that(request.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.withMessage("caller package")
                .that(request.getCallerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
    }

    @Test
    public void testBuildValidRequest_withUserBiddingSignals_success() {
        FetchAndJoinCustomAudienceInput request =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        expect.withMessage("fetch uri").that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.withMessage("bidding signals")
                .that(request.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.withMessage("caller package")
                .that(request.getCallerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
    }

    @Test
    public void testBuildNullFetchUri_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new FetchAndJoinCustomAudienceInput.Builder(
                                        null, CustomAudienceFixture.VALID_OWNER)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .build());
    }

    @Test
    public void testBuildNullCallerPackageName_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new FetchAndJoinCustomAudienceInput.Builder(VALID_FETCH_URI_1, null)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .build());
    }

    @Test
    public void testEquals_identical() {
        EqualsTester et = new EqualsTester(expect);

        FetchAndJoinCustomAudienceInput request1 =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        FetchAndJoinCustomAudienceInput request2 =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        et.expectObjectsAreEqual(request1, request2);
    }

    @Test
    public void testEquals_different() {
        EqualsTester et = new EqualsTester(expect);

        FetchAndJoinCustomAudienceInput request1 =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        FetchAndJoinCustomAudienceInput request2 =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME + "123")
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    @Test
    public void testEquals_null() {
        FetchAndJoinCustomAudienceInput request1 =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        assertThat(request1).isNotNull();
    }

    @Test
    public void testToString() {
        FetchAndJoinCustomAudienceInput request =
                new FetchAndJoinCustomAudienceInput.Builder(
                                VALID_FETCH_URI_1, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        String expected =
                String.format(
                        "FetchAndJoinCustomAudienceInput{fetchUri=%s, name=%s,"
                                + " activationTime=%s, expirationTime=%s, userBiddingSignals=%s, "
                                + "callerPackageName=%s}",
                        VALID_FETCH_URI_1,
                        CustomAudienceFixture.VALID_NAME,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                        CustomAudienceFixture.VALID_OWNER);

        assertThat(request.toString()).isEqualTo(expected);
    }
}
