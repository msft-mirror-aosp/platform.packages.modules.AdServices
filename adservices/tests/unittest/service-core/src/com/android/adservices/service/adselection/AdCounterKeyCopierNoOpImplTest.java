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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;

import com.android.adservices.data.adselection.DBAdSelection;

import org.junit.Test;

public class AdCounterKeyCopierNoOpImplTest {
    private final AdCounterKeyCopierNoOpImpl mAdCounterKeyCopier = new AdCounterKeyCopierNoOpImpl();

    @Test
    public void testCopyAdCounterKeysNullBuilderThrows() {
        AdScoringOutcome sourceOutcome =
                AdScoringOutcomeFixture.anAdScoringBuilder(CommonFixture.VALID_BUYER_1, 1.0)
                        .build();
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys(null, sourceOutcome));
    }

    @Test
    public void testCopyAdCounterKeysNullOutcomeThrows() {
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys(new DBAdSelection.Builder(), null));
    }

    @Test
    public void testCopyAdCounterKeys() {
        AdScoringOutcome sourceOutcome =
                AdScoringOutcomeFixture.anAdScoringBuilderWithAdCounterKeys(
                                CommonFixture.VALID_BUYER_1, 1.0)
                        .build();
        DBAdSelection.Builder targetBuilder =
                new DBAdSelection.Builder()
                        .setWinningAdBid(sourceOutcome.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(sourceOutcome.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                sourceOutcome
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(sourceOutcome.getDecisionLogicUri())
                        .setContextualSignals("{}");

        DBAdSelection.Builder outputBuilder =
                mAdCounterKeyCopier.copyAdCounterKeys(targetBuilder, sourceOutcome);

        assertThat(outputBuilder).isEqualTo(targetBuilder);

        DBAdSelection outputSelection =
                outputBuilder
                        .setAdSelectionId(10)
                        .setCreationTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();
        assertThat(outputSelection.getAdCounterKeys()).isNull();
    }
}
