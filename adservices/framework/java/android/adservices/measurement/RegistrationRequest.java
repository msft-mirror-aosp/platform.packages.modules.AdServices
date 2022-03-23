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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.InputEvent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;


/**
 * Class to hold input to measurement registration calls.
 * @hide
 */
public final class RegistrationRequest implements Parcelable {
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
       INVALID,
       REGISTER_SOURCE,
       REGISTER_TRIGGER,
    })
    public @interface RegistrationType {}
    /** Invalid registration type used as a default. */
    public static final int INVALID = 0;
    /** A request to register an Attribution Source event
     * (NOTE: adservices type not android.context.AttributionSource). */
    public static final int REGISTER_SOURCE = 1;
    /** A request to register a trigger event. */
    public static final int REGISTER_TRIGGER = 2;

    private final @RegistrationType int mRegistrationType;
    private final Uri mRegistrationUri;
    private final Uri mTopOriginUri;
    private final Uri mReferrerUri;
    private final InputEvent mInputEvent;
    private final AttributionSource mAttributionSource;

    /**
     * Create a registration request.
     */
    private RegistrationRequest(
            @RegistrationType int registrationType,
            @NonNull Uri registrationUri,
            @NonNull Uri topOriginUri,
            @NonNull Uri referrerUri,
            @Nullable InputEvent inputEvent,
            @NonNull AttributionSource attributionSource) {
        Objects.requireNonNull(registrationUri);
        Objects.requireNonNull(topOriginUri);
        Objects.requireNonNull(referrerUri);
        Objects.requireNonNull(attributionSource);
        mRegistrationType = registrationType;
        mRegistrationUri = registrationUri;
        mTopOriginUri = topOriginUri;
        mReferrerUri = referrerUri;
        mInputEvent = inputEvent;
        mAttributionSource = attributionSource;
    }

    /**
     * Unpack an RegistrationRequest from a Parcel.
     */
    private RegistrationRequest(Parcel in) {
        mRegistrationType = in.readInt();
        mRegistrationUri = Uri.CREATOR.createFromParcel(in);
        mTopOriginUri = Uri.CREATOR.createFromParcel(in);
        mReferrerUri = Uri.CREATOR.createFromParcel(in);
        mAttributionSource = AttributionSource.CREATOR.createFromParcel(in);
        boolean hasInputEvent = in.readBoolean();
        if (hasInputEvent) {
            mInputEvent = InputEvent.CREATOR.createFromParcel(in);
        } else {
            mInputEvent = null;
        }
    }

    /**
     * Creator for Paracelable (via reflection).
     */
    public static final @NonNull Parcelable.Creator<RegistrationRequest> CREATOR =
            new Parcelable.Creator<RegistrationRequest>() {
                @Override public RegistrationRequest createFromParcel(Parcel in) {
                    return new RegistrationRequest(in);
                }

                @Override public RegistrationRequest[] newArray(int size) {
                    return new RegistrationRequest[size];
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
        out.writeInt(mRegistrationType);
        mRegistrationUri.writeToParcel(out, flags);
        mTopOriginUri.writeToParcel(out, flags);
        mReferrerUri.writeToParcel(out, flags);
        mAttributionSource.writeToParcel(out, flags);
        if (mInputEvent != null) {
            out.writeBoolean(true);
            mInputEvent.writeToParcel(out, flags);
        } else {
            out.writeBoolean(false);
        }
    }

    /**
     * Type of the registration.
     */
    public @RegistrationType int getRegistrationType() {
        return mRegistrationType;
    }

    /**
     * Top level origin of the App / Publisher.
     */
    public @NonNull Uri getTopOriginUri() {
        return mTopOriginUri;
    }

    /**
     * Referrer to pass to the registration Uri.
     */
    public @NonNull Uri getReferrerUri() {
        return mReferrerUri;
    }

    /**
     * Source URI of the App / Publisher.
     */
    public @NonNull Uri getRegistrationUri() {
        return mRegistrationUri;
    }

    /**
     * InputEvent related to ad event.
     */
    public @Nullable InputEvent getInputEvent() {
        return mInputEvent;
    }

    /**
     * AttributionSource of the registration.
     */
    public @NonNull AttributionSource getAttributionSource() {
        return mAttributionSource;
    }

    /**
     * A builder for {@link RegistrationRequest}.
     */
    public static final class Builder {
        private @RegistrationType int mRegistrationType;
        private Uri mRegistrationUri;
        private Uri mTopOriginUri;
        private Uri mReferrerUri;
        private InputEvent mInputEvent;
        private AttributionSource mAttributionSource;

        public Builder() {
            mRegistrationType = INVALID;
            mRegistrationUri = Uri.EMPTY;
            mTopOriginUri = Uri.EMPTY;
            mReferrerUri = Uri.EMPTY;
        }

        /**
         * See {@link RegistrationRequest#getRegistrationType}.
         */
        public @NonNull Builder setRegistrationType(
                @RegistrationType int type) {
            if (type != REGISTER_SOURCE
                    && type != REGISTER_TRIGGER) {
                throw new IllegalArgumentException("Invalid registrationType");
            }
            mRegistrationType = type;
            return this;
        }

        /**
         * See {@link RegistrationRequest#getTopOriginUri}.
         */
        public @NonNull Builder setTopOriginUri(@NonNull Uri origin) {
            Objects.requireNonNull(origin);
            mTopOriginUri = origin;
            return this;
        }

        /**
         * See {@link RegistrationRequest#getReferrerUri}.
         */
        public @NonNull Builder setReferrerUri(@NonNull Uri referrer) {
            Objects.requireNonNull(referrer);
            mReferrerUri = referrer;
            return this;
        }

        /**
         * See {@link RegistrationRequest#getRegistrationUri}.
         */
        public @NonNull Builder setRegistrationUri(@NonNull Uri uri) {
            Objects.requireNonNull(uri);
            mRegistrationUri = uri;
            return this;
        }

        /**
         * See {@link RegistrationRequest#getInputEvent}.
         */
        public @NonNull Builder setInputEvent(@Nullable InputEvent event) {
            mInputEvent = event;
            return this;
        }

        /**
         * See {@link RegistrationRequest#getAttributionSource}.
         */
        public @NonNull Builder setAttributionSource(
                @NonNull AttributionSource attributionSource) {
            Objects.requireNonNull(attributionSource);
            mAttributionSource = attributionSource;
            return this;
        }

        /**
         * Build the RegistrationRequest.
         */
        public @NonNull RegistrationRequest build() {
            // Check parameters that start in a non-null state don't
            // somehow get changed to an invalid one (this should
            // not be possible), if it happens throw IllegalStateException.
            if (mRegistrationUri == null
                    || mTopOriginUri == null
                    || mReferrerUri == null) {
                throw new IllegalStateException("Unexpected null value");
            }
            // Ensure registrationType has been set,
            // throws IllegalArgumentException if mRegistrationType
            // isn't a valid choice.
            if (mRegistrationType != REGISTER_SOURCE
                    && mRegistrationType != REGISTER_TRIGGER) {
                throw new IllegalArgumentException("Invalid registrationType");
            }
            // Ensure attributionSource has been set.
            // throws IllegalArgumentException if mAttributionSource
            // is null.
            if (mAttributionSource == null) {
                throw new IllegalArgumentException("attributionSource unset");
            }
            return new RegistrationRequest(
                    mRegistrationType,
                    mRegistrationUri,
                    mTopOriginUri,
                    mReferrerUri,
                    mInputEvent,
                    mAttributionSource);
        }
    }
}
