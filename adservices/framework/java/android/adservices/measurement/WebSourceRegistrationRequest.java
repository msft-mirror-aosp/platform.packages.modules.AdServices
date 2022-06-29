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

/** Class to hold input to measurement source registration calls from web context. */
public final class WebSourceRegistrationRequest implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Parcelable.Creator<WebSourceRegistrationRequest> CREATOR =
            new Parcelable.Creator<WebSourceRegistrationRequest>() {
                @Override
                public WebSourceRegistrationRequest createFromParcel(Parcel in) {
                    return new WebSourceRegistrationRequest(in);
                }

                @Override
                public WebSourceRegistrationRequest[] newArray(int size) {
                    return new WebSourceRegistrationRequest[size];
                }
            };
    /** Registration info to fetch sources. */
    @NonNull private final List<WebSourceParams> mWebSourceParams;

    /** User interaction input event. */
    @Nullable private final InputEvent mInputEvent;

    /** Top level origin of publisher. */
    @NonNull private final Uri mTopOriginUri;

    /** App destination of the source. */
    @Nullable private final Uri mOsDestination;

    /** Web destination of the source. */
    @Nullable private final Uri mWebDestination;

    /** Verified destination by the caller. This is where the user actually landed. */
    @Nullable private final Uri mVerifiedDestination;

    /** Constructor for {@link WebSourceRegistrationRequest}. */
    private WebSourceRegistrationRequest(
            @NonNull List<WebSourceParams> webSourceParams,
            @Nullable InputEvent inputEvent,
            @NonNull Uri topOriginUri,
            @Nullable Uri osDestination,
            @Nullable Uri webDestination,
            @Nullable Uri verifiedDestination) {
        mWebSourceParams = webSourceParams;
        mInputEvent = inputEvent;
        mTopOriginUri = topOriginUri;
        mOsDestination = osDestination;
        mWebDestination = webDestination;
        mVerifiedDestination = verifiedDestination;
    }

    private WebSourceRegistrationRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        ArrayList<WebSourceParams> sourceRegistrations = new ArrayList<>();
        in.readList(
                sourceRegistrations, WebSourceParams.class.getClassLoader(), WebSourceParams.class);
        mWebSourceParams = sourceRegistrations;
        if (in.readBoolean()) {
            mInputEvent = InputEvent.CREATOR.createFromParcel(in);
        } else {
            mInputEvent = null;
        }
        mTopOriginUri = Uri.CREATOR.createFromParcel(in);
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
        if (!(o instanceof WebSourceRegistrationRequest)) return false;
        WebSourceRegistrationRequest that = (WebSourceRegistrationRequest) o;
        return Objects.equals(mWebSourceParams, that.mWebSourceParams)
                && Objects.equals(mInputEvent, that.mInputEvent)
                && Objects.equals(mTopOriginUri, that.mTopOriginUri)
                && Objects.equals(mOsDestination, that.mOsDestination)
                && Objects.equals(mWebDestination, that.mWebDestination)
                && Objects.equals(mVerifiedDestination, that.mVerifiedDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mWebSourceParams,
                mInputEvent,
                mTopOriginUri,
                mOsDestination,
                mWebDestination,
                mVerifiedDestination);
    }

    /** Getter for source params. */
    @NonNull
    public List<WebSourceParams> getSourceParams() {
        return mWebSourceParams;
    }

    /** Getter for input event. */
    @Nullable
    public InputEvent getInputEvent() {
        return mInputEvent;
    }

    /** Getter for top origin Uri. */
    @NonNull
    public Uri getTopOriginUri() {
        return mTopOriginUri;
    }

    /** Getter for OS destination. */
    @Nullable
    public Uri getOsDestination() {
        return mOsDestination;
    }

    /** Getter for web destination. */
    @Nullable
    public Uri getWebDestination() {
        return mWebDestination;
    }

    /** Getter for verified destination. */
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
        out.writeList(mWebSourceParams);
        if (mInputEvent != null) {
            out.writeBoolean(true);
            mInputEvent.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
        mTopOriginUri.writeToParcel(out, flags);
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

    /** Builder for {@link WebSourceRegistrationRequest}. */
    public static final class Builder {
        /** Registration info to fetch sources. */
        @NonNull private List<WebSourceParams> mWebSourceParams;
        /** User interaction input event. */
        @Nullable private InputEvent mInputEvent;
        /** Top level origin of publisher. */
        @NonNull private Uri mTopOriginUri;
        /** App destination of the source. */
        @Nullable private Uri mOsDestination;
        /** Web destination of the source. */
        @Nullable private Uri mWebDestination;
        /**
         * Verified destination by the caller. If available, sources should be checked against it.
         */
        @Nullable private Uri mVerifiedDestination;

        /**
         * Setter for source params. It is a required parameter and the provided list should not be
         * empty.
         *
         * @param webSourceParams source sourceParams
         * @return builder
         */
        @NonNull
        public Builder setSourceParams(@NonNull List<WebSourceParams> webSourceParams) {
            mWebSourceParams = webSourceParams;
            return this;
        }

        /**
         * Setter for input event.
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
         * Setter for top origin Uri. It is a required parameter.
         *
         * @param topOriginUri publisher top origin {@link Uri}
         * @return builder
         */
        @NonNull
        public Builder setTopOriginUri(@NonNull Uri topOriginUri) {
            mTopOriginUri = topOriginUri;
            return this;
        }

        /**
         * Setter for OS destination. At least one of OS destination or web destination is required.
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
         * Setter for web destination. At least one of OS destination or web destination is
         * required.
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
         * Setter for verified destination.
         *
         * @param verifiedDestination verified destination
         * @return builder
         */
        @NonNull
        public Builder setVerifiedDestination(@Nullable Uri verifiedDestination) {
            mVerifiedDestination = verifiedDestination;
            return this;
        }

        /** Pre-validates paramerters and builds {@link WebSourceRegistrationRequest}. */
        @NonNull
        public WebSourceRegistrationRequest build() {
            if (mWebSourceParams == null || mWebSourceParams.isEmpty()) {
                throw new IllegalArgumentException("source params not provided");
            }

            if (mOsDestination == null && mWebDestination == null) {
                throw new IllegalArgumentException(
                        "At least one of osDestination or webDestination needs to be provided");
            }

            Objects.requireNonNull(mTopOriginUri);

            return new WebSourceRegistrationRequest(
                    mWebSourceParams,
                    mInputEvent,
                    mTopOriginUri,
                    mOsDestination,
                    mWebDestination,
                    mVerifiedDestination);
        }
    }
}
