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

import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;

import java.time.Instant;

public class AdSelectionOutcomeFixture {
    public static final long SAMPLE_AD_SELECTION_ID1 = 12345;
    public static final long SAMPLE_AD_SELECTION_ID2 = 123456;
    public static final Uri SAMPLE_RENDER_URI1 = Uri.parse("https://test.com/render");
    public static final Uri SAMPLE_RENDER_URI2 = Uri.parse("https://test2.com/render");

    public static AdSelectionOutcome anAdSelectionOutcome() {
        return new AdSelectionOutcome.Builder()
                .setAdSelectionId(SAMPLE_AD_SELECTION_ID1)
                .setRenderUri(SAMPLE_RENDER_URI1)
                .build();
    }

    public static AdSelectionOutcome anAdSelectionOutcome(final long adSelectionId) {
        return new AdSelectionOutcome.Builder()
                .setAdSelectionId(adSelectionId)
                .setRenderUri(SAMPLE_RENDER_URI1)
                .build();
    }

    public static AdSelectionOutcome anAdSelectionOutcome(final Uri renderUri) {
        return new AdSelectionOutcome.Builder()
                .setAdSelectionId(SAMPLE_AD_SELECTION_ID1)
                .setRenderUri(renderUri)
                .build();
    }

    public static AdSelectionOutcome anAdSelectionOutcome(
            final long adSelectionId, final Uri renderUri) {
        return new AdSelectionOutcome.Builder()
                .setAdSelectionId(adSelectionId)
                .setRenderUri(renderUri)
                .build();
    }

    public static AdSelectionOutcome anAdSelectionOutcomeInDB(
            @NonNull AdSelectionEntryDao mAdSelectionEntryDao) {
        final Uri biddingLogicUri1 = Uri.parse("https://www.domain.com/logic/1");
        final Uri renderUri = Uri.parse("https://www.domain.com/advert/");
        final Instant activationTime = Instant.now();
        final long adSelectionId = 1;
        final String contextualSignals = "contextual_signals";
        final double bid1 = 10;
        final String callerPackageName1 = "callerPackageName1";
        final CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();
        final DBAdSelection dbAdSelectionEntry =
                new DBAdSelection.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(contextualSignals)
                        .setBiddingLogicUri(biddingLogicUri1)
                        .setWinningAdRenderUri(renderUri)
                        .setWinningAdBid(bid1)
                        .setCreationTimestamp(activationTime)
                        .setCallerPackageName(callerPackageName1)
                        .build();
        mAdSelectionEntryDao.persistAdSelection(dbAdSelectionEntry);
        return anAdSelectionOutcome(
                dbAdSelectionEntry.getAdSelectionId(), dbAdSelectionEntry.getWinningAdRenderUri());
    }
}
