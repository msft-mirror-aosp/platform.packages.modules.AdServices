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

package android.adservices.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * This class holds JSON that will be passed into a javascript auction function.
 *
 * @hide
 */
public final class AdSelectionSignals implements Parcelable {

    public static final AdSelectionSignals EMPTY = fromString("{}");

    @NonNull private final String mAdSelectionSignals;

    private AdSelectionSignals(@NonNull Parcel in) {
        this(in.readString());
    }

    private AdSelectionSignals(@NonNull String adSelectionSignals) {
        Objects.requireNonNull(adSelectionSignals);
        validate(adSelectionSignals);
        mAdSelectionSignals = adSelectionSignals;
    }

    @NonNull
    public static final Creator<AdSelectionSignals> CREATOR =
            new Creator<AdSelectionSignals>() {
                @Override
                public AdSelectionSignals createFromParcel(Parcel in) {
                    return new AdSelectionSignals(in);
                }

                @Override
                public AdSelectionSignals[] newArray(int size) {
                    return new AdSelectionSignals[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mAdSelectionSignals);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AdSelectionSignals
                && mAdSelectionSignals.equals(((AdSelectionSignals) o).getStringForm());
    }

    @Override
    public int hashCode() {
        return mAdSelectionSignals.hashCode();
    }

    @Override
    public String toString() {
        return getStringForm();
    }

    /**
     * Creates an AdSelectionSignals from a given JSON string.
     *
     * @param source Any valid JSON string to create the AdSelectionSignals with or null.
     * @return An AdSelectionSignals object wrapping the given String or null if the input was null
     */
    @NonNull
    public static AdSelectionSignals fromString(@Nullable String source) {
        if (source == null) {
            return null;
        }
        return new AdSelectionSignals(source);
    }

    /**
     * Creates an AdSelectionSignals from a given {@link JSONObject}.
     *
     * @param source Any valid {@link JSONObject} to create the {@link AdSelectionSignals} with.
     * @return An {@link AdSelectionSignals} object wrapping the given JSON
     */
    @NonNull
    public static AdSelectionSignals fromJson(@NonNull JSONObject source) {
        Objects.requireNonNull(source);
        return new AdSelectionSignals(source.toString());
    }

    /** @return The String form of the JSON wrapped by this class. */
    @NonNull
    public String getStringForm() {
        return mAdSelectionSignals;
    }

    /** @return The JSON form of the JSON wrapped by this class. */
    @NonNull
    public JSONObject getJSONForm() throws JSONException {
        return new JSONObject(mAdSelectionSignals);
    }

    private void validate(String inputString) {
        // TODO(b/238849930) Bring the existing validation function in here
    }
}
