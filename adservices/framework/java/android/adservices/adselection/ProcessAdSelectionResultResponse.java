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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Represents the response from processAdSelectionResult.
 *
 * @hide
 */
public class ProcessAdSelectionResultResponse implements Parcelable {
    private final long mAdSelectionId;
    @NonNull private final Uri mAdRenderUri;

    public static final Creator<ProcessAdSelectionResultResponse> CREATOR =
            new Creator<>() {
                @Override
                public ProcessAdSelectionResultResponse createFromParcel(Parcel in) {
                    Objects.requireNonNull(in);

                    return new ProcessAdSelectionResultResponse(in);
                }

                @Override
                public ProcessAdSelectionResultResponse[] newArray(int size) {
                    return new ProcessAdSelectionResultResponse[size];
                }
            };

    private ProcessAdSelectionResultResponse(long adSelectionId, @NonNull Uri adRenderUri) {
        Objects.requireNonNull(adRenderUri);

        this.mAdSelectionId = adSelectionId;
        this.mAdRenderUri = adRenderUri;
    }

    private ProcessAdSelectionResultResponse(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mAdSelectionId = in.readLong();
        this.mAdRenderUri = Uri.CREATOR.createFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Returns the adSelectionId that identifies the AdSelection. */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /** Returns the adSelectionData that is collected from device. */
    public Uri getAdRenderUri() {
        return mAdRenderUri;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        Objects.requireNonNull(mAdRenderUri);

        dest.writeLong(mAdSelectionId);
        mAdRenderUri.writeToParcel(dest, flags);
    }

    /**
     * Builder for {@link ProcessAdSelectionResultResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        private long mAdSelectionId;
        @Nullable private Uri mAdRenderUri;

        public Builder() {}

        /** Sets the adSelectionId. */
        @NonNull
        public ProcessAdSelectionResultResponse.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Sets the adSelectionData. */
        @NonNull
        public ProcessAdSelectionResultResponse.Builder setAdRenderUri(Uri adRenderUri) {
            Objects.requireNonNull(adRenderUri);

            this.mAdRenderUri = adRenderUri;
            return this;
        }

        /**
         * Builds a {@link ProcessAdSelectionResultResponse} instance.
         *
         * @throws IllegalArgumentException if the adSelectionIid is not set
         * @throws NullPointerException if the RenderUri is null
         */
        @NonNull
        public ProcessAdSelectionResultResponse build() {
            Objects.requireNonNull(mAdRenderUri);
            Preconditions.checkArgument(
                    mAdSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);

            return new ProcessAdSelectionResultResponse(mAdSelectionId, mAdRenderUri);
        }
    }
}
