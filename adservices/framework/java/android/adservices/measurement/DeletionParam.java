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
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to hold deletion related request. This is an internal class for communication between the
 * {@link MeasurementManager} and {@link IMeasurementService} impl.
 *
 * @hide
 */
public final class DeletionParam implements Parcelable {
    private final List<Uri> mOriginUris;
    private final List<Uri> mDomainUris;
    private final Instant mStart;
    private final Instant mEnd;
    private final String mPackageName;
    @DeletionRequest.DeletionMode private final int mDeletionMode;
    @DeletionRequest.MatchBehavior private final int mMatchBehavior;

    private DeletionParam(@NonNull Builder builder) {
        mOriginUris = builder.mOriginUris;
        mDomainUris = builder.mDomainUris;
        mDeletionMode = builder.mDeletionMode;
        mMatchBehavior = builder.mMatchBehavior;
        mStart = builder.mStart;
        mEnd = builder.mEnd;
        mPackageName = builder.mPackageName;
    }

    /** Unpack an DeletionRequest from a Parcel. */
    private DeletionParam(Parcel in) {
        mPackageName = in.readString();

        mDomainUris = new ArrayList<>();
        in.readTypedList(mDomainUris, Uri.CREATOR);

        mOriginUris = new ArrayList<>();
        in.readTypedList(mOriginUris, Uri.CREATOR);

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

        mDeletionMode = in.readInt();
        mMatchBehavior = in.readInt();
    }

    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Parcelable.Creator<DeletionParam> CREATOR =
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
        out.writeString(mPackageName);

        out.writeTypedList(mDomainUris);

        out.writeTypedList(mOriginUris);

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

        out.writeInt(mDeletionMode);

        out.writeInt(mMatchBehavior);
    }

    /**
     * Publisher/Advertiser Origins for which data should be deleted. These will be matched as-is.
     */
    @NonNull
    public List<Uri> getOriginUris() {
        return mOriginUris;
    }

    /**
     * Publisher/Advertiser domains for which data should be deleted. These will be pattern matched
     * with regex SCHEME://(.*\.|)SITE .
     */
    @NonNull
    public List<Uri> getDomainUris() {
        return mDomainUris;
    }

    /** Deletion mode for matched records. */
    @DeletionRequest.DeletionMode
    public int getDeletionMode() {
        return mDeletionMode;
    }

    /** Match behavior for provided origins/domains. */
    @DeletionRequest.MatchBehavior
    public int getMatchBehavior() {
        return mMatchBehavior;
    }

    /** Instant in time the deletion starts, or null if none. */
    @Nullable
    public Instant getStart() {
        return mStart;
    }

    /** Instant in time the deletion ends, or null if none. */
    @Nullable
    public Instant getEnd() {
        return mEnd;
    }

    /** Client's package name used for the deletion. */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /** A builder for {@link DeletionParam}. */
    public static final class Builder {
        private List<Uri> mOriginUris;
        private List<Uri> mDomainUris;
        private Instant mStart;
        private Instant mEnd;
        private String mPackageName;
        @DeletionRequest.DeletionMode private int mDeletionMode;
        @DeletionRequest.MatchBehavior private int mMatchBehavior;

        public Builder() {}

        /** See {@link DeletionParam#getOriginUris()}. */
        @NonNull
        public Builder setOriginUris(@NonNull List<Uri> originUris) {
            mOriginUris = originUris;
            return this;
        }

        /** See {@link DeletionParam#getDomainUris()}. */
        @NonNull
        public Builder setDomainUris(@NonNull List<Uri> domainUris) {
            mDomainUris = domainUris;
            return this;
        }

        /** See {@link DeletionParam#getDeletionMode()}. */
        @NonNull
        public Builder setDeletionMode(@DeletionRequest.DeletionMode int deletionMode) {
            mDeletionMode = deletionMode;
            return this;
        }

        /** See {@link DeletionParam#getDeletionMode()}. */
        @NonNull
        public Builder setMatchBehavior(@DeletionRequest.MatchBehavior int matchBehavior) {
            mMatchBehavior = matchBehavior;
            return this;
        }

        /** See {@link DeletionParam#getStart}. */
        @NonNull
        public Builder setStart(@Nullable Instant start) {
            mStart = start;
            return this;
        }

        /** See {@link DeletionParam#getEnd}. */
        @NonNull
        public Builder setEnd(@Nullable Instant end) {
            mEnd = end;
            return this;
        }

        /** See {@link DeletionParam#getPackageName()}. */
        @NonNull
        public Builder setPackageName(@NonNull String packageName) {
            Objects.requireNonNull(packageName);
            mPackageName = packageName;
            return this;
        }

        /** Build the DeletionRequest. */
        @NonNull
        public DeletionParam build() {
            if (mPackageName == null || mOriginUris == null || mDomainUris == null) {
                throw new IllegalArgumentException(
                        "PackageName, OriginUris, or DomainUris is null");
            }
            return new DeletionParam(this);
        }
    }
}
