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

package android.adservices.cts;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.net.Uri;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

public final class FetchAndJoinCustomAudienceRequestTest extends CtsAdServicesDeviceTestCase {
    public static final Uri VALID_FETCH_URI_1 =
            CustomAudienceFixture.getValidFetchUriByBuyer(CommonFixture.VALID_BUYER_1, "1");

    @Test
    public void testBuildValidRequest_all_success() {
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        expect.that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.that(request.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(request.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(request.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(request.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
    }

    @Test
    public void testBuildValidRequest_onlyFetchUri_success() {
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1).build();

        expect.that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
    }

    @Test
    public void testBuildValidRequest_withName_success() {
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .build();

        expect.that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.that(request.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
    }

    @Test
    public void testBuildValidRequest_withActivationTime_success() {
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .build();

        expect.that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.that(request.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
    }

    @Test
    public void testBuildValidRequest_withExpirationTime_success() {
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .build();

        expect.that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.that(request.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    @Test
    public void testBuildValidRequest_withUserBiddingSignals_success() {
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        expect.that(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        expect.that(request.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
    }

    @Test
    public void testBuildValidRequest_withFetchUri_success() {
        Uri overrideFetchUri =
                CustomAudienceFixture.getValidFetchUriByBuyer(
                        CommonFixture.VALID_BUYER_1, /* token= */ "2");
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setFetchUri(overrideFetchUri)
                        .build();

        expect.that(request.getFetchUri()).isEqualTo(overrideFetchUri);
    }

    @Test
    public void testBuildNullFetchUri_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new FetchAndJoinCustomAudienceRequest.Builder(null)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .build());
    }

    @Test
    public void testEquals_identical() {
        FetchAndJoinCustomAudienceRequest request1 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        FetchAndJoinCustomAudienceRequest request2 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(request1, request2);

        // Different object
        FetchAndJoinCustomAudienceRequest request3 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_OWNER)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();
        et.expectObjectsAreNotEqual(request1, request3);

        // Equality with null
        et.expectObjectsAreNotEqual(request1, null);
    }

    @Test
    public void testToString() {
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        String expected =
                String.format(
                        "FetchAndJoinCustomAudienceRequest{fetchUri=%s, name=%s,"
                                + " activationTime=%s, expirationTime=%s, userBiddingSignals=%s}",
                        VALID_FETCH_URI_1,
                        CustomAudienceFixture.VALID_NAME,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        expect.that(request.toString()).isEqualTo(expected);
    }
}
