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
import static android.adservices.common.FrequencyCapFilters.AD_EVENT_TYPE_WIN;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FrequencyCapFilters;
import android.annotation.NonNull;
import android.os.OutcomeReceiver;

import com.android.internal.util.Preconditions;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Request object wrapping the required arguments needed to update an ad counter histogram.
 *
 * <p>The ad counter histograms, which are historical logs of events which are associated with an ad
 * counter key and an ad event type, are used to inform frequency cap filtering when using the
 * Protected Audience APIs.
 */
public class UpdateAdCounterHistogramRequest {
    /** @hide */
    public static final String UNSET_AD_EVENT_TYPE_MESSAGE = "Ad event type must be set";

    /** @hide */
    public static final String DISALLOW_AD_EVENT_TYPE_WIN_MESSAGE =
            "Win event types cannot be manually updated";

    /** @hide */
    public static final String INVALID_AD_EVENT_TYPE_MESSAGE =
            "Ad event type must be one of AD_EVENT_TYPE_IMPRESSION, AD_EVENT_TYPE_VIEW, or"
                    + " AD_EVENT_TYPE_CLICK";

    /** @hide */
    public static final String UNSET_CALLER_ADTECH_MESSAGE = "Caller ad tech must not be null";

    private final long mAdSelectionId;
    @FrequencyCapFilters.AdEventType private final int mAdEventType;
    @NonNull private final AdTechIdentifier mCallerAdTech;

    private UpdateAdCounterHistogramRequest(@NonNull Builder builder) {
        Objects.requireNonNull(builder);

        mAdSelectionId = builder.mAdSelectionId;
        mAdEventType = builder.mAdEventType;
        mCallerAdTech = builder.mCallerAdTech;
    }

    /**
     * Gets the ad selection ID with which the rendered ad's events are associated.
     *
     * <p>For more information about the ad selection ID, see {@link AdSelectionOutcome}.
     *
     * <p>The ad must have been selected from Protected Audience ad selection in the last 24 hours,
     * and the ad selection call must have been initiated from the same app as the current calling
     * app. Event histograms for all ad counter keys associated with the ad specified by the ad
     * selection ID will be updated for the ad event type from {@link #getAdEventType()}, to be used
     * in Protected Audience frequency cap filtering.
     */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /**
     * Gets the ad event type which, along with an ad's counter keys, identifies which histogram
     * should be updated.
     */
    @FrequencyCapFilters.AdEventType
    public int getAdEventType() {
        return mAdEventType;
    }

    /**
     * Gets the caller adtech entity's {@link AdTechIdentifier}.
     *
     * <p>The adtech using this {@link UpdateAdCounterHistogramRequest} object must have enrolled
     * with the Privacy Sandbox and be allowed to act on behalf of the calling app. The specified
     * adtech is not required to be the same adtech as either the buyer which owns the rendered ad
     * or the seller which initiated the ad selection associated with the ID returned by {@link
     * #getAdSelectionId()}.
     *
     * <p>For more information about API requirements and exceptions, see {@link
     * AdSelectionManager#updateAdCounterHistogram(UpdateAdCounterHistogramRequest, Executor,
     * OutcomeReceiver)}.
     */
    @NonNull
    public AdTechIdentifier getCallerAdTech() {
        return mCallerAdTech;
    }

    /**
     * Checks whether the {@link UpdateAdCounterHistogramRequest} objects contain the same
     * information.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateAdCounterHistogramRequest)) return false;
        UpdateAdCounterHistogramRequest that = (UpdateAdCounterHistogramRequest) o;
        return mAdSelectionId == that.mAdSelectionId
                && mAdEventType == that.mAdEventType
                && mCallerAdTech.equals(that.mCallerAdTech);
    }

    /** Returns the hash of the {@link UpdateAdCounterHistogramRequest} object's data. */
    @Override
    public int hashCode() {
        return Objects.hash(mAdSelectionId, mAdEventType, mCallerAdTech);
    }

    @Override
    public String toString() {
        return "UpdateAdCounterHistogramRequest{"
                + "mAdSelectionId="
                + mAdSelectionId
                + ", mAdEventType="
                + mAdEventType
                + ", mCallerAdTech="
                + mCallerAdTech
                + '}';
    }

    /** Builder for {@link UpdateAdCounterHistogramRequest} objects. */
    public static final class Builder {
        private long mAdSelectionId;
        @FrequencyCapFilters.AdEventType private int mAdEventType;
        @NonNull private AdTechIdentifier mCallerAdTech;

        public Builder(
                long adSelectionId, int adEventType, @NonNull AdTechIdentifier callerAdTech) {
            Preconditions.checkArgument(
                    adSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);
            Preconditions.checkArgument(
                    adEventType != AD_EVENT_TYPE_WIN, DISALLOW_AD_EVENT_TYPE_WIN_MESSAGE);
            Preconditions.checkArgument(
                    adEventType >= FrequencyCapFilters.AD_EVENT_TYPE_MIN
                            && adEventType <= FrequencyCapFilters.AD_EVENT_TYPE_MAX,
                    INVALID_AD_EVENT_TYPE_MESSAGE);
            Objects.requireNonNull(callerAdTech, UNSET_CALLER_ADTECH_MESSAGE);

            mAdSelectionId = adSelectionId;
            mAdEventType = adEventType;
            mCallerAdTech = callerAdTech;
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
         * Sets the ad event type which, along with an ad's counter keys, identifies which histogram
         * should be updated.
         *
         * <p>See {@link #getAdEventType()} for more information.
         */
        @NonNull
        public Builder setAdEventType(@FrequencyCapFilters.AdEventType int adEventType) {
            Preconditions.checkArgument(
                    adEventType != AD_EVENT_TYPE_WIN, DISALLOW_AD_EVENT_TYPE_WIN_MESSAGE);
            Preconditions.checkArgument(
                    adEventType >= FrequencyCapFilters.AD_EVENT_TYPE_MIN
                            && adEventType <= FrequencyCapFilters.AD_EVENT_TYPE_MAX,
                    INVALID_AD_EVENT_TYPE_MESSAGE);
            mAdEventType = adEventType;
            return this;
        }

        /**
         * Sets the caller adtech entity's {@link AdTechIdentifier}.
         *
         * <p>See {@link #getCallerAdTech()} for more information.
         */
        @NonNull
        public Builder setCallerAdTech(@NonNull AdTechIdentifier callerAdTech) {
            Objects.requireNonNull(callerAdTech, UNSET_CALLER_ADTECH_MESSAGE);
            mCallerAdTech = callerAdTech;
            return this;
        }

        /** Builds the {@link UpdateAdCounterHistogramRequest} object. */
        @NonNull
        public UpdateAdCounterHistogramRequest build() {
            return new UpdateAdCounterHistogramRequest(this);
        }
    }
}
