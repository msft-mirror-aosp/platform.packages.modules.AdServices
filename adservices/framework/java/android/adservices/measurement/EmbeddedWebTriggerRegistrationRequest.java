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
 * Class to hold input to measurement trigger registration calls from embedded web context.
 *
 * @hide
 */
public final class EmbeddedWebTriggerRegistrationRequest implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Parcelable.Creator<EmbeddedWebTriggerRegistrationRequest> CREATOR =
            new Parcelable.Creator<EmbeddedWebTriggerRegistrationRequest>() {
                @Override
                public EmbeddedWebTriggerRegistrationRequest createFromParcel(Parcel in) {
                    return new EmbeddedWebTriggerRegistrationRequest(in);
                }

                @Override
                public EmbeddedWebTriggerRegistrationRequest[] newArray(int size) {
                    return new EmbeddedWebTriggerRegistrationRequest[size];
                }
            };
    /** Registration info to fetch sources. */
    @NonNull private final List<TriggerParams> mTriggerParams;

    /** User interaction input event. */
    @Nullable private final InputEvent mInputEvent;

    /** Top level origin of publisher app. */
    @Nullable private final Uri mTopOriginUri;

    /** Constructor for {@link EmbeddedWebTriggerRegistrationRequest}. */
    private EmbeddedWebTriggerRegistrationRequest(
            @NonNull List<TriggerParams> triggerParams,
            @Nullable InputEvent inputEvent,
            @Nullable Uri topOriginUri) {
        mTriggerParams = triggerParams;
        mInputEvent = inputEvent;
        mTopOriginUri = topOriginUri;
    }

    /** Unpack parcel of OSAttributionTriggerRegistrationRequest. */
    private EmbeddedWebTriggerRegistrationRequest(Parcel in) {
        Objects.requireNonNull(in);
        ArrayList<TriggerParams> triggerParams = new ArrayList<>();
        in.readList(triggerParams, TriggerParams.class.getClassLoader(), TriggerParams.class);
        mTriggerParams = triggerParams;

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
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddedWebTriggerRegistrationRequest)) return false;
        EmbeddedWebTriggerRegistrationRequest that = (EmbeddedWebTriggerRegistrationRequest) o;
        return Objects.equals(mTriggerParams, that.mTriggerParams)
                && Objects.equals(mInputEvent, that.mInputEvent)
                && Objects.equals(mTopOriginUri, that.mTopOriginUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTriggerParams, mInputEvent, mTopOriginUri);
    }

    /** Getter for {@link #mTriggerParams}. */
    @NonNull
    public List<TriggerParams> getTriggerParams() {
        return mTriggerParams;
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        out.writeList(mTriggerParams);
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
    }

    /** Builder for {@link EmbeddedWebTriggerRegistrationRequest}. */
    public static final class Builder {
        /**
         * Registration info to fetch triggers. Maximum 20 registrations allowed at once, to be in
         * sync with Chrome platform.
         */
        @NonNull private List<TriggerParams> mTriggerParams;
        /** User interaction input event. */
        @Nullable private InputEvent mInputEvent;
        /** Top level origin of publisher app. */
        @NonNull private Uri mTopOriginUri;

        /**
         * Setter for {@link #mTriggerParams}.
         *
         * @param triggerParams source registrations
         * @return builder
         */
        @NonNull
        public Builder setTriggerParams(@NonNull List<TriggerParams> triggerParams) {
            mTriggerParams = triggerParams;
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

        /** Pre-validates paramerters and builds {@link EmbeddedWebTriggerRegistrationRequest}. */
        @NonNull
        public EmbeddedWebTriggerRegistrationRequest build() {
            if (mTriggerParams == null || mTriggerParams.isEmpty()) {
                throw new IllegalArgumentException("registration URI unset");
            }

            return new EmbeddedWebTriggerRegistrationRequest(
                    mTriggerParams, mInputEvent, mTopOriginUri);
        }
    }
}
