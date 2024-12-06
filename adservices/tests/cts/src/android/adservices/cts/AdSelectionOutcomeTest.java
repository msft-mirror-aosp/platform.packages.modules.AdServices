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

package android.adservices.cts;

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Test;

import java.util.List;

@RequiresSdkLevelAtLeastS
public final class AdSelectionOutcomeTest extends CtsAdServicesDeviceTestCase {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    private static final Uri VALID_COMPONENT_AD_RENDER_URI_1 =
            new Uri.Builder().path("valid.example.com/testing/hello/component/1").build();
    private static final Uri VALID_COMPONENT_AD_RENDER_URI_2 =
            new Uri.Builder().path("valid.example.com/testing/hello/component/2").build();
    private static final List<Uri> AD_COMPONENT_URIS =
            List.of(VALID_COMPONENT_AD_RENDER_URI_1, VALID_COMPONENT_AD_RENDER_URI_2);
    private static final int TEST_AD_SELECTION_ID = 12345;
    private static final AdTechIdentifier WINNING_SELLER_FIRST =
            AdTechIdentifier.fromString("www.winningsellerid.com");
    private static final AdTechIdentifier WINNING_SELLER_SECOND =
            AdTechIdentifier.fromString("www.secondwillingsellerid.com");

    @Test
    public void testBuildAdSelectionOutcome() {
        AdSelectionOutcome adSelectionOutcome =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        expect.withMessage("Ad selection id")
                .that(adSelectionOutcome.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render uri")
                .that(adSelectionOutcome.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("Winning seller")
                .that(adSelectionOutcome.getWinningSeller())
                .isEqualTo(AdTechIdentifier.UNSET_AD_TECH_IDENTIFIER);
        expect.withMessage("Component ad uri")
                .that(adSelectionOutcome.getComponentAdUris())
                .isEmpty();
    }

    @Test
    public void testBuildAdSelectionOutcome_withWinningSeller_buildsCorrectly() {
        AdSelectionOutcome adSelectionOutcome =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        expect.withMessage("Ad selection id")
                .that(adSelectionOutcome.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render uri")
                .that(adSelectionOutcome.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("Winning seller")
                .that(adSelectionOutcome.getWinningSeller())
                .isEqualTo(WINNING_SELLER_FIRST);
        expect.withMessage("Component ad uri")
                .that(adSelectionOutcome.getComponentAdUris())
                .isEmpty();
    }

    @Test
    public void testBuildAdSelectionOutcomeWithAdComponentUrisSucceeds() {
        AdSelectionOutcome adSelectionOutcome =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setComponentAdUris(AD_COMPONENT_URIS)
                        .build();

        expect.withMessage("Ad selection id")
                .that(adSelectionOutcome.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        expect.withMessage("Ad render uri")
                .that(adSelectionOutcome.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("Winning seller")
                .that(adSelectionOutcome.getWinningSeller())
                .isEqualTo(AdTechIdentifier.UNSET_AD_TECH_IDENTIFIER);
        expect.withMessage("Component ad uri")
                .that(adSelectionOutcome.getComponentAdUris())
                .isEqualTo(AD_COMPONENT_URIS);
    }

    @Test
    public void testBuildAdSelectionOutcomeChecksIfOutcomeIsEmpty() {
        AdSelectionOutcome notEmptyOutcome =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();
        AdSelectionOutcome emptyOutcome = AdSelectionOutcome.NO_OUTCOME;

        expect.withMessage("Non empty outcome").that(notEmptyOutcome.hasOutcome()).isTrue();
        expect.withMessage("Empty outcome").that(emptyOutcome.hasOutcome()).isFalse();
    }

    @Test
    public void testAdSelectionOutcomeWithSameValuesAreEqual() {
        AdSelectionOutcome obj1 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        AdSelectionOutcome obj2 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        AdSelectionOutcome obj3 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(
                                new Uri.Builder().path("different.url.com/testing/hello").build())
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj1, obj3);
    }

    @Test
    public void testAdSelectionOutcomeWithComponentAdsSameValuesAreEqual() {
        AdSelectionOutcome obj1 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setComponentAdUris(AD_COMPONENT_URIS)
                        .build();

        AdSelectionOutcome obj2 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setComponentAdUris(AD_COMPONENT_URIS)
                        .build();

        AdSelectionOutcome obj3 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(
                                new Uri.Builder().path("different.url.com/testing/hello").build())
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj1, obj3);
    }

    @Test
    public void testAdSelectionOutcome_withWinningSellerAndSameValues_areEqual() {
        AdSelectionOutcome obj1 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        AdSelectionOutcome obj2 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_FIRST)
                        .build();

        AdSelectionOutcome obj3 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .setWinningSeller(WINNING_SELLER_SECOND)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj1, obj3);
    }
}
