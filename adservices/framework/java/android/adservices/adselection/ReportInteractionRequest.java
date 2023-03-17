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

import static android.adservices.adselection.AdSelectionOutcome.UNSET_AD_SELECTION_ID;
import static android.adservices.adselection.AdSelectionOutcome.UNSET_AD_SELECTION_ID_MESSAGE;

import android.annotation.IntDef;
import android.annotation.NonNull;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Request object wrapping the required arguments needed to report an interaction.
 */
public class ReportInteractionRequest {
    public static final int FLAG_REPORTING_DESTINATION_SELLER = 1 << 0;
    public static final int FLAG_REPORTING_DESTINATION_BUYER = 1 << 1;
    private static final int UNSET_REPORTING_DESTINATIONS = 0;
    private static final String UNSET_REPORTING_DESTINATIONS_MESSAGE =
            "Reporting destinations bitfield not set.";

    private final long mAdSelectionId;
    @NonNull private final String mInteractionKey;
    @NonNull private final String mInteractionData;
    @ReportingDestination private final int mReportingDestinations; // buyer, seller, or both

    public ReportInteractionRequest(
            long adSelectionId,
            @NonNull String interactionKey,
            @NonNull String interactionData,
            @ReportingDestination int reportingDestinations) {
        Objects.requireNonNull(interactionKey);
        Objects.requireNonNull(interactionData);

        Preconditions.checkArgument(
                adSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);
        Preconditions.checkArgument(
                reportingDestinations != UNSET_REPORTING_DESTINATIONS,
                UNSET_REPORTING_DESTINATIONS_MESSAGE);

        this.mAdSelectionId = adSelectionId;
        this.mInteractionKey = interactionKey;
        this.mInteractionData = interactionData;
        this.mReportingDestinations = reportingDestinations;
    }

    /** Returns the adSelectionId, the primary identifier of an ad selection process. */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /**
     * Returns the interaction key, the type of interaction to be reported.
     *
     * <p>This will be used to fetch the {@code interactionReportingUri} associated with the {@code
     * interactionKey} registered in {@code registerAdBeacon} after ad selection.
     */
    @NonNull
    public String getInteractionKey() {
        return mInteractionKey;
    }

    /**
     * Returns the interaction data.
     *
     * <p>After ad selection, this data is generated by the caller, and will be attached in a POST
     * request to the {@code interactionReportingUri} registered in {@code registerAdBeacon}.
     */
    @NonNull
    public String getInteractionData() {
        return mInteractionData;
    }

    /**
     * Returns the bitfield of reporting destinations to report to (buyer, seller, or both).
     *
     * <p>To create this bitfield, place an {@code |} bitwise operator between each {@code
     * reportingDestination} to be reported to. For example to only report to buyer, set the
     * reportingDestinations field to {@link #FLAG_REPORTING_DESTINATION_BUYER} To only report to
     * seller, set the reportingDestinations field to {@link #FLAG_REPORTING_DESTINATION_SELLER} To
     * report to both buyers and sellers, set the reportingDestinations field to {@link
     * #FLAG_REPORTING_DESTINATION_BUYER} | {@link #FLAG_REPORTING_DESTINATION_SELLER}
     */
    @ReportingDestination
    public int getReportingDestinations() {
        return mReportingDestinations;
    }

    /** @hide */
    @IntDef(
            flag = true,
            prefix = {"FLAG_REPORTING_DESTINATION"},
            value = {FLAG_REPORTING_DESTINATION_SELLER, FLAG_REPORTING_DESTINATION_BUYER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReportingDestination {}
}
