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
import android.annotation.Nullable;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Request object wrapping the required arguments needed to report an interaction.
 *
 * @hide
 */
// TODO(b/261812140): Unhide for report interaction API review
public class ReportInteractionRequest {
    public static final int FLAG_REPORTING_DESTINATION_SELLER = 1 << 0;
    public static final int FLAG_REPORTING_DESTINATION_BUYER = 1 << 1;
    private static final int UNSET_REPORTING_DESTINATIONS = 0;
    private static final String UNSET_REPORTING_DESTINATIONS_MESSAGE =
            "Reporting destinations bitfield not set.";

    private final long mAdSelectionId;
    @NonNull private final String mInteractionKey;
    @NonNull private final String mInteractionData;
    private final int mReportingDestinations; // buyer, seller, or both

    private ReportInteractionRequest(
            long adSelectionId,
            @NonNull String interactionKey,
            @NonNull String interactionData,
            int reportingDestinations) {
        Objects.requireNonNull(interactionKey);
        Objects.requireNonNull(interactionData);

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
    public String getInteractionKey() {
        return mInteractionKey;
    }

    /**
     * Returns the interaction data.
     *
     * <p>After ad selection, this data is generated by the caller, and will be attached in a POST
     * request to the {@code interactionReportingUri} registered in {@code registerAdBeacon}.
     */
    public String getInteractionData() {
        return mInteractionData;
    }

    /**
     * Returns the bitfield of reporting destinations to report to (buyer, seller, or both).
     *
     * <p>To create this bitfield, place an {@code |} bitwise operator between each {@link
     * ReportingDestination} to be reported to. For example to only report to buyer, set the
     * reportingDestinations field to {@link ReportingDestination#FLAG_REPORTING_DESTINATION_BUYER}
     * To only report to seller, set the reportingDestinations field to {@link
     * ReportingDestination#FLAG_REPORTING_DESTINATION_SELLER} To report to both buyers and sellers,
     * set the reportingDestinations field to {@link
     * ReportingDestination#FLAG_REPORTING_DESTINATION_BUYER} | {@link
     * ReportingDestination#FLAG_REPORTING_DESTINATION_SELLER}
     */
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

    /**
     * Builder for {@link ReportInteractionRequest} objects.
     *
     * @hide
     */
    // TODO(b/261812140): Unhide for report interaction API review
    public static final class Builder {
        private long mAdSelectionId = UNSET_AD_SELECTION_ID;
        @Nullable private String mInteractionKey;
        @Nullable private String mInteractionData;
        private int mReportingDestinations = UNSET_REPORTING_DESTINATIONS;

        public Builder() {}

        /**
         * Sets the adSelectionId.
         *
         * <p>See {@link ReportInteractionRequest#getAdSelectionId()} for more details
         */
        @NonNull
        public ReportInteractionRequest.Builder setAdSelectionId(long adSelectionId) {
            Preconditions.checkArgument(
                    adSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);

            mAdSelectionId = adSelectionId;
            return this;
        }

        /**
         * Sets the interactionKey.
         *
         * <p>See {@link ReportInteractionRequest#getInteractionKey()}} for more details.
         */
        @NonNull
        public ReportInteractionRequest.Builder setInteractionKey(@NonNull String interactionKey) {
            Objects.requireNonNull(interactionKey);

            mInteractionKey = interactionKey;
            return this;
        }

        /**
         * Sets the interactionData.
         *
         * <p>See {@link ReportInteractionRequest#getInteractionData()} for more details.
         */
        @NonNull
        public ReportInteractionRequest.Builder setInteractionData(
                @NonNull String interactionData) {
            Objects.requireNonNull(interactionData);

            mInteractionData = interactionData;
            return this;
        }

        /**
         * Sets the bitfield of reporting destinations.
         *
         * <p>See {@link ReportInteractionRequest#getReportingDestinations()} for more details.
         */
        @NonNull
        public ReportInteractionRequest.Builder setReportingDestinations(
                int reportingDestinations) {
            Preconditions.checkArgument(
                    reportingDestinations != UNSET_REPORTING_DESTINATIONS,
                    UNSET_REPORTING_DESTINATIONS_MESSAGE);

            mReportingDestinations = reportingDestinations;
            return this;
        }

        /** Builds a {@link ReportInteractionRequest} instance. */
        @NonNull
        public ReportInteractionRequest build() {
            Objects.requireNonNull(mInteractionKey);
            Objects.requireNonNull(mInteractionData);

            Preconditions.checkArgument(
                    mAdSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);
            Preconditions.checkArgument(
                    mReportingDestinations != UNSET_REPORTING_DESTINATIONS,
                    UNSET_REPORTING_DESTINATIONS_MESSAGE);

            return new ReportInteractionRequest(
                    mAdSelectionId, mInteractionKey, mInteractionData, mReportingDestinations);
        }
    }
}
