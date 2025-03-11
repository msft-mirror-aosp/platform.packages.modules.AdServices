/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

public final class AdSelectionResponseTest extends AdServicesUnitTestCase {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    private static final long TEST_AD_SELECTION_ID = 12345;
    private static final AdTechIdentifier WINNING_SELLER_FIRST =
            AdTechIdentifier.fromString("www.winningsellerfirst.com");
    private static final AdTechIdentifier WINNING_SELLER_SECOND =
            AdTechIdentifier.fromString("www.winningsellersecond.com");

    @Test
    public void testBuildAdSelectionResponse() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        expect.withMessage("Ad selection id")
                .that(adSelectionResponse.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render id")
                .that(adSelectionResponse.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
    }

    @Test
    public void testBuildAdSelectionResponse_withWinningSeller_buildsCorrectly() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        expect.withMessage("Ad selection id")
                .that(adSelectionResponse.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render id")
                .that(adSelectionResponse.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("Winning seller")
                .that(adSelectionResponse.getWinningSeller())
                .isEqualTo(WINNING_SELLER_FIRST);
    }

    @Test
    public void testParcelAdSelectionResponse() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        Parcel p = Parcel.obtain();
        adSelectionResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionResponse fromParcel = AdSelectionResponse.CREATOR.createFromParcel(p);

        expect.withMessage("Ad selection id")
                .that(fromParcel.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render id")
                .that(fromParcel.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
    }

    @Test
    public void testParcelAdSelectionResponse_withWinningSeller_buildsCorrectly() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        Parcel p = Parcel.obtain();
        adSelectionResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionResponse fromParcel = AdSelectionResponse.CREATOR.createFromParcel(p);

        expect.withMessage("Ad selection id")
                .that(fromParcel.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render id")
                .that(fromParcel.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("Winning seller")
                .that(fromParcel.getWinningSeller())
                .isEqualTo(WINNING_SELLER_FIRST);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdSelectionResponse.Builder()
                                // Not setting AdSelectionId making it null.
                                .setRenderUri(VALID_RENDER_URI)
                                .build());
    }

    @Test
    public void testFailsToBuildWithNullAdData() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionResponse.Builder()
                                .setAdSelectionId(TEST_AD_SELECTION_ID)
                                // Not setting AdData making it null.
                                .build());
    }

    @Test
    public void testAdSelectionResponseWithSameValuesAreEqual() {
        AdSelectionResponse obj1 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        AdSelectionResponse obj2 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testAdSelectionResponse_withWinningSeller_areEqual() {
        AdSelectionResponse obj1 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        AdSelectionResponse obj2 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testAdSelectionResponseWithDifferentValuesAreNotEqual() {
        AdSelectionResponse obj1 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        AdSelectionResponse obj2 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(
                                new Uri.Builder().path("different.url.com/testing/hello").build())
                        .build();

        AdSelectionResponse obj3 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(13579)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj1, obj3);
        et.expectObjectsAreNotEqual(obj2, obj3);
    }

    @Test
    public void testAdSelectionResponse_withDifferentWinningSeller_areNotEqual() {
        AdSelectionResponse obj1 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        AdSelectionResponse obj2 =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_SECOND)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
    }

    @Test
    public void testAdSelectionResponseDescribeContents() {
        AdSelectionResponse obj =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        expect.that(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testToString() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        assertThat(adSelectionResponse.toString())
                .isEqualTo(
                        String.format(
                                "AdSelectionResponse{mAdSelectionId=%s, mRenderUri=%s,"
                                        + " mWinningSeller=%s}",
                                TEST_AD_SELECTION_ID, VALID_RENDER_URI, ""));
    }

    @Test
    public void testToString_withWinningSeller() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        assertThat(adSelectionResponse.toString())
                .isEqualTo(
                        String.format(
                                "AdSelectionResponse{mAdSelectionId=%s, mRenderUri=%s,"
                                        + " mWinningSeller=%s}",
                                TEST_AD_SELECTION_ID, VALID_RENDER_URI, WINNING_SELLER_FIRST));
    }
}
