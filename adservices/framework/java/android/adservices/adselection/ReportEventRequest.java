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
 * Request object wrapping the required arguments needed to report an ad event.
 */
public class ReportEventRequest {
    public static final int FLAG_REPORTING_DESTINATION_SELLER = 1 << 0;
    public static final int FLAG_REPORTING_DESTINATION_BUYER = 1 << 1;
    private static final int UNSET_REPORTING_DESTINATIONS = 0;
    private static final String UNSET_REPORTING_DESTINATIONS_MESSAGE =
            "Reporting destinations bitfield not set.";
    private static final String INVALID_REPORTING_DESTINATIONS_MESSAGE =
            "Invalid reporting destinations bitfield!";

    private final long mAdSelectionId;
    @NonNull private final String mEventKey;
    @NonNull private final String mEventData;
    @ReportingDestination private final int mReportingDestinations; // buyer, seller, or both

    private ReportEventRequest(@NonNull Builder builder) {
        Objects.requireNonNull(builder);

        this.mAdSelectionId = builder.mAdSelectionId;
        this.mEventKey = builder.mEventKey;
        this.mEventData = builder.mEventData;
        this.mReportingDestinations = builder.mReportingDestinations;
    }

    /**
     * Returns the adSelectionId, the primary identifier of an ad selection process.
     */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /**
     * Returns the event key, the type of ad event to be reported.
     *
     * <p>This field will be used to fetch the {@code reportingUri} associated with the {@code
     * eventKey} registered in {@code registerAdBeacon} after ad selection.
     *
     * <p>This field should be an exact match to the {@code eventKey} registered in {@code
     * registerAdBeacon}. Specific details about {@code registerAdBeacon} can be found at the
     * documentation of {@link AdSelectionManager#reportImpression}
     *
     * <p>The event key (when inspecting its byte array with {@link String#getBytes()}) in {@code
     * UTF-8} format should not exceed 40 bytes. Any key exceeding this limit will not be registered
     * during the {@code registerAdBeacon} call.
     */
    @NonNull
    public String getKey() {
        return mEventKey;
    }

    /**
     * Returns the ad event data.
     *
     * <p>After ad selection, this data is generated by the caller. The caller can then call {@link
     * AdSelectionManager#reportEvent}. This data will be attached in a POST request to the {@code
     * reportingUri} registered in {@code registerAdBeacon}.
     *
     * <p>The size of {@link String#getBytes()} in {@code UTF-8} format should be below 64KB.
     */
    @NonNull
    public String getData() {
        return mEventData;
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

    /** Builder for {@link ReportEventRequest} objects. */
    public static final class Builder {

        private long mAdSelectionId;
        @NonNull private String mEventKey;
        @NonNull private String mEventData;
        @ReportingDestination private int mReportingDestinations; // buyer, seller, or both

        public Builder(
                long adSelectionId,
                @NonNull String eventKey,
                @NonNull String eventData,
                @ReportingDestination int reportingDestinations) {
            Objects.requireNonNull(eventKey);
            Objects.requireNonNull(eventData);

            Preconditions.checkArgument(
                    adSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);
            Preconditions.checkArgument(
                    reportingDestinations != UNSET_REPORTING_DESTINATIONS,
                    UNSET_REPORTING_DESTINATIONS_MESSAGE);

            Preconditions.checkArgument(
                    isValidDestination(reportingDestinations),
                    INVALID_REPORTING_DESTINATIONS_MESSAGE);

            this.mAdSelectionId = adSelectionId;
            this.mEventKey = eventKey;
            this.mEventData = eventData;
            this.mReportingDestinations = reportingDestinations;
        }

        private boolean isValidDestination(@ReportingDestination int reportingDestinations) {
            return 0 < reportingDestinations
                    && reportingDestinations
                            <= (FLAG_REPORTING_DESTINATION_SELLER
                                    | FLAG_REPORTING_DESTINATION_BUYER);
        }

        /**
         * Sets the ad selection ID with which the rendered ad's events are associated.
         *
         * <p>See {@link #getAdSelectionId()} for more information.
         */
        @NonNull
        public Builder setAdSelectionId(long adSelectionId) {
            Preconditions.checkArgument(
                    adSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);
            mAdSelectionId = adSelectionId;
            return this;
        }

        /**
         * Sets the event key, the type of ad event to be reported.
         *
         * <p>See {@link #getKey()} for more information.
         */
        @NonNull
        public Builder setKey(@NonNull String eventKey) {
            Objects.requireNonNull(eventKey);

            mEventKey = eventKey;
            return this;
        }

        /**
         * Sets the ad event data.
         *
         * <p>See {@link #getData()} for more information.
         */
        @NonNull
        public Builder setData(@NonNull String eventData) {
            Objects.requireNonNull(eventData);

            mEventData = eventData;
            return this;
        }

        /**
         * Sets the ad event data.
         *
         * <p>See {@link #getData()} for more information.
         */
        @NonNull
        public Builder setReportingDestinations(@ReportingDestination int reportingDestinations) {
            Preconditions.checkArgument(
                    isValidDestination(reportingDestinations),
                    INVALID_REPORTING_DESTINATIONS_MESSAGE);

            mReportingDestinations = reportingDestinations;
            return this;
        }

        /** Builds the {@link ReportEventRequest} object. */
        @NonNull
        public ReportEventRequest build() {
            return new ReportEventRequest(this);
        }
    }
}
