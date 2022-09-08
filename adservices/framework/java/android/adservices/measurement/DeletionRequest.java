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
package android.adservices.measurement;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.time.Instant;
import java.util.Objects;


/**
 * Class to hold deletion related request.
 * @hide
 */
public final class DeletionRequest implements Parcelable {
    private final Uri mOriginUri;
    private final Instant mStart;
    private final Instant mEnd;
    private final AttributionSource mAttributionSource;

    /**
     * Create a deletion request.
     */
    private DeletionRequest(
            @Nullable Uri originUri,
            @Nullable Instant start,
            @Nullable Instant end,
            @NonNull AttributionSource attributionSource) {
        Objects.requireNonNull(attributionSource);
        mOriginUri = originUri;
        mStart = start;
        mEnd = end;
        mAttributionSource = attributionSource;
    }

    /**
     * Unpack an DeletionRequest from a Parcel.
     */
    private DeletionRequest(Parcel in) {
        mOriginUri = Uri.CREATOR.createFromParcel(in);
        mAttributionSource = AttributionSource.CREATOR.createFromParcel(in);
        boolean hasStart = in.readBoolean();
        if (hasStart) {
            mStart = Instant.ofEpochMilli(in.readLong());
        } else {
            mStart = null;
        }
        boolean hasEnd = in.readBoolean();
        if (hasEnd) {
            mEnd = Instant.ofEpochMilli(in.readLong());
        } else {
            mEnd = null;
        }
    }

    /**
     * Creator for Paracelable (via reflection).
     */
    public static final @NonNull Parcelable.Creator<DeletionRequest> CREATOR =
            new Parcelable.Creator<DeletionRequest>() {
                @Override public DeletionRequest createFromParcel(Parcel in) {
                    return new DeletionRequest(in);
                }

                @Override public DeletionRequest[] newArray(int size) {
                    return new DeletionRequest[size];
                }
            };

    /**
     * For Parcelable, no special marshalled objects.
     */
    public int describeContents() {
        return 0;
    }

    /**
     * For Parcelable, write out to a Parcel in particular order.
     */
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        mOriginUri.writeToParcel(out, flags);
        mAttributionSource.writeToParcel(out, flags);
        if (mStart != null) {
            out.writeBoolean(true);
            out.writeLong(mStart.toEpochMilli());
        } else {
            out.writeBoolean(false);
        }
        if (mEnd != null) {
            out.writeBoolean(true);
            out.writeLong(mEnd.toEpochMilli());
        } else {
            out.writeBoolean(false);
        }
    }

    /**
     * Origin of the App / Publisher, or null for all origins.
     */
    public @Nullable Uri getOriginUri() {
        return mOriginUri;
    }

    /**
     * Instant in time the deletion starts, or null if none.
     */
    public @Nullable Instant getStart() {
        return mStart;
    }

    /**
     * Instant in time the deletion ends, or null if none.
     */
    public @Nullable Instant getEnd() {
        return mEnd;
    }

    /**
     * AttributionSource of the deletion.
     */
    public @NonNull AttributionSource getAttributionSource() {
        return mAttributionSource;
    }

    /**
     * A builder for {@link DeletionRequest}.
     */
    public static final class Builder {
        private Uri mOriginUri;
        private Instant mStart;
        private Instant mEnd;
        private AttributionSource mAttributionSource;

        public Builder() {
            mOriginUri = Uri.EMPTY;
        }

        /**
         * See {@link DeletionRequest#getOriginUri}.
         */
        public @NonNull Builder setOriginUri(@NonNull Uri origin) {
            Objects.requireNonNull(origin);
            mOriginUri = origin;
            return this;
        }

        /**
         * See {@link DeletionRequest#getStart}.
         */
        public @NonNull Builder setStart(@Nullable Instant start) {
            Objects.requireNonNull(start);
            mStart = start;
            return this;
        }

        /**
         * See {@link DeletionRequest#getEnd}.
         */
        public @NonNull Builder setEnd(@Nullable Instant end) {
            Objects.requireNonNull(end);
            mEnd = end;
            return this;
        }

        /**
         * See {@link DeletionRequest#getAttributionSource}.
         */
        public @NonNull Builder setAttributionSource(
                @NonNull AttributionSource attributionSource) {
            Objects.requireNonNull(attributionSource);
            mAttributionSource = attributionSource;
            return this;
        }

        /**
         * Build the DeletionRequest.
         */
        public @NonNull DeletionRequest build() {
            // Ensure attributionSource has been set,
            // throw IllegalArgumentException if null.
            if (mAttributionSource == null) {
                throw new IllegalArgumentException("attributionSource unset");
            }
            return new DeletionRequest(
                    mOriginUri, mStart, mEnd, mAttributionSource);
        }
    }
}
