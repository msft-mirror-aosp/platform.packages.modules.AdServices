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
 * Represents the user's opted-in/opted-out choice for individual module in AdServices.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API)
public final class AdServicesModuleUserChoice implements Parcelable {

    /** Default user choice state */
    public static final int USER_CHOICE_UNKNOWN = 0;

    /** User opted in state */
    public static final int USER_CHOICE_OPTED_IN = 1;

    /** User opted out state */
    public static final int USER_CHOICE_OPTED_OUT = 2;

    /**
     * Result codes that are common across various modules.
     *
     * @hide
     */
    @IntDef(
            prefix = {""},
            value = {USER_CHOICE_UNKNOWN, USER_CHOICE_OPTED_IN, USER_CHOICE_OPTED_OUT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ModuleUserChoiceCode {}

    @Module.ModuleCode private int mModule;

    @ModuleUserChoiceCode private int mUserChoice;

    private AdServicesModuleUserChoice(@NonNull Parcel in) {

        mModule = in.readInt();
        mUserChoice = in.readInt();
    }

    private AdServicesModuleUserChoice(int module, int userChoice) {
        this.mModule = module;
        this.mUserChoice = userChoice;
    }

    @NonNull
    public static final Creator<AdServicesModuleUserChoice> CREATOR =
            new Creator<>() {
                @Override
                public AdServicesModuleUserChoice createFromParcel(Parcel in) {
                    Objects.requireNonNull(in);
                    return new AdServicesModuleUserChoice(in);
                }

                @Override
                public AdServicesModuleUserChoice[] newArray(int size) {
                    return new AdServicesModuleUserChoice[size];
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
        dest.writeInt(mModule);
        dest.writeInt(mUserChoice);
    }

    /** Gets the name of current module */
    public int getModule() {
        return mModule;
    }

    /** Gets the user opted in/out choice of current module */
    public int getUserChoice() {
        return mUserChoice;
    }

    public static final class Builder {
        @Module.ModuleCode private int mModule;

        @ModuleUserChoiceCode private int mUserChoice;

        public Builder() {}

        /** Sets the AdServices module. */
        @NonNull
        public AdServicesModuleUserChoice.Builder setModule(@Module.ModuleCode int module) {
            this.mModule = module;
            return this;
        }

        /** Sets the AdServices moduleState. */
        @NonNull
        public AdServicesModuleUserChoice.Builder setUserChoice(
                @ModuleUserChoiceCode int userChoice) {
            this.mUserChoice = userChoice;
            return this;
        }

        /** Builds a {@link AdServicesModuleUserChoice} instance. */
        @NonNull
        public AdServicesModuleUserChoice build() {
            return new AdServicesModuleUserChoice(this.mModule, this.mUserChoice);
        }
    }
}
