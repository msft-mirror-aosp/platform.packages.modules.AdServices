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
import android.view.InputEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to hold input to measurement source registration calls from embedded web context.
 *
 * @hide
 */
public final class EmbeddedWebSourceRegistrationRequest implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Parcelable.Creator<EmbeddedWebSourceRegistrationRequest> CREATOR =
            new Parcelable.Creator<EmbeddedWebSourceRegistrationRequest>() {
                @Override
                public EmbeddedWebSourceRegistrationRequest createFromParcel(Parcel in) {
                    return new EmbeddedWebSourceRegistrationRequest(in);
                }

                @Override
                public EmbeddedWebSourceRegistrationRequest[] newArray(int size) {
                    return new EmbeddedWebSourceRegistrationRequest[size];
                }
            };
    /** Registration info to fetch sources. */
    @NonNull private final List<SourceParams> mSourceParams;

    /** User interaction input event. */
    @Nullable private final InputEvent mInputEvent;

    /** Top level origin of publisher app. */
    @Nullable private final Uri mTopOriginUri;

    /** App destination of the source. */
    @Nullable private final Uri mOsDestination;

    /** Web destination of the source. */
    @Nullable private final Uri mWebDestination;

    /** Verified destination by the caller. If available, sources should be checked against it. */
    @Nullable private final Uri mVerifiedDestination;

    /** Constructor for {@link EmbeddedWebSourceRegistrationRequest}. */
    private EmbeddedWebSourceRegistrationRequest(
            @NonNull List<SourceParams> sourceParams,
            @Nullable InputEvent inputEvent,
            @Nullable Uri topOriginUri,
            @Nullable Uri osDestination,
            @Nullable Uri webDestination,
            @Nullable Uri verifiedDestination) {
        mSourceParams = sourceParams;
        mInputEvent = inputEvent;
        mTopOriginUri = topOriginUri;
        mOsDestination = osDestination;
        mWebDestination = webDestination;
        mVerifiedDestination = verifiedDestination;
    }

    /**
     * Unpack OSAttributionSourceRegistrationRequest from parcel.
     *
     * @param in parcel
     */
    private EmbeddedWebSourceRegistrationRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        ArrayList<SourceParams> sourceRegistrations = new ArrayList<>();
        in.readList(sourceRegistrations, SourceParams.class.getClassLoader(), SourceParams.class);
        mSourceParams = sourceRegistrations;
        if (in.readBoolean()) {
            mInputEvent = InputEvent.CREATOR.createFromParcel(in);
        } else {
            mInputEvent = null;
        }
        if (in.readBoolean()) {
            mTopOriginUri = Uri.CREATOR.createFromParcel(in);
        } else {
            mTopOriginUri = null;
        }
        if (in.readBoolean()) {
            mOsDestination = Uri.CREATOR.createFromParcel(in);
        } else {
            mOsDestination = null;
        }
        if (in.readBoolean()) {
            mWebDestination = Uri.CREATOR.createFromParcel(in);
        } else {
            mWebDestination = null;
        }
        if (in.readBoolean()) {
            mVerifiedDestination = Uri.CREATOR.createFromParcel(in);
        } else {
            mVerifiedDestination = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddedWebSourceRegistrationRequest)) return false;
        EmbeddedWebSourceRegistrationRequest that = (EmbeddedWebSourceRegistrationRequest) o;
        return Objects.equals(mSourceParams, that.mSourceParams)
                && Objects.equals(mInputEvent, that.mInputEvent)
                && Objects.equals(mTopOriginUri, that.mTopOriginUri)
                && Objects.equals(mOsDestination, that.mOsDestination)
                && Objects.equals(mWebDestination, that.mWebDestination)
                && Objects.equals(mVerifiedDestination, that.mVerifiedDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSourceParams,
                mInputEvent,
                mTopOriginUri,
                mOsDestination,
                mWebDestination,
                mVerifiedDestination);
    }

    /** Getter for {@link #mSourceParams}. */
    @NonNull
    public List<SourceParams> getSourceParams() {
        return mSourceParams;
    }

    /** Getter for {@link #mInputEvent}. */
    @Nullable
    public InputEvent getInputEvent() {
        return mInputEvent;
    }

    /** Getter for {@link #mTopOriginUri}. */
    @Nullable
    public Uri getTopOriginUri() {
        return mTopOriginUri;
    }

    /** Getter for {@link #mOsDestination}. */
    @Nullable
    public Uri getOsDestination() {
        return mOsDestination;
    }

    /** Getter for {@link #mWebDestination}. */
    @Nullable
    public Uri getWebDestination() {
        return mWebDestination;
    }

    /** Getter for {@link #mVerifiedDestination}. */
    @Nullable
    public Uri getVerifiedDestination() {
        return mVerifiedDestination;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        out.writeList(mSourceParams);
        if (mInputEvent != null) {
            out.writeBoolean(true);
            mInputEvent.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
        if (mTopOriginUri != null) {
            out.writeBoolean(true);
            mTopOriginUri.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
        if (mOsDestination != null) {
            out.writeBoolean(true);
            mOsDestination.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
        if (mWebDestination != null) {
            out.writeBoolean(true);
            mWebDestination.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
        if (mVerifiedDestination != null) {
            out.writeBoolean(true);
            mVerifiedDestination.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
    }

    /** Builder for {@link EmbeddedWebSourceRegistrationRequest}. */
    public static final class Builder {
        /** Registration info to fetch sources. */
        @NonNull private List<SourceParams> mSourceParams;
        /** User interaction input event. */
        @Nullable private InputEvent mInputEvent;
        /** Top level origin of publisher app. */
        @Nullable private Uri mTopOriginUri;
        /** App destination of the source. */
        @Nullable private Uri mOsDestination;
        /** Web destination of the source. */
        @Nullable private Uri mWebDestination;
        /**
         * Verified destination by the caller. If available, sources should be checked against it.
         */
        @Nullable private Uri mVerifiedDestination;

        /**
         * Setter for {@link #mSourceParams}.
         *
         * @param sourceParams source sourceParams
         * @return builder
         */
        @NonNull
        public Builder setSourceParams(@NonNull List<SourceParams> sourceParams) {
            mSourceParams = sourceParams;
            return this;
        }

        /**
         * Setter for input {@link #mInputEvent}.
         *
         * @param inputEvent user input event
         * @return builder
         */
        @NonNull
        public Builder setInputEvent(@Nullable InputEvent inputEvent) {
            mInputEvent = inputEvent;
            return this;
        }

        /**
         * Setter for {@link #mTopOriginUri}.
         *
         * @param topOriginUri publisher top origin {@link Uri}
         * @return builder
         */
        @NonNull
        public Builder setTopOriginUri(@Nullable Uri topOriginUri) {
            mTopOriginUri = topOriginUri;
            return this;
        }

        /**
         * Setter for {@link #mOsDestination}.
         *
         * @param osDestination app destination {@link Uri}
         * @return builder
         */
        @NonNull
        public Builder setOsDestination(@NonNull Uri osDestination) {
            mOsDestination = osDestination;
            return this;
        }

        /**
         * Setter for {@link #mWebDestination}.
         *
         * @param webDestination web destination {@link Uri}
         * @return builder
         */
        @NonNull
        public Builder setWebDestination(@NonNull Uri webDestination) {
            mWebDestination = webDestination;
            return this;
        }

        /**
         * Setter for {@link #mVerifiedDestination}.
         *
         * @param verifiedDestination verified destination
         * @return builder
         */
        @NonNull
        public Builder setVerifiedDestination(@Nullable Uri verifiedDestination) {
            mVerifiedDestination = verifiedDestination;
            return this;
        }

        /** Pre-validates paramerters and builds {@link EmbeddedWebSourceRegistrationRequest}. */
        @NonNull
        public EmbeddedWebSourceRegistrationRequest build() {
            if (mSourceParams == null || mSourceParams.isEmpty()) {
                throw new IllegalArgumentException("source params not provided");
            }

            if (mOsDestination == null && mWebDestination == null) {
                throw new IllegalArgumentException(
                        "At least one of osDestination or webDestination needs to be provided");
            }

            return new EmbeddedWebSourceRegistrationRequest(
                    mSourceParams,
                    mInputEvent,
                    mTopOriginUri,
                    mOsDestination,
                    mWebDestination,
                    mVerifiedDestination);
        }
    }
}
