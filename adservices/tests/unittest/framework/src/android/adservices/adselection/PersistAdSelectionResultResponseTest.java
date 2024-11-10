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

package android.adservices.adselection;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

public final class PersistAdSelectionResultResponseTest extends AdServicesUnitTestCase {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    private static final Uri ANOTHER_VALID_RENDER_URI =
            new Uri.Builder().path("another-valid.example.com/testing/hello").build();
    private static final long TEST_AD_SELECTION_ID = 12345;
    private static final long ANOTHER_TEST_AD_SELECTION_ID = 6789;
    private static final AdTechIdentifier WINNING_SELLER_FIRST =
            AdTechIdentifier.fromString("www.winningseller.com");
    private static final AdTechIdentifier WINNING_SELLER_SECOND =
            AdTechIdentifier.fromString("www.secondwillingseller.com");

    @Test
    public void testBuildPersistAdSelectionResultResponse() {
        PersistAdSelectionResultResponse persistAdSelectionResultResponse =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        expect.withMessage("Ad selection id")
                .that(persistAdSelectionResultResponse.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render uri")
                .that(persistAdSelectionResultResponse.getAdRenderUri())
                .isEqualTo(VALID_RENDER_URI);
    }

    @Test
    public void testBuildPersistAdSelectionResultResponse_withWinningSeller_buildsCorrectly() {
        PersistAdSelectionResultResponse persistAdSelectionResultResponse =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        expect.withMessage("Ad selection id")
                .that(persistAdSelectionResultResponse.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render uri")
                .that(persistAdSelectionResultResponse.getAdRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("Winning seller")
                .that(persistAdSelectionResultResponse.getWinningSeller())
                .isEqualTo(WINNING_SELLER_FIRST);
    }

    @Test
    public void testParcelPersistAdSelectionResultResponse() {
        PersistAdSelectionResultResponse persistAdSelectionResultResponse =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        Parcel p = Parcel.obtain();
        persistAdSelectionResultResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        PersistAdSelectionResultResponse fromParcel =
                PersistAdSelectionResultResponse.CREATOR.createFromParcel(p);

        expect.withMessage("Ad selection id")
                .that(fromParcel.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render uri")
                .that(fromParcel.getAdRenderUri())
                .isEqualTo(VALID_RENDER_URI);
    }

    @Test
    public void testParcelPersistAdSelectionResultResponse_withWinningSeller_buildsCorrectly() {
        PersistAdSelectionResultResponse persistAdSelectionResultResponse =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        Parcel p = Parcel.obtain();
        persistAdSelectionResultResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        PersistAdSelectionResultResponse fromParcel =
                PersistAdSelectionResultResponse.CREATOR.createFromParcel(p);

        expect.withMessage("Ad selection id")
                .that(fromParcel.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render uri")
                .that(fromParcel.getAdRenderUri())
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
                        new PersistAdSelectionResultResponse.Builder()
                                // Not setting AdSelectionId making it null.
                                .setAdRenderUri(VALID_RENDER_URI)
                                .build());
    }

    @Test
    public void testPersistAdSelectionResultResponseWithSameValuesAreEqual() {
        PersistAdSelectionResultResponse obj1 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        PersistAdSelectionResultResponse obj2 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testPersistAdSelectionResultResponse_withWinningSellerWithSameValues_areEqual() {
        PersistAdSelectionResultResponse obj1 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        PersistAdSelectionResultResponse obj2 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testPersistAdSelectionResultResponseWithDifferentValuesAreNotEqual() {
        PersistAdSelectionResultResponse obj1 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        PersistAdSelectionResultResponse obj2 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(ANOTHER_VALID_RENDER_URI)
                        .build();

        PersistAdSelectionResultResponse obj3 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(ANOTHER_TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj1, obj3);
        et.expectObjectsAreNotEqual(obj2, obj3);
    }

    @Test
    public void testPersistAdSelectionResultResponseDescribeContents() {
        PersistAdSelectionResultResponse obj =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        expect.that(obj.describeContents()).isEqualTo(0);
    }
}
