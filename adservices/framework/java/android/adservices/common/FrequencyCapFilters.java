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

import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * A container for the ad filters that are based on frequency caps.
 *
 * <p>Frequency caps filters combine an event type with a list of {@link KeyedFrequencyCap} objects
 * to define a collection of ad filters. If any of these frequency caps are exceeded for a given ad,
 * the ad will be removed from the group of ads submitted to a buyer adtech's bidding function.
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
                AD_EVENT_TYPE_INVALID,
                AD_EVENT_TYPE_WIN,
                AD_EVENT_TYPE_IMPRESSION,
                AD_EVENT_TYPE_VIEW,
                AD_EVENT_TYPE_CLICK
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AdEventType {}

    /** @hide */
    public static final int AD_EVENT_TYPE_INVALID = -1;

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
    /** @hide */
    @VisibleForTesting public static final String WIN_EVENTS_FIELD_NAME = "win";
    /** @hide */
    @VisibleForTesting public static final String IMPRESSION_EVENTS_FIELD_NAME = "impression";
    /** @hide */
    @VisibleForTesting public static final String VIEW_EVENTS_FIELD_NAME = "view";
    /** @hide */
    @VisibleForTesting public static final String CLICK_EVENTS_FIELD_NAME = "click";

    @NonNull private final List<KeyedFrequencyCap> mKeyedFrequencyCapsForWinEvents;
    @NonNull private final List<KeyedFrequencyCap> mKeyedFrequencyCapsForImpressionEvents;
    @NonNull private final List<KeyedFrequencyCap> mKeyedFrequencyCapsForViewEvents;
    @NonNull private final List<KeyedFrequencyCap> mKeyedFrequencyCapsForClickEvents;

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

        mKeyedFrequencyCapsForWinEvents = builder.mKeyedFrequencyCapsForWinEvents;
        mKeyedFrequencyCapsForImpressionEvents = builder.mKeyedFrequencyCapsForImpressionEvents;
        mKeyedFrequencyCapsForViewEvents = builder.mKeyedFrequencyCapsForViewEvents;
        mKeyedFrequencyCapsForClickEvents = builder.mKeyedFrequencyCapsForClickEvents;
    }

    private FrequencyCapFilters(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mKeyedFrequencyCapsForWinEvents = new ArrayList<>();
        mKeyedFrequencyCapsForImpressionEvents = new ArrayList<>();
        mKeyedFrequencyCapsForViewEvents = new ArrayList<>();
        mKeyedFrequencyCapsForClickEvents = new ArrayList<>();

        in.readTypedList(mKeyedFrequencyCapsForWinEvents, KeyedFrequencyCap.CREATOR);
        in.readTypedList(mKeyedFrequencyCapsForImpressionEvents, KeyedFrequencyCap.CREATOR);
        in.readTypedList(mKeyedFrequencyCapsForViewEvents, KeyedFrequencyCap.CREATOR);
        in.readTypedList(mKeyedFrequencyCapsForClickEvents, KeyedFrequencyCap.CREATOR);
    }

    /**
     * Gets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
     * #AD_EVENT_TYPE_WIN} event type.
     *
     * <p>These frequency caps apply to events for ads that were selected as winners in ad
     * selection. Winning ads are used to automatically increment the associated counter keys on the
     * win event type.
     */
    @NonNull
    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForWinEvents() {
        return mKeyedFrequencyCapsForWinEvents;
    }

    /**
     * Gets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
     * #AD_EVENT_TYPE_IMPRESSION} event type.
     *
     * <p>These frequency caps apply to events which correlate to an impression as interpreted by an
     * adtech. Note that events are not automatically counted when calling {@link
     * android.adservices.adselection.AdSelectionManager#reportImpression(ReportImpressionRequest,
     * Executor, OutcomeReceiver)}.
     */
    @NonNull
    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForImpressionEvents() {
        return mKeyedFrequencyCapsForImpressionEvents;
    }

    /**
     * Gets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
     * #AD_EVENT_TYPE_VIEW} event type.
     *
     * <p>These frequency caps apply to events which correlate to a view as interpreted by an
     * adtech.
     */
    @NonNull
    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForViewEvents() {
        return mKeyedFrequencyCapsForViewEvents;
    }

    /**
     * Gets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
     * #AD_EVENT_TYPE_CLICK} event type.
     *
     * <p>These frequency caps apply to events which correlate to a click as interpreted by an
     * adtech.
     */
    @NonNull
    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForClickEvents() {
        return mKeyedFrequencyCapsForClickEvents;
    }

    /**
     * @return The estimated size of this object, in bytes.
     * @hide
     */
    public int getSizeInBytes() {
        return getSizeInBytesOfFcapList(mKeyedFrequencyCapsForWinEvents)
                + getSizeInBytesOfFcapList(mKeyedFrequencyCapsForImpressionEvents)
                + getSizeInBytesOfFcapList(mKeyedFrequencyCapsForViewEvents)
                + getSizeInBytesOfFcapList(mKeyedFrequencyCapsForClickEvents);
    }

    private int getSizeInBytesOfFcapList(List<KeyedFrequencyCap> fcaps) {
        int toReturn = 0;
        for (final KeyedFrequencyCap fcap : fcaps) {
            toReturn += fcap.getSizeInBytes();
        }
        return toReturn;
    }

    /**
     * A JSON serializer.
     *
     * @return A JSON serialization of this object.
     * @hide
     */
    public JSONObject toJson() throws JSONException {
        JSONObject toReturn = new JSONObject();
        toReturn.put(WIN_EVENTS_FIELD_NAME, fcapSetToJsonArray(mKeyedFrequencyCapsForWinEvents));
        toReturn.put(
                IMPRESSION_EVENTS_FIELD_NAME,
                fcapSetToJsonArray(mKeyedFrequencyCapsForImpressionEvents));
        toReturn.put(VIEW_EVENTS_FIELD_NAME, fcapSetToJsonArray(mKeyedFrequencyCapsForViewEvents));
        toReturn.put(
                CLICK_EVENTS_FIELD_NAME, fcapSetToJsonArray(mKeyedFrequencyCapsForClickEvents));
        return toReturn;
    }

    private static JSONArray fcapSetToJsonArray(List<KeyedFrequencyCap> fcapSet)
            throws JSONException {
        JSONArray toReturn = new JSONArray();
        for (KeyedFrequencyCap fcap : fcapSet) {
            toReturn.put(fcap.toJson());
        }
        return toReturn;
    }

    /**
     * A JSON de-serializer.
     *
     * @param json A JSON representation of an {@link FrequencyCapFilters} object as would be
     *     generated by {@link #toJson()}.
     * @return An {@link FrequencyCapFilters} object generated from the given JSON.
     * @hide
     */
    public static FrequencyCapFilters fromJson(JSONObject json) throws JSONException {
        Builder builder = new Builder();
        if (json.has(WIN_EVENTS_FIELD_NAME)) {
            builder.setKeyedFrequencyCapsForWinEvents(
                    jsonArrayToFcapList(json.getJSONArray(WIN_EVENTS_FIELD_NAME)));
        }
        if (json.has(IMPRESSION_EVENTS_FIELD_NAME)) {
            builder.setKeyedFrequencyCapsForImpressionEvents(
                    jsonArrayToFcapList(json.getJSONArray(IMPRESSION_EVENTS_FIELD_NAME)));
        }
        if (json.has(VIEW_EVENTS_FIELD_NAME)) {
            builder.setKeyedFrequencyCapsForViewEvents(
                    jsonArrayToFcapList(json.getJSONArray(VIEW_EVENTS_FIELD_NAME)));
        }
        if (json.has(CLICK_EVENTS_FIELD_NAME)) {
            builder.setKeyedFrequencyCapsForClickEvents(
                    jsonArrayToFcapList(json.getJSONArray(CLICK_EVENTS_FIELD_NAME)));
        }
        return builder.build();
    }

    private static List<KeyedFrequencyCap> jsonArrayToFcapList(JSONArray json)
            throws JSONException {
        List<KeyedFrequencyCap> toReturn = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            toReturn.add(KeyedFrequencyCap.fromJson(json.getJSONObject(i)));
        }
        return toReturn;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        dest.writeTypedList(mKeyedFrequencyCapsForWinEvents);
        dest.writeTypedList(mKeyedFrequencyCapsForImpressionEvents);
        dest.writeTypedList(mKeyedFrequencyCapsForViewEvents);
        dest.writeTypedList(mKeyedFrequencyCapsForClickEvents);
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
        return mKeyedFrequencyCapsForWinEvents.equals(that.mKeyedFrequencyCapsForWinEvents)
                && mKeyedFrequencyCapsForImpressionEvents.equals(
                        that.mKeyedFrequencyCapsForImpressionEvents)
                && mKeyedFrequencyCapsForViewEvents.equals(that.mKeyedFrequencyCapsForViewEvents)
                && mKeyedFrequencyCapsForClickEvents.equals(that.mKeyedFrequencyCapsForClickEvents);
    }

    /** Returns the hash of the {@link FrequencyCapFilters} object's data. */
    @Override
    public int hashCode() {
        return Objects.hash(
                mKeyedFrequencyCapsForWinEvents,
                mKeyedFrequencyCapsForImpressionEvents,
                mKeyedFrequencyCapsForViewEvents,
                mKeyedFrequencyCapsForClickEvents);
    }

    @Override
    public String toString() {
        return "FrequencyCapFilters{"
                + "mKeyedFrequencyCapsForWinEvents="
                + mKeyedFrequencyCapsForWinEvents
                + ", mKeyedFrequencyCapsForImpressionEvents="
                + mKeyedFrequencyCapsForImpressionEvents
                + ", mKeyedFrequencyCapsForViewEvents="
                + mKeyedFrequencyCapsForViewEvents
                + ", mKeyedFrequencyCapsForClickEvents="
                + mKeyedFrequencyCapsForClickEvents
                + '}';
    }

    /** Builder for creating {@link FrequencyCapFilters} objects. */
    public static final class Builder {
        @NonNull
        private List<KeyedFrequencyCap> mKeyedFrequencyCapsForWinEvents = new ArrayList<>();

        @NonNull
        private List<KeyedFrequencyCap> mKeyedFrequencyCapsForImpressionEvents = new ArrayList<>();

        @NonNull
        private List<KeyedFrequencyCap> mKeyedFrequencyCapsForViewEvents = new ArrayList<>();

        @NonNull
        private List<KeyedFrequencyCap> mKeyedFrequencyCapsForClickEvents = new ArrayList<>();

        public Builder() {}

        /**
         * Sets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
         * #AD_EVENT_TYPE_WIN} event type.
         *
         * <p>See {@link #getKeyedFrequencyCapsForWinEvents()} for more information.
         */
        @NonNull
        public Builder setKeyedFrequencyCapsForWinEvents(
                @NonNull List<KeyedFrequencyCap> keyedFrequencyCapsForWinEvents) {
            Objects.requireNonNull(keyedFrequencyCapsForWinEvents);
            mKeyedFrequencyCapsForWinEvents = keyedFrequencyCapsForWinEvents;
            return this;
        }

        /**
         * Sets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
         * #AD_EVENT_TYPE_IMPRESSION} event type.
         *
         * <p>See {@link #getKeyedFrequencyCapsForImpressionEvents()} for more information.
         */
        @NonNull
        public Builder setKeyedFrequencyCapsForImpressionEvents(
                @NonNull List<KeyedFrequencyCap> keyedFrequencyCapsForImpressionEvents) {
            Objects.requireNonNull(keyedFrequencyCapsForImpressionEvents);
            mKeyedFrequencyCapsForImpressionEvents = keyedFrequencyCapsForImpressionEvents;
            return this;
        }

        /**
         * Sets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
         * #AD_EVENT_TYPE_VIEW} event type.
         *
         * <p>See {@link #getKeyedFrequencyCapsForViewEvents()} for more information.
         */
        @NonNull
        public Builder setKeyedFrequencyCapsForViewEvents(
                @NonNull List<KeyedFrequencyCap> keyedFrequencyCapsForViewEvents) {
            Objects.requireNonNull(keyedFrequencyCapsForViewEvents);
            mKeyedFrequencyCapsForViewEvents = keyedFrequencyCapsForViewEvents;
            return this;
        }

        /**
         * Sets the list of {@link KeyedFrequencyCap} objects that will filter on the {@link
         * #AD_EVENT_TYPE_CLICK} event type.
         *
         * <p>See {@link #getKeyedFrequencyCapsForClickEvents()} for more information.
         */
        @NonNull
        public Builder setKeyedFrequencyCapsForClickEvents(
                @NonNull List<KeyedFrequencyCap> keyedFrequencyCapsForClickEvents) {
            Objects.requireNonNull(keyedFrequencyCapsForClickEvents);
            mKeyedFrequencyCapsForClickEvents = keyedFrequencyCapsForClickEvents;
            return this;
        }

        /** Builds and returns a {@link FrequencyCapFilters} instance. */
        @NonNull
        public FrequencyCapFilters build() {
            return new FrequencyCapFilters(this);
        }
    }
}
