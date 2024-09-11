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
 * Represents the module states of AdServices. Can be unknown, enabled, or disabled.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API)
public final class AdServicesModuleState implements Parcelable {

    /** Default module state */
    public static final int MODULE_STATE_UNKNOWN = 0;

    /** Module is available on the device */
    public static final int MODULE_STATE_ENABLED = 1;

    /** Module is not available on the device */
    public static final int MODULE_STATE_DISABLED = 2;

    /**
     * Result codes that are common across various modules.
     *
     * @hide
     */
    @IntDef(
            prefix = {""},
            value = {MODULE_STATE_UNKNOWN, MODULE_STATE_ENABLED, MODULE_STATE_DISABLED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ModuleStateCode {}

    @Module.ModuleCode private int mModule;

    @ModuleStateCode private int mModuleState;

    private AdServicesModuleState(Parcel in) {
        Objects.requireNonNull(in, "Parcel is null");
        mModule = in.readInt();
        mModuleState = in.readInt();
    }

    private AdServicesModuleState(@Module.ModuleCode int module, @ModuleStateCode int moduleState) {
        this.mModule = module;
        this.mModuleState = moduleState;
    }

    /** Gets the state of current module */
    @ModuleStateCode
    public int getModuleState() {
        return mModuleState;
    }

    /** Gets the name of current module */
    @Module.ModuleCode
    public int getModule() {
        return mModule;
    }

    @NonNull
    public static final Creator<AdServicesModuleState> CREATOR =
            new Creator<>() {
                @Override
                public AdServicesModuleState createFromParcel(Parcel in) {
                    return new AdServicesModuleState(in);
                }

                @Override
                public AdServicesModuleState[] newArray(int size) {
                    return new AdServicesModuleState[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest, "Parcel is null");
        dest.writeInt(mModule);
        dest.writeInt(mModuleState);
    }

    public static final class Builder {
        @Module.ModuleCode private int mModule;

        @ModuleStateCode private int mModuleState;

        public Builder() {}

        /** Sets the AdServices module. */
        @NonNull
        public AdServicesModuleState.Builder setModule(@Module.ModuleCode int module) {
            this.mModule = module;
            return this;
        }

        /** Sets the AdServices moduleState. */
        @NonNull
        public AdServicesModuleState.Builder setModuleState(@ModuleStateCode int moduleState) {
            this.mModuleState = moduleState;
            return this;
        }

        /** Builds a {@link AdServicesModuleState} instance. */
        @NonNull
        public AdServicesModuleState build() {
            return new AdServicesModuleState(this.mModule, this.mModuleState);
        }
    }
}
