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
@FlaggedApi(Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API)
public final class AdServicesModuleState implements Parcelable {

    /** Default module state */
    public static final int MODULE_STATE_UNKNOWN = AdServicesCommonManager.MODULE_STATE_UNKNOWN;

    /** Module is available on the device */
    public static final int MODULE_STATE_ENABLED = AdServicesCommonManager.MODULE_STATE_ENABLED;

    /** Module is not available on the device */
    public static final int MODULE_STATE_DISABLED = AdServicesCommonManager.MODULE_STATE_DISABLED;

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

    /**
     * Constructor for a module state.
     *
     * @param module desired module
     * @param moduleState desired module state
     */
    public AdServicesModuleState(@Module.ModuleCode int module, @ModuleStateCode int moduleState) {
        this.mModule = Module.validate(module);
        this.mModuleState = validate(moduleState);
    }

    /**
     * Validates a module state. For this function doesn't alter the input and just returns it back
     * or fails with {@link IllegalArgumentException}.
     *
     * @param moduleState module state to validate
     * @return module state
     */
    @ModuleStateCode
    private static int validate(@ModuleStateCode int moduleState) {
        return switch (moduleState) {
            case MODULE_STATE_UNKNOWN, MODULE_STATE_ENABLED, MODULE_STATE_DISABLED -> moduleState;
            default -> throw new IllegalArgumentException("Invalid Module State:" + moduleState);
        };
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
}
