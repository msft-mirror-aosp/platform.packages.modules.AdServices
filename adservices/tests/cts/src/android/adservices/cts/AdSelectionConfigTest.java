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

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Map;

public final class AdSelectionConfigTest extends CtsAdServicesDeviceTestCase {
    @Test
    public void testBuildValidAdSelectionConfigSuccess() {
        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap =
                SignedContextualAdsFixture.getBuyerSignedContextualAdsMap();
        AdSelectionConfig config =
                new AdSelectionConfig.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                        .setCustomAudienceBuyers(AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS)
                        .setAdSelectionSignals(AdSelectionConfigFixture.AD_SELECTION_SIGNALS)
                        .setSellerSignals(AdSelectionConfigFixture.SELLER_SIGNALS)
                        .setPerBuyerSignals(AdSelectionConfigFixture.PER_BUYER_SIGNALS)
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .setTrustedScoringSignalsUri(
                                AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI)
                        .build();

        expect.that(config.getSeller()).isEqualTo(AdSelectionConfigFixture.SELLER);
        expect.that(config.getDecisionLogicUri())
                .isEqualTo(AdSelectionConfigFixture.DECISION_LOGIC_URI);
        expect.that(config.getCustomAudienceBuyers())
                .isEqualTo(AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS);
        expect.that(config.getAdSelectionSignals())
                .isEqualTo(AdSelectionConfigFixture.AD_SELECTION_SIGNALS);
        expect.that(config.getSellerSignals()).isEqualTo(AdSelectionConfigFixture.SELLER_SIGNALS);
        expect.that(config.getPerBuyerSignals())
                .isEqualTo(AdSelectionConfigFixture.PER_BUYER_SIGNALS);
        expect.that(config.getPerBuyerSignedContextualAds()).isEqualTo(contextualAdsMap);
        expect.that(config.getTrustedScoringSignalsUri())
                .isEqualTo(AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI);
    }

    @Test
    public void testParcelValidAdDataSuccess() {
        AdSelectionConfig config =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .build();

        Parcel p = Parcel.obtain();
        config.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionConfig fromParcel = AdSelectionConfig.CREATOR.createFromParcel(p);

        expect.that(config.getSeller()).isEqualTo(fromParcel.getSeller());
        expect.that(config.getDecisionLogicUri()).isEqualTo(fromParcel.getDecisionLogicUri());
        expect.that(config.getCustomAudienceBuyers())
                .isEqualTo(fromParcel.getCustomAudienceBuyers());
        expect.that(config.getAdSelectionSignals()).isEqualTo(fromParcel.getAdSelectionSignals());
        expect.that(config.getSellerSignals()).isEqualTo(fromParcel.getSellerSignals());
        expect.that(config.getPerBuyerSignals()).isEqualTo(fromParcel.getPerBuyerSignals());
        expect.that(config.getPerBuyerSignedContextualAds())
                .isEqualTo(fromParcel.getPerBuyerSignedContextualAds());
        expect.that(config.getTrustedScoringSignalsUri())
                .isEqualTo(fromParcel.getTrustedScoringSignalsUri());
    }

    @Test
    public void testBuildMinimalAdSelectionConfigWithDefaultsSuccess() {
        AdSelectionConfig config =
                new AdSelectionConfig.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                        .setCustomAudienceBuyers(AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS)
                        .setTrustedScoringSignalsUri(
                                AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI)
                        .build();

        expect.that(config.getSeller()).isEqualTo(AdSelectionConfigFixture.SELLER);
        expect.that(config.getDecisionLogicUri())
                .isEqualTo(AdSelectionConfigFixture.DECISION_LOGIC_URI);
        expect.that(config.getCustomAudienceBuyers())
                .isEqualTo(AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS);
        expect.that(config.getTrustedScoringSignalsUri())
                .isEqualTo(AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI);

        // Populated by default with empty signals, map, and list
        expect.that(config.getAdSelectionSignals())
                .isEqualTo(AdSelectionConfigFixture.EMPTY_SIGNALS);
        expect.that(config.getSellerSignals()).isEqualTo(AdSelectionConfigFixture.EMPTY_SIGNALS);
        expect.that(config.getPerBuyerSignals()).isEmpty();
        expect.that(config.getPerBuyerSignedContextualAds()).isEmpty();
    }

    @Test
    public void testBuildAdSelectionConfigUnsetSellerFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionConfig.Builder()
                                .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                                .setCustomAudienceBuyers(
                                        AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS)
                                .build());
    }

    @Test
    public void testBuildValidAdSelectionConfigCloneSuccess() {
        AdSelectionConfig config =
                new AdSelectionConfig.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                        .setCustomAudienceBuyers(AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS)
                        .setAdSelectionSignals(AdSelectionConfigFixture.AD_SELECTION_SIGNALS)
                        .setSellerSignals(AdSelectionConfigFixture.SELLER_SIGNALS)
                        .setPerBuyerSignals(AdSelectionConfigFixture.PER_BUYER_SIGNALS)
                        .setTrustedScoringSignalsUri(
                                AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI)
                        .build();

        AdSelectionConfig cloneConfig = config.cloneToBuilder().build();

        expect.that(cloneConfig.getSeller()).isEqualTo(AdSelectionConfigFixture.SELLER);
        expect.that(cloneConfig.getDecisionLogicUri())
                .isEqualTo(AdSelectionConfigFixture.DECISION_LOGIC_URI);
        expect.that(cloneConfig.getCustomAudienceBuyers())
                .isEqualTo(AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS);
        expect.that(cloneConfig.getAdSelectionSignals())
                .isEqualTo(AdSelectionConfigFixture.AD_SELECTION_SIGNALS);
        expect.that(cloneConfig.getSellerSignals())
                .isEqualTo(AdSelectionConfigFixture.SELLER_SIGNALS);
        expect.that(cloneConfig.getPerBuyerSignals())
                .isEqualTo(AdSelectionConfigFixture.PER_BUYER_SIGNALS);
        expect.that(cloneConfig.getTrustedScoringSignalsUri())
                .isEqualTo(AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI);
    }

    @Test
    public void testBuildAdSelectionConfigUnsetDecisionLogicUriFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionConfig.Builder()
                                .setSeller(AdSelectionConfigFixture.SELLER)
                                .setCustomAudienceBuyers(
                                        AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS)
                                .build());
    }

    @Test
    public void testBuildAdSelectionConfigUnsetBuyersFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdSelectionConfig.Builder()
                                .setSeller(AdSelectionConfigFixture.SELLER)
                                .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                                .build());
    }

    @Test
    public void testAdSelectionConfigDescribeContents() {
        AdSelectionConfig obj = AdSelectionConfigFixture.anAdSelectionConfig();

        expect.that(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testEqualConfigsHaveSameHashCode() {
        AdSelectionConfig obj1 = AdSelectionConfigFixture.anAdSelectionConfig();
        AdSelectionConfig obj2 = AdSelectionConfigFixture.anAdSelectionConfig();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testNotEqualConfigsHaveDifferentHashCode() {
        AdSelectionConfig obj1 = AdSelectionConfigFixture.anAdSelectionConfig();
        AdSelectionConfig obj2 =
                AdSelectionConfigFixture.anAdSelectionConfig(AdSelectionConfigFixture.SELLER_1);
        AdSelectionConfig obj3 =
                AdSelectionConfigFixture.anAdSelectionConfig(
                        Uri.parse("https://different.uri.com"));

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj2, obj3);
        et.expectObjectsAreNotEqual(obj1, obj3);
    }

    @Test
    public void testEmptyConfigHasProperValuesSuccess() {
        AdSelectionConfig config = AdSelectionConfig.EMPTY;

        expect.that(config.getSeller()).isEqualTo(AdTechIdentifier.fromString(""));
        expect.that(config.getDecisionLogicUri()).isEqualTo(Uri.EMPTY);
        expect.that(config.getCustomAudienceBuyers()).hasSize(0);
        expect.that(config.getAdSelectionSignals()).isEqualTo(AdSelectionSignals.EMPTY);
        expect.that(config.getSellerSignals()).isEqualTo(AdSelectionSignals.EMPTY);
        expect.that(config.getPerBuyerSignals()).hasSize(0);
        expect.that(config.getTrustedScoringSignalsUri()).isEqualTo(Uri.EMPTY);
    }
}
