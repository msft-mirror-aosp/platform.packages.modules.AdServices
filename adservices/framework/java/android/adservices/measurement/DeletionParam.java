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
 *
 * @hide
 */
public final class DeletionParam implements Parcelable {
    private final Uri mOriginUri;
    private final Instant mStart;
    private final Instant mEnd;
    private final AttributionSource mAttributionSource;

    /** Create a deletion request. */
    private DeletionParam(
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

    /** Unpack an DeletionRequest from a Parcel. */
    private DeletionParam(Parcel in) {
        mAttributionSource = AttributionSource.CREATOR.createFromParcel(in);
        boolean hasOrigin = in.readBoolean();
        if (hasOrigin) {
            mOriginUri = Uri.CREATOR.createFromParcel(in);
        } else {
            mOriginUri = null;
        }
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

    /** Creator for Paracelable (via reflection). */
    public static final @NonNull Parcelable.Creator<DeletionParam> CREATOR =
            new Parcelable.Creator<DeletionParam>() {
                @Override
                public DeletionParam createFromParcel(Parcel in) {
                    return new DeletionParam(in);
                }

                @Override
                public DeletionParam[] newArray(int size) {
                    return new DeletionParam[size];
                }
            };

    /** For Parcelable, no special marshalled objects. */
    public int describeContents() {
        return 0;
    }

    /** For Parcelable, write out to a Parcel in particular order. */
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        mAttributionSource.writeToParcel(out, flags);
        if (mOriginUri != null) {
            out.writeBoolean(true);
            mOriginUri.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
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

    /** Origin of the App / Publisher, or null for all origins. */
    public @Nullable Uri getOriginUri() {
        return mOriginUri;
    }

    /** Instant in time the deletion starts, or null if none. */
    public @Nullable Instant getStart() {
        return mStart;
    }

    /** Instant in time the deletion ends, or null if none. */
    public @Nullable Instant getEnd() {
        return mEnd;
    }

    /** AttributionSource of the deletion. */
    public @NonNull AttributionSource getAttributionSource() {
        return mAttributionSource;
    }

    /** A builder for {@link DeletionParam}. */
    public static final class Builder {
        private Uri mOriginUri;
        private Instant mStart;
        private Instant mEnd;
        private AttributionSource mAttributionSource;

        public Builder() {}

        /** See {@link DeletionParam#getOriginUri}. */
        public @NonNull Builder setOriginUri(@Nullable Uri origin) {
            mOriginUri = origin;
            return this;
        }

        /** See {@link DeletionParam#getStart}. */
        public @NonNull Builder setStart(@Nullable Instant start) {
            mStart = start;
            return this;
        }

        /** See {@link DeletionParam#getEnd}. */
        public @NonNull Builder setEnd(@Nullable Instant end) {
            mEnd = end;
            return this;
        }

        /** See {@link DeletionParam#getAttributionSource}. */
        public @NonNull Builder setAttributionSource(@NonNull AttributionSource attributionSource) {
            Objects.requireNonNull(attributionSource);
            mAttributionSource = attributionSource;
            return this;
        }

        /** Build the DeletionRequest. */
        public @NonNull DeletionParam build() {
            // Ensure attributionSource has been set,
            // throw IllegalArgumentException if null.
            if (mAttributionSource == null) {
                throw new IllegalArgumentException("attributionSource unset");
            }
            return new DeletionParam(mOriginUri, mStart, mEnd, mAttributionSource);
        }
    }
}
