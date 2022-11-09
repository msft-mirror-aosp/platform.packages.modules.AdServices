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

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import java.util.List;
import java.util.Objects;

/**
 * Represents input parameters to the compareAds API.
 *
 * @hide
 */
public class AdSelectionFromOutcomesInput {
    @NonNull private final List<AdSelectionOutcome> mAdOutcomes;
    @NonNull private final AdSelectionSignals mSelectionSignals;
    @NonNull private final Uri mSelectionLogicUri;
    @NonNull private final String mCallerPackageName;

    private AdSelectionFromOutcomesInput(
            List<AdSelectionOutcome> adOutcomes,
            AdSelectionSignals selectionSignals,
            Uri selectionLogicUri,
            String callerPackageName) {
        Objects.requireNonNull(adOutcomes);
        Objects.requireNonNull(selectionSignals);
        Objects.requireNonNull(selectionLogicUri);
        Objects.requireNonNull(callerPackageName);

        this.mAdOutcomes = adOutcomes;
        this.mSelectionSignals = selectionSignals;
        this.mSelectionLogicUri = selectionLogicUri;
        this.mCallerPackageName = callerPackageName;
    }

    @NonNull
    public List<AdSelectionOutcome> getAdOutcomes() {
        return mAdOutcomes;
    }

    @NonNull
    public AdSelectionSignals getSelectionSignals() {
        return mSelectionSignals;
    }

    @NonNull
    public Uri getSelectionLogicUri() {
        return mSelectionLogicUri;
    }

    @NonNull
    public String getCallerPackageName() {
        return mCallerPackageName;
    }

    /**
     * Builder for {@link AdSelectionFromOutcomesInput} objects.
     *
     * @hide
     */
    public static final class Builder {
        @Nullable private List<AdSelectionOutcome> mAdOutcomes;
        @Nullable private AdSelectionSignals mSelectionSignals;
        @Nullable private Uri mSelectionLogicUri;
        @Nullable private String mCallerPackageName;

        public Builder() {}

        /** Set the list of AdSelectionOutcome. */
        @NonNull
        public AdSelectionFromOutcomesInput.Builder setAdOutcomes(
                @NonNull List<AdSelectionOutcome> adOutcomes) {
            Objects.requireNonNull(adOutcomes);

            this.mAdOutcomes = adOutcomes;
            return this;
        }

        /** Set the AdSelectionSignals. */
        @NonNull
        public AdSelectionFromOutcomesInput.Builder setSelectionSignals(
                @NonNull AdSelectionSignals selectionSignals) {
            Objects.requireNonNull(selectionSignals);

            this.mSelectionSignals = selectionSignals;
            return this;
        }

        /** Set the Uri. */
        @NonNull
        public AdSelectionFromOutcomesInput.Builder setSelectionUri(
                @NonNull Uri selectionLogicUri) {
            Objects.requireNonNull(selectionLogicUri);

            this.mSelectionLogicUri = selectionLogicUri;
            return this;
        }

        /** Sets the caller's package name. */
        @NonNull
        public AdSelectionFromOutcomesInput.Builder setCallerPackageName(
                @NonNull String callerPackageName) {
            Objects.requireNonNull(callerPackageName);

            this.mCallerPackageName = callerPackageName;
            return this;
        }

        /** Builds a {@link AdSelectionInput} instance. */
        @NonNull
        public AdSelectionFromOutcomesInput build() {
            Objects.requireNonNull(mAdOutcomes);
            Objects.requireNonNull(mSelectionSignals);
            Objects.requireNonNull(mSelectionLogicUri);
            Objects.requireNonNull(mCallerPackageName);

            return new AdSelectionFromOutcomesInput(
                    mAdOutcomes, mSelectionSignals, mSelectionLogicUri, mCallerPackageName);
        }
    }
}
