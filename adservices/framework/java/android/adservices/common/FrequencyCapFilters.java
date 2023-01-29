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

package android.adservices.common;

import android.adservices.adselection.ReportImpressionRequest;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.AdServicesParcelableUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A container for the ad filters that are based on frequency caps.
 *
 * <p>Frequency caps filters combine an event type with a set of {@link KeyedFrequencyCap} objects
 * to define a set of ad filters. If any of these frequency caps are met for a given ad, the ad will
 * be removed from the group of ads submitted to a buyer adtech's bidding function.
 *
 * @hide
 */
// TODO(b/221876775): Unhide for frequency cap API review
public final class FrequencyCapFilters implements Parcelable {
    /**
     * Event types which are used to update ad counter histograms, which inform frequency cap
     * filtering in FLEDGE.
     *
     * @hide
     */
    @IntDef(
            prefix = {"AD_EVENT_TYPE_"},
            value = {
                AD_EVENT_TYPE_WIN,
                AD_EVENT_TYPE_IMPRESSION,
                AD_EVENT_TYPE_VIEW,
                AD_EVENT_TYPE_CLICK
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AdEventType {}

    /**
     * The WIN ad event type is automatically populated within the FLEDGE service for any winning ad
     * which is returned from FLEDGE ad selection.
     *
     * <p>It should not be used to manually update an ad counter histogram.
     */
    public static final int AD_EVENT_TYPE_WIN = 0;

    public static final int AD_EVENT_TYPE_IMPRESSION = 1;
    public static final int AD_EVENT_TYPE_VIEW = 2;
    public static final int AD_EVENT_TYPE_CLICK = 3;

    @NonNull private final Set<KeyedFrequencyCap> mWinKeyedFrequencyCaps;
    @NonNull private final Set<KeyedFrequencyCap> mImpressionKeyedFrequencyCaps;
    @NonNull private final Set<KeyedFrequencyCap> mViewKeyedFrequencyCaps;
    @NonNull private final Set<KeyedFrequencyCap> mClickKeyedFrequencyCaps;

    @NonNull
    public static final Creator<FrequencyCapFilters> CREATOR =
            new Creator<FrequencyCapFilters>() {
                @Override
                public FrequencyCapFilters createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new FrequencyCapFilters(in);
                }

                @Override
                public FrequencyCapFilters[] newArray(int size) {
                    return new FrequencyCapFilters[size];
                }
            };

    private FrequencyCapFilters(@NonNull Builder builder) {
        Objects.requireNonNull(builder);

        mWinKeyedFrequencyCaps = builder.mWinKeyedFrequencyCaps;
        mImpressionKeyedFrequencyCaps = builder.mImpressionKeyedFrequencyCaps;
        mViewKeyedFrequencyCaps = builder.mViewKeyedFrequencyCaps;
        mClickKeyedFrequencyCaps = builder.mClickKeyedFrequencyCaps;
    }

    private FrequencyCapFilters(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mWinKeyedFrequencyCaps =
                AdServicesParcelableUtil.readSetFromParcel(in, KeyedFrequencyCap.CREATOR);
        mImpressionKeyedFrequencyCaps =
                AdServicesParcelableUtil.readSetFromParcel(in, KeyedFrequencyCap.CREATOR);
        mViewKeyedFrequencyCaps =
                AdServicesParcelableUtil.readSetFromParcel(in, KeyedFrequencyCap.CREATOR);
        mClickKeyedFrequencyCaps =
                AdServicesParcelableUtil.readSetFromParcel(in, KeyedFrequencyCap.CREATOR);
    }

    /**
     * Gets the set of {@link KeyedFrequencyCap} objects that will filter on the win event type.
     *
     * <p>These frequency caps apply to events for ads that were selected as winners in ad
     * selection. Winning ads are used to automatically increment the associated counter keys on the
     * win event type.
     */
    @NonNull
    public Set<KeyedFrequencyCap> getWinKeyedFrequencyCaps() {
        return mWinKeyedFrequencyCaps;
    }

    /**
     * Gets the set of {@link KeyedFrequencyCap} objects that will filter on the impression event
     * type.
     *
     * <p>These frequency caps apply to events which correlate to an impression as interpreted by an
     * adtech. Note that events are not automatically counted when calling {@link
     * android.adservices.adselection.AdSelectionManager#reportImpression(ReportImpressionRequest,
     * Executor, OutcomeReceiver)}.
     */
    @NonNull
    public Set<KeyedFrequencyCap> getImpressionKeyedFrequencyCaps() {
        return mImpressionKeyedFrequencyCaps;
    }

    /**
     * Gets the set of {@link KeyedFrequencyCap} objects that will filter on the view event type.
     *
     * <p>These frequency caps apply to events which correlate to a view as interpreted by an
     * adtech.
     */
    @NonNull
    public Set<KeyedFrequencyCap> getViewKeyedFrequencyCaps() {
        return mViewKeyedFrequencyCaps;
    }

    /**
     * Gets the set of {@link KeyedFrequencyCap} objects that will filter on the click event type.
     *
     * <p>These frequency caps apply to events which correlate to a click as interpreted by an
     * adtech.
     */
    @NonNull
    public Set<KeyedFrequencyCap> getClickKeyedFrequencyCaps() {
        return mClickKeyedFrequencyCaps;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        AdServicesParcelableUtil.writeSetToParcel(dest, mWinKeyedFrequencyCaps);
        AdServicesParcelableUtil.writeSetToParcel(dest, mImpressionKeyedFrequencyCaps);
        AdServicesParcelableUtil.writeSetToParcel(dest, mViewKeyedFrequencyCaps);
        AdServicesParcelableUtil.writeSetToParcel(dest, mClickKeyedFrequencyCaps);
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Checks whether the {@link FrequencyCapFilters} objects contain the same information. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrequencyCapFilters)) return false;
        FrequencyCapFilters that = (FrequencyCapFilters) o;
        return mWinKeyedFrequencyCaps.equals(that.mWinKeyedFrequencyCaps)
                && mImpressionKeyedFrequencyCaps.equals(that.mImpressionKeyedFrequencyCaps)
                && mViewKeyedFrequencyCaps.equals(that.mViewKeyedFrequencyCaps)
                && mClickKeyedFrequencyCaps.equals(that.mClickKeyedFrequencyCaps);
    }

    /** Returns the hash of the {@link FrequencyCapFilters} object's data. */
    @Override
    public int hashCode() {
        return Objects.hash(
                mWinKeyedFrequencyCaps,
                mImpressionKeyedFrequencyCaps,
                mViewKeyedFrequencyCaps,
                mClickKeyedFrequencyCaps);
    }

    @Override
    public String toString() {
        return "FrequencyCapFilters{"
                + "mWinKeyedFrequencyCaps="
                + mWinKeyedFrequencyCaps
                + ", mImpressionKeyedFrequencyCaps="
                + mImpressionKeyedFrequencyCaps
                + ", mViewKeyedFrequencyCaps="
                + mViewKeyedFrequencyCaps
                + ", mClickKeyedFrequencyCaps="
                + mClickKeyedFrequencyCaps
                + '}';
    }

    /** Builder for creating {@link FrequencyCapFilters} objects. */
    public static final class Builder {
        @NonNull private Set<KeyedFrequencyCap> mWinKeyedFrequencyCaps = new HashSet<>();
        @NonNull private Set<KeyedFrequencyCap> mImpressionKeyedFrequencyCaps = new HashSet<>();
        @NonNull private Set<KeyedFrequencyCap> mViewKeyedFrequencyCaps = new HashSet<>();
        @NonNull private Set<KeyedFrequencyCap> mClickKeyedFrequencyCaps = new HashSet<>();

        public Builder() {}

        /**
         * Sets the set of {@link KeyedFrequencyCap} objects that will filter on the win event type.
         *
         * <p>See {@link #getWinKeyedFrequencyCaps()} for more information.
         */
        @NonNull
        public Builder setWinKeyedFrequencyCaps(
                @NonNull Set<KeyedFrequencyCap> winKeyedFrequencyCaps) {
            Objects.requireNonNull(winKeyedFrequencyCaps);
            mWinKeyedFrequencyCaps = winKeyedFrequencyCaps;
            return this;
        }

        /**
         * Sets the set of {@link KeyedFrequencyCap} objects that will filter on the impression
         * event type.
         *
         * <p>See {@link #getImpressionKeyedFrequencyCaps()} for more information.
         */
        @NonNull
        public Builder setImpressionKeyedFrequencyCaps(
                @NonNull Set<KeyedFrequencyCap> impressionKeyedFrequencyCaps) {
            Objects.requireNonNull(impressionKeyedFrequencyCaps);
            mImpressionKeyedFrequencyCaps = impressionKeyedFrequencyCaps;
            return this;
        }

        /**
         * Sets the set of {@link KeyedFrequencyCap} objects that will filter on the view event
         * type.
         *
         * <p>See {@link #getViewKeyedFrequencyCaps()} for more information.
         */
        @NonNull
        public Builder setViewKeyedFrequencyCaps(
                @NonNull Set<KeyedFrequencyCap> viewKeyedFrequencyCaps) {
            Objects.requireNonNull(viewKeyedFrequencyCaps);
            mViewKeyedFrequencyCaps = viewKeyedFrequencyCaps;
            return this;
        }

        /**
         * Sets the set of {@link KeyedFrequencyCap} objects that will filter on the click event
         * type.
         *
         * <p>See {@link #getClickKeyedFrequencyCaps()} for more information.
         */
        @NonNull
        public Builder setClickKeyedFrequencyCaps(
                @NonNull Set<KeyedFrequencyCap> clickKeyedFrequencyCaps) {
            Objects.requireNonNull(clickKeyedFrequencyCaps);
            mClickKeyedFrequencyCaps = clickKeyedFrequencyCaps;
            return this;
        }

        /** Builds and returns a {@link FrequencyCapFilters} instance. */
        @NonNull
        public FrequencyCapFilters build() {
            return new FrequencyCapFilters(this);
        }
    }
}
