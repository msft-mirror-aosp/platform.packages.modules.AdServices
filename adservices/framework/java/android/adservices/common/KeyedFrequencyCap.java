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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.time.Duration;
import java.util.Objects;

/**
 * A frequency cap for a specific ad counter key.
 *
 * <p>Frequency caps define the maximum count of previously counted events within a given time
 * interval. If the frequency cap is exceeded, the associated ad will be filtered out of ad
 * selection.
 *
 * @hide
 */
// TODO(b/221876775): Unhide for frequency cap API review
public final class KeyedFrequencyCap implements Parcelable {
    @NonNull private final String mAdCounterKey;
    private final int mMaxCount;
    @NonNull private final Duration mInterval;

    @NonNull
    public static final Creator<KeyedFrequencyCap> CREATOR =
            new Creator<KeyedFrequencyCap>() {
                @Override
                public KeyedFrequencyCap createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new KeyedFrequencyCap(in);
                }

                @Override
                public KeyedFrequencyCap[] newArray(int size) {
                    return new KeyedFrequencyCap[size];
                }
            };

    private KeyedFrequencyCap(@NonNull Builder builder) {
        Objects.requireNonNull(builder);

        mAdCounterKey = builder.mAdCounterKey;
        mMaxCount = builder.mMaxCount;
        mInterval = builder.mInterval;
    }

    private KeyedFrequencyCap(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mAdCounterKey = in.readString();
        mMaxCount = in.readInt();
        mInterval = Duration.ofSeconds(in.readLong());
    }

    /**
     * Returns the ad counter key that the frequency cap is applied to.
     *
     * <p>The ad counter key is defined by an adtech and is an arbitrary string which defines any
     * criteria which may have previously been counted and persisted on the device. If the on-device
     * count exceeds the maximum count within a certain time interval, the frequency cap has been
     * exceeded.
     */
    @NonNull
    public String getAdCounterKey() {
        return mAdCounterKey;
    }

    /**
     * Returns the maximum count within a given time interval that a frequency cap applies to.
     *
     * <p>If there are more events for an adtech counted on the device within the time interval
     * defined by {@link #getInterval()}, the frequency cap has been exceeded.
     */
    public int getMaxCount() {
        return mMaxCount;
    }

    /**
     * Returns the interval, as a {@link Duration} which will be truncated to the nearest second,
     * over which the frequency cap is calculated.
     *
     * <p>When this frequency cap is computed, the number of persisted events is counted in the most
     * recent time interval. If the count of specified events for an adtech is equal to or greater
     * than the number returned by {@link #getMaxCount()}, the frequency cap has been exceeded.
     */
    @NonNull
    public Duration getInterval() {
        return mInterval;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        dest.writeString(mAdCounterKey);
        dest.writeInt(mMaxCount);
        dest.writeLong(mInterval.getSeconds());
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Checks whether the {@link KeyedFrequencyCap} objects contain the same information. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyedFrequencyCap)) return false;
        KeyedFrequencyCap that = (KeyedFrequencyCap) o;
        return mMaxCount == that.mMaxCount
                && mInterval.equals(that.mInterval)
                && mAdCounterKey.equals(that.mAdCounterKey);
    }

    /** Returns the hash of the {@link KeyedFrequencyCap} object's data. */
    @Override
    public int hashCode() {
        return Objects.hash(mAdCounterKey, mMaxCount, mInterval);
    }

    @Override
    public String toString() {
        return "KeyedFrequencyCap{"
                + "mAdCounterKey='"
                + mAdCounterKey
                + '\''
                + ", mMaxCount="
                + mMaxCount
                + ", mInterval="
                + mInterval
                + '}';
    }

    /** Builder for creating {@link KeyedFrequencyCap} objects. */
    public static final class Builder {
        @Nullable private String mAdCounterKey;
        private int mMaxCount;
        @Nullable private Duration mInterval;

        public Builder() {}

        /**
         * Sets the ad counter key the frequency cap applies to.
         *
         * <p>See {@link #getAdCounterKey()} for more information.
         */
        @NonNull
        public Builder setAdCounterKey(@NonNull String adCounterKey) {
            Objects.requireNonNull(adCounterKey, "Ad counter key must not be null");
            Preconditions.checkStringNotEmpty(adCounterKey, "Ad counter key must not be empty");
            mAdCounterKey = adCounterKey;
            return this;
        }

        /**
         * Sets the maximum count within the time interval for the frequency cap.
         *
         * <p>See {@link #getMaxCount()} for more information.
         */
        @NonNull
        public Builder setMaxCount(int maxCount) {
            Preconditions.checkArgument(maxCount > 0, "Max count must be positive and non-zero");
            mMaxCount = maxCount;
            return this;
        }

        /**
         * Sets the interval, as a {@link Duration} which will be truncated to the nearest second,
         * over which the frequency cap is calculated.
         *
         * <p>See {@link #getInterval()} for more information.
         */
        @NonNull
        public Builder setInterval(@NonNull Duration interval) {
            Objects.requireNonNull(interval, "Interval must not be null");
            Preconditions.checkArgument(
                    interval.getSeconds() > 0, "Interval in seconds must be positive and non-zero");
            mInterval = interval;
            return this;
        }

        /**
         * Builds and returns a {@link KeyedFrequencyCap} instance.
         *
         * @throws NullPointerException if the ad counter key or interval are null
         * @throws IllegalArgumentException if the ad counter key, max count, or interval are
         *     invalid
         */
        @NonNull
        public KeyedFrequencyCap build() throws NullPointerException, IllegalArgumentException {
            Objects.requireNonNull(mAdCounterKey, "Event key must be set");
            Preconditions.checkArgument(mMaxCount > 0, "Max count must be positive and non-zero");
            Objects.requireNonNull(mInterval, "Interval must not be null");

            return new KeyedFrequencyCap(this);
        }
    }
}
