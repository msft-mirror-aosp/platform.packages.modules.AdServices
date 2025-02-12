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

package com.android.adservices.service.customaudience;

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.WINNER_TYPE_CA_WINNER;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.adservices.common.ComponentAdData;
import android.adservices.common.ComponentAdDataFixture;
import android.adservices.common.DBComponentAdDataFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBComponentAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ComponentAdsStrategyTest extends AdServicesMockitoTestCase {
    private static final List<ComponentAdData> COMPONENT_AD_DATA_LIST =
            ComponentAdDataFixture.getValidComponentAdsByBuyer(VALID_BUYER_1);

    private static final BiddingAuctionServers.AuctionResult AUCTION_RESULT_WITH_COMPONENT_ADS =
            BiddingAuctionServers.AuctionResult.newBuilder()
                    .addAllAdComponentRenderUrls(List.of("renderUri1", "renderUri2", "renderUri3"))
                    .setCustomAudienceOwner(VALID_OWNER)
                    .setBuyer(VALID_BUYER_1.toString())
                    .setCustomAudienceName(VALID_NAME)
                    .build();

    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private ComponentAdsListValidator mComponentAdsListValidatorMock;

    private ComponentAdsStrategy mComponentAdsStrategyEnabled;
    private ComponentAdsStrategy mComponentAdsStrategyDisabled;

    @Before
    public void setup() {
        mComponentAdsStrategyEnabled =
                ComponentAdsStrategy.createInstance(
                        /* componentAdsEnabled= */ true, mComponentAdsListValidatorMock);
        mComponentAdsStrategyDisabled =
                ComponentAdsStrategy.createInstance(
                        /* componentAdsEnabled= */ false, mComponentAdsListValidatorMock);
    }

    @Test
    public void testEnabledStrategyPersistCustomAudiencesWithComponentAdsAddsComponentAds() {
        DBCustomAudience customAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(VALID_BUYER_1).build();
        Uri dailyUpdateUri = Uri.parse("https://example.com");

        boolean debuggable = true;

        mComponentAdsStrategyEnabled.persistCustomAudiencesWithComponentAds(
                mCustomAudienceDaoMock,
                customAudience,
                dailyUpdateUri,
                debuggable,
                COMPONENT_AD_DATA_LIST);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        customAudience, dailyUpdateUri, debuggable, COMPONENT_AD_DATA_LIST);
    }

    @Test
    public void testDisabledStrategyPersistCustomAudiencesWithComponentAdsOnlyAddsCA() {
        DBCustomAudience customAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(VALID_BUYER_1).build();
        Uri dailyUpdateUri = Uri.parse("https://example.com");

        boolean debuggable = true;

        mComponentAdsStrategyDisabled.persistCustomAudiencesWithComponentAds(
                mCustomAudienceDaoMock,
                customAudience,
                dailyUpdateUri,
                debuggable,
                COMPONENT_AD_DATA_LIST);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        customAudience, dailyUpdateUri, debuggable, List.of());
    }

    @Test
    public void testEnabledStrategyIncrementNumCustomAudiencesWithComponentAds() {
        BuyerInputGeneratorIntermediateStats stats = new BuyerInputGeneratorIntermediateStats();
        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(0);
        mComponentAdsStrategyEnabled.incrementNumCustomAudiencesWithComponentAds(stats);
        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(1);
        mComponentAdsStrategyEnabled.incrementNumCustomAudiencesWithComponentAds(stats);
        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(2);
    }

    @Test
    public void testDisabledStrategyNoIncrementNumCustomAudiencesWithComponentAds() {
        BuyerInputGeneratorIntermediateStats stats = new BuyerInputGeneratorIntermediateStats();
        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(0);
        mComponentAdsStrategyDisabled.incrementNumCustomAudiencesWithComponentAds(stats);
        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(0);
        mComponentAdsStrategyDisabled.incrementNumCustomAudiencesWithComponentAds(stats);
        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(0);
    }

    @Test
    public void testEnabledStrategySetNumComponentAdsInPersistAdSelectionResultWinnerType() {
        PersistAdSelectionResultCalledStats.Builder builder =
                PersistAdSelectionResultCalledStats.builder().setWinnerType(WINNER_TYPE_CA_WINNER);
        mComponentAdsStrategyEnabled.setNumComponentAdsInPersistAdSelectionResultWinnerType(
                builder, 1);
        expect.that(builder.build().getNumComponentAds()).isEqualTo(1);
    }

    @Test
    public void testDisabledStrategyUnsetNumComponentAdsInPersistAdSelectionResultWinnerType() {
        PersistAdSelectionResultCalledStats.Builder builder =
                PersistAdSelectionResultCalledStats.builder().setWinnerType(WINNER_TYPE_CA_WINNER);
        mComponentAdsStrategyDisabled.setNumComponentAdsInPersistAdSelectionResultWinnerType(
                builder, 1);
        expect.that(builder.build().getNumComponentAds()).isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testEnabledStrategyGetNumCustomAudiencesWithComponentAds() {
        BuyerInputGeneratorIntermediateStats stats = new BuyerInputGeneratorIntermediateStats();
        expect.that(mComponentAdsStrategyEnabled.getNumCustomAudiencesWithComponentAds(stats))
                .isEqualTo(0);
        expect.that(mComponentAdsStrategyEnabled.getNumCustomAudiencesWithComponentAds(stats))
                .isEqualTo(0);

        mComponentAdsStrategyEnabled.incrementNumCustomAudiencesWithComponentAds(stats);

        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(1);
        expect.that(mComponentAdsStrategyEnabled.getNumCustomAudiencesWithComponentAds(stats))
                .isEqualTo(1);
    }

    @Test
    public void testDisabledStrategyGetNumCustomAudiencesWithComponentAds() {
        BuyerInputGeneratorIntermediateStats stats = new BuyerInputGeneratorIntermediateStats();
        mComponentAdsStrategyDisabled.incrementNumCustomAudiencesWithComponentAds(stats);
        expect.that(stats.getNumCustomAudiencesWithComponentAds()).isEqualTo(0);
        expect.that(mComponentAdsStrategyDisabled.getNumCustomAudiencesWithComponentAds(stats))
                .isEqualTo(FIELD_UNSET);
    }

    @Test
    public void
            testEnabledStrategyGetCustomAudiencesWithComponentAds_multipleAudiences_multipleAds() {
        DBCustomAudience audience1 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(VALID_BUYER_1).build();
        DBCustomAudience audience2 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(VALID_BUYER_2).build();

        List<DBCustomAudience> dbCustomAudiences = List.of(audience1, audience2);

        List<DBComponentAdData> dbComponentAds1 =
                DBComponentAdDataFixture.getValidComponentAdsByBuyer(
                        ComponentAdDataFixture.getValidComponentAdsByBuyerAndRenderId(
                                VALID_BUYER_1, List.of("render1", "render2")),
                        audience1.getOwner(),
                        audience1.getBuyer(),
                        audience1.getName());
        List<DBComponentAdData> dbComponentAds2 =
                DBComponentAdDataFixture.getValidComponentAdsByBuyer(
                        ComponentAdDataFixture.getValidComponentAdsByBuyerAndRenderId(
                                VALID_BUYER_2, List.of("render2", "render3")),
                        audience2.getOwner(),
                        audience2.getBuyer(),
                        audience2.getName());

        List<DBComponentAdData> combinedComponentAds = new ArrayList<>(dbComponentAds1);
        combinedComponentAds.addAll(dbComponentAds2);

        when(mCustomAudienceDaoMock.getComponentAdsByBuyers(Set.of(VALID_BUYER_1, VALID_BUYER_2)))
                .thenReturn(combinedComponentAds);

        List<CustomAudienceWithComponentAds> result =
                mComponentAdsStrategyEnabled.getCustomAudiencesWithComponentAds(
                        mCustomAudienceDaoMock, dbCustomAudiences);

        expect.that(result).hasSize(2);

        // Check audience1
        expect.that(result.get(0).getDBCustomAudience()).isEqualTo(audience1);
        List<String> adRenderIds1 =
                dbComponentAds1.stream()
                        .map(DBComponentAdData::getRenderId)
                        .collect(Collectors.toList());
        expect.that(result.get(0).getComponentAdRenderIds())
                .containsExactlyElementsIn(adRenderIds1)
                .inOrder();

        // Check audience2
        expect.that(result.get(1).getDBCustomAudience()).isEqualTo(audience2);
        List<String> adRenderIds2 =
                dbComponentAds2.stream()
                        .map(DBComponentAdData::getRenderId)
                        .collect(Collectors.toList());
        expect.that(result.get(1).getComponentAdRenderIds())
                .containsExactlyElementsIn(adRenderIds2)
                .inOrder();
    }

    @Test
    public void
            testEnabledStrategyGetCustomAudiencesWithComponentAds_multipleAudiences_someWithNoAds() {
        DBCustomAudience audience1 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
        DBCustomAudience audience2 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_2)
                        .build(); // No ads
        DBCustomAudience audience3 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("name_2")
                        .build();

        List<DBCustomAudience> dbCustomAudiences = List.of(audience1, audience2, audience3);

        List<DBComponentAdData> dbComponentAds1 =
                DBComponentAdDataFixture.getValidComponentAdsByBuyer(
                        ComponentAdDataFixture.getValidComponentAdsByBuyerAndRenderId(
                                VALID_BUYER_1, List.of("render1", "render2")),
                        audience1.getOwner(),
                        audience1.getBuyer(),
                        audience1.getName());
        List<DBComponentAdData> dbComponentAds2 =
                DBComponentAdDataFixture.getValidComponentAdsByBuyer(
                        ComponentAdDataFixture.getValidComponentAdsByBuyerAndRenderId(
                                VALID_BUYER_2, List.of("render2", "render3")),
                        audience2.getOwner(),
                        audience2.getBuyer(),
                        audience2.getName());

        List<DBComponentAdData> combinedComponentAds = new ArrayList<>(dbComponentAds1);
        combinedComponentAds.addAll(dbComponentAds2);

        when(mCustomAudienceDaoMock.getComponentAdsByBuyers(
                        Set.of(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2)))
                .thenReturn(combinedComponentAds);

        List<CustomAudienceWithComponentAds> result =
                mComponentAdsStrategyEnabled.getCustomAudiencesWithComponentAds(
                        mCustomAudienceDaoMock, dbCustomAudiences);

        expect.that(result).hasSize(3);

        expect.that(result.get(0).getDBCustomAudience()).isEqualTo(audience1);
        List<String> adRenderIds1 =
                dbComponentAds1.stream()
                        .map(DBComponentAdData::getRenderId)
                        .collect(Collectors.toList());
        expect.that(result.get(0).getComponentAdRenderIds())
                .containsExactlyElementsIn(adRenderIds1)
                .inOrder();

        expect.that(result.get(1).getDBCustomAudience()).isEqualTo(audience2);
        List<String> adRenderIds2 =
                dbComponentAds2.stream()
                        .map(DBComponentAdData::getRenderId)
                        .collect(Collectors.toList());
        expect.that(result.get(1).getComponentAdRenderIds())
                .containsExactlyElementsIn(adRenderIds2)
                .inOrder();

        expect.that(result.get(2).getDBCustomAudience()).isEqualTo(audience3);
        expect.that(result.get(2).getComponentAdRenderIds()).isEmpty();
    }

    @Test
    public void testEnabledStrategyGetCustomAudiencesWithComponentAds_unmatchedComponentAds() {
        DBCustomAudience audience1 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
        List<DBCustomAudience> dbCustomAudiences = List.of(audience1);

        List<DBComponentAdData> unmatchedComponentAds =
                DBComponentAdDataFixture.getValidComponentAdsByBuyer(
                        ComponentAdDataFixture.getValidComponentAdsByBuyer(
                                CommonFixture.VALID_BUYER_1),
                        audience1.getOwner(),
                        audience1.getBuyer(),
                        "unmatched_name");

        when(mCustomAudienceDaoMock.getComponentAdsByBuyers(Set.of(CommonFixture.VALID_BUYER_1)))
                .thenReturn(unmatchedComponentAds);

        List<CustomAudienceWithComponentAds> result =
                mComponentAdsStrategyEnabled.getCustomAudiencesWithComponentAds(
                        mCustomAudienceDaoMock, dbCustomAudiences);

        expect.that(result).hasSize(1);

        expect.that(result.get(0).getDBCustomAudience()).isEqualTo(audience1);

        expect.that(result.get(0).getComponentAdRenderIds()).isEmpty();
    }

    @Test
    public void testDisabledStrategyGetComponentAdRenderIdsForDBCustomAudiences() {
        List<DBCustomAudience> dbCustomAudiences =
                List.of(
                        DBCustomAudienceFixture.getValidBuilderByBuyer(VALID_BUYER_1).build(),
                        DBCustomAudienceFixture.getValidBuilderByBuyer(VALID_BUYER_2).build());

        List<CustomAudienceWithComponentAds> result =
                mComponentAdsStrategyDisabled.getCustomAudiencesWithComponentAds(
                        mCustomAudienceDaoMock, dbCustomAudiences);

        expect.that(result.get(0).getDBCustomAudience()).isEqualTo(dbCustomAudiences.get(0));
        expect.that(result.get(1).getDBCustomAudience()).isEqualTo(dbCustomAudiences.get(1));

        expect.that(result.get(0).getComponentAdRenderIds()).isEmpty();
        expect.that(result.get(1).getComponentAdRenderIds()).isEmpty();
        verifyZeroInteractions(mCustomAudienceDaoMock);
    }

    @Test
    public void testExtractValidComponentAdsEnabled() {
        List<ComponentAdData> componentAds =
                ComponentAdDataFixture.getValidComponentAdsByBuyer(VALID_BUYER_1);

        mComponentAdsStrategyEnabled.extractValidComponentAds(VALID_BUYER_1, componentAds);
        verify(mComponentAdsListValidatorMock)
                .extractValidComponentAds(VALID_BUYER_1, componentAds);
    }

    @Test
    public void testExtractValidComponentAdsDisabled() {
        List<ComponentAdData> unFilteredComponentAds =
                ComponentAdDataFixture.getValidComponentAdsByBuyer(VALID_BUYER_1);

        List<ComponentAdData> componentAds =
                mComponentAdsStrategyDisabled.extractValidComponentAds(
                        VALID_BUYER_1, unFilteredComponentAds);

        expect.that(componentAds).containsExactlyElementsIn(unFilteredComponentAds).inOrder();
        verifyZeroInteractions(mComponentAdsListValidatorMock);
    }

    @Test
    public void testExtractComponentAdsThatMatchOnDeviceStrategyEnabledReturnsOnlyOnDevice() {
        DBComponentAdData dbComponentAdData1 =
                DBComponentAdData.create(
                        VALID_OWNER,
                        VALID_BUYER_1,
                        VALID_NAME,
                        Uri.parse("renderUri1"),
                        "renderId1");
        DBComponentAdData dbComponentAdData2 =
                DBComponentAdData.create(
                        VALID_OWNER,
                        VALID_BUYER_1,
                        VALID_NAME,
                        Uri.parse("renderUri2"),
                        "renderId2");

        List<DBComponentAdData> dbComponentAdDataList =
                List.of(dbComponentAdData1, dbComponentAdData2);

        when(mCustomAudienceDaoMock.getComponentAdsByCustomAudienceInfo(
                        VALID_OWNER, VALID_BUYER_1, VALID_NAME))
                .thenReturn(dbComponentAdDataList);

        List<Uri> result =
                mComponentAdsStrategyEnabled.extractComponentAdsThatMatchOnDevice(
                        AUCTION_RESULT_WITH_COMPONENT_ADS, mCustomAudienceDaoMock);

        verify(mCustomAudienceDaoMock)
                .getComponentAdsByCustomAudienceInfo(VALID_OWNER, VALID_BUYER_1, VALID_NAME);

        expect.that(result)
                .containsExactlyElementsIn(
                        List.of(
                                dbComponentAdData1.getRenderUri(),
                                dbComponentAdData2.getRenderUri()))
                .inOrder();
    }

    @Test
    public void testExtractComponentAdsThatMatchOnDeviceStrategyDisabled() {

        List<Uri> result =
                mComponentAdsStrategyDisabled.extractComponentAdsThatMatchOnDevice(
                        AUCTION_RESULT_WITH_COMPONENT_ADS, mCustomAudienceDaoMock);

        expect.that(result).isEmpty();
        verifyZeroInteractions(mCustomAudienceDaoMock);
    }
}
