/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.common;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represents the notification type of AdServices.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API)
public final class NotificationTypeParams implements Parcelable {
    /** Don't show any notification during the enrollment. */
    public static final int NOTIFICATION_NONE = 0;

    /** Shows ongoing notification during the enrollment, which user can not dismiss. */
    public static final int NOTIFICATION_ONGOING = 1;

    /** Shows regular notification during the enrollment, which user can dismiss. */
    public static final int NOTIFICATION_REGULAR = 2;

    /**
     * Result codes that are common across various APIs.
     *
     * @hide
     */
    @IntDef(
            prefix = {""},
            value = {NOTIFICATION_NONE, NOTIFICATION_ONGOING, NOTIFICATION_REGULAR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NotificationTypeCode {}

    @NotificationTypeCode private int mNotificationType;

    private NotificationTypeParams(@NotificationTypeCode int notificationType) {
        this.mNotificationType = notificationType;
    }

    private NotificationTypeParams(@NonNull Parcel in) {
        mNotificationType = in.readInt();
    }

    @NonNull
    public static final Creator<NotificationTypeParams> CREATOR =
            new Creator<>() {
                @Override
                public NotificationTypeParams createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new NotificationTypeParams(in);
                }

                @Override
                public NotificationTypeParams[] newArray(int size) {
                    return new NotificationTypeParams[size];
                }
            };

    /**
     * Describe the kinds of special objects contained in this Parcelable instance's marshaled
     * representation. For example, if the object will include a file descriptor in the output of
     * {@link #writeToParcel(Parcel, int)}, the return value of this method must include the {@link
     * #CONTENTS_FILE_DESCRIPTOR} bit.
     *
     * @return a bitmask indicating the set of special object types marshaled by this Parcelable
     *     object instance.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this object in to a Parcel.
     *
     * @param dest The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written. May be 0 or {@link
     *     #PARCELABLE_WRITE_RETURN_VALUE}.
     */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        dest.writeInt(mNotificationType);
    }

    public int getNotificationType() {
        return mNotificationType;
    }

    public static final class Builder {
        @NotificationTypeCode private int mNotificationType;

        public Builder() {}

        /** Sets the AdServices notificationType. */
        @NonNull
        public NotificationTypeParams.Builder setNotificationType(
                @NotificationTypeCode int notificationType) {
            this.mNotificationType = notificationType;
            return this;
        }

        /** Builds a {@link NotificationTypeParams} instance. */
        @NonNull
        public NotificationTypeParams build() {
            return new NotificationTypeParams(this.mNotificationType);
        }
    }
}
