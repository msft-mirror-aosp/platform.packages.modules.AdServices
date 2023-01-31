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

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * This class holds JSON that will be passed to ad-techs in interaction reporting.
 *
 * @hide
 */
public class InteractionData implements Parcelable {
    @NonNull private final String mInteractionData;

    @NonNull
    public static final Creator<InteractionData> CREATOR =
            new Creator<InteractionData>() {
                @Override
                public InteractionData createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);

                    return new InteractionData(in);
                }

                @Override
                public InteractionData[] newArray(int size) {
                    return new InteractionData[size];
                }
            };

    private InteractionData(@NonNull String source) {
        Objects.requireNonNull(source);
        validate(source);

        mInteractionData = source;
    }

    private InteractionData(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mInteractionData = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeString(mInteractionData);
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @return the data's String form data size in bytes.
     * @hide
     */
    public int getSizeInBytes() {
        return this.mInteractionData.getBytes().length;
    }

    /** @return The String form of the JSON wrapped by this class. */
    @Override
    @NonNull
    public String toString() {
        return mInteractionData;
    }

    /**
     * Validates that {@code interactionData} parses properly into a valid JSON object.
     *
     * @throws IllegalArgumentException if {@code interactionData} fails to parse into a valid JSON
     *     object.
     */
    private static void validate(@NonNull String interactionData) throws IllegalArgumentException {
        try {
            new JSONObject(interactionData);
        } catch (JSONException e) {
            throw new IllegalArgumentException(
                    "Interaction Data does not parse properly into JSON!");
        }
    }

    /**
     * Creates an {@link InteractionData} object from a String that parses into a valid JSON object.
     *
     * @param source String to create the {@link InteractionData} with.
     * @return A {@link InteractionData} object wrapping the given String.
     * @throws IllegalArgumentException if {@code source} fails to parse into a valid JSON object.
     */
    @NonNull
    public static InteractionData fromString(@NonNull String source) {
        Objects.requireNonNull(source);

        return new InteractionData(source);
    }
}
