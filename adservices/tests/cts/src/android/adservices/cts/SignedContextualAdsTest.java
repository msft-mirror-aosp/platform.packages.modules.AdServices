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

package android.adservices.cts;

import static android.adservices.adselection.SignedContextualAdsFixture.AD_WITH_BID_1;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdTechIdentifier;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Collections;

public final class SignedContextualAdsTest extends CtsAdServicesDeviceTestCase {
    public static final byte[] TEST_SIGNATURE = new byte[] {0, 1, 2};

    @Test
    public void testBuildContextualAdsSuccess() {
        SignedContextualAds contextualAds =
                new SignedContextualAds.Builder()
                        .setBuyer(SignedContextualAdsFixture.BUYER)
                        .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                        .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                        .setSignature(TEST_SIGNATURE)
                        .build();

        expect.that(contextualAds.getBuyer()).isEqualTo(SignedContextualAdsFixture.BUYER);
        expect.that(contextualAds.getDecisionLogicUri())
                .isEqualTo(SignedContextualAdsFixture.DECISION_LOGIC_URI);
        expect.that(contextualAds.getAdsWithBid())
                .isEqualTo(SignedContextualAdsFixture.ADS_WITH_BID);
        expect.that(contextualAds.getSignature()).isEqualTo(TEST_SIGNATURE);
    }

    @Test
    public void testBuildContextualAdsBuilderSuccess() {
        AdTechIdentifier newAdTech = AdTechIdentifier.fromString("new-buyer");
        SignedContextualAds contextualAds = SignedContextualAdsFixture.aSignedContextualAds();
        expect.that(contextualAds.getBuyer()).isNotEqualTo(newAdTech);

        SignedContextualAds anotherContextualAds =
                new SignedContextualAds.Builder(contextualAds).setBuyer(newAdTech).build();
        expect.that(anotherContextualAds.getBuyer()).isEqualTo(newAdTech);
    }

    @Test
    public void testParcelValidContextualAdsSuccess() {
        SignedContextualAds contextualAds = SignedContextualAdsFixture.aSignedContextualAds();

        Parcel p = Parcel.obtain();
        contextualAds.writeToParcel(p, 0);
        p.setDataPosition(0);
        SignedContextualAds fromParcel = SignedContextualAds.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getBuyer()).isEqualTo(contextualAds.getBuyer());
        expect.that(fromParcel.getDecisionLogicUri())
                .isEqualTo(contextualAds.getDecisionLogicUri());
        expect.that(fromParcel.getAdsWithBid()).isEqualTo(contextualAds.getAdsWithBid());
        expect.that(fromParcel.getSignature()).isEqualTo(contextualAds.getSignature());
    }

    @Test
    public void testSetContextualAdsNullBuyerFailure() {
        assertThrows(
                NullPointerException.class, () -> new SignedContextualAds.Builder().setBuyer(null));
    }

    @Test
    public void testSetContextualAdsNullDecisionLogicUriFailure() {
        assertThrows(
                NullPointerException.class,
                () -> new SignedContextualAds.Builder().setDecisionLogicUri(null));
    }

    @Test
    public void testSetContextualAdsNullAdWithBidFailure() {
        assertThrows(
                NullPointerException.class,
                () -> new SignedContextualAds.Builder().setAdsWithBid(null));
    }

    @Test
    public void testSetContextualAdsNullSignatureFailure() {
        assertThrows(
                NullPointerException.class,
                () -> new SignedContextualAds.Builder().setSignature(null));
    }

    @Test
    public void testParcelNullDestFailure() {
        SignedContextualAds contextualAds = SignedContextualAdsFixture.aSignedContextualAds();
        Parcel nullDest = null;
        assertThrows(NullPointerException.class, () -> contextualAds.writeToParcel(nullDest, 0));
    }

    @Test
    public void testBuildContextualAdsUnsetBuyerFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SignedContextualAds.Builder()
                                .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                                .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                                .setSignature(TEST_SIGNATURE)
                                .build());
    }

    @Test
    public void testBuildContextualAdsUnsetDecisionLogicUriFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SignedContextualAds.Builder()
                                .setBuyer(SignedContextualAdsFixture.BUYER)
                                .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                                .setSignature(TEST_SIGNATURE)
                                .build());
    }

    @Test
    public void testBuildContextualAdsUnsetAdWithBidFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SignedContextualAds.Builder()
                                .setBuyer(SignedContextualAdsFixture.BUYER)
                                .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                                .setSignature(TEST_SIGNATURE)
                                .build());
    }

    @Test
    public void testBuildContextualAdsUnsetSignatureFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SignedContextualAds.Builder()
                                .setBuyer(SignedContextualAdsFixture.BUYER)
                                .setDecisionLogicUri(SignedContextualAdsFixture.DECISION_LOGIC_URI)
                                .setAdsWithBid(SignedContextualAdsFixture.ADS_WITH_BID)
                                .build());
    }

    @Test
    public void testContextualAdsDescribeContents() {
        SignedContextualAds obj = SignedContextualAdsFixture.aSignedContextualAds();

        assertThat(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testContextualAdsEqual() {
        SignedContextualAds obj1 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder().build();
        SignedContextualAds obj2 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder().build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testContextualAdsAreNotEqual() {
        SignedContextualAds obj1 = SignedContextualAdsFixture.aSignedContextualAds();
        SignedContextualAds obj2 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder()
                        .setBuyer(SignedContextualAdsFixture.BUYER_2)
                        .build();
        SignedContextualAds obj3 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder()
                        .setAdsWithBid(Collections.singletonList(AD_WITH_BID_1))
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj2, obj3);
        et.expectObjectsAreNotEqual(obj1, obj3);
    }

    @Test
    public void testSignedContextualAdsDescribeContents() {
        SignedContextualAds obj1 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder().build();
        assertThat(obj1.describeContents()).isEqualTo(0);
    }
}
