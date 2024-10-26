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

import static android.adservices.common.AdServicesModuleState.MODULE_STATE_UNKNOWN;
import static android.adservices.common.AdServicesModuleState.ModuleStateCode;
import static android.adservices.common.Module.ModuleCode;
import static android.adservices.common.NotificationType.NotificationTypeCode;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.flags.Flags;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The request sent from from system applications to control the module states with Adservices.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API)
public final class UpdateAdServicesModuleStatesParams implements Parcelable {
    private final List<AdServicesModuleState> mAdServicesModuleStateList;
    @NotificationTypeCode private final int mNotificationType;

    private UpdateAdServicesModuleStatesParams(
            List<AdServicesModuleState> adServicesModuleStateList,
            @NotificationTypeCode int notificationType) {
        mAdServicesModuleStateList = Objects.requireNonNull(adServicesModuleStateList);
        mNotificationType = notificationType;
    }

    private UpdateAdServicesModuleStatesParams(Parcel in) {
        mAdServicesModuleStateList = new ArrayList<>();
        in.readTypedList(mAdServicesModuleStateList, AdServicesModuleState.CREATOR);
        mNotificationType = in.readInt();
    }

    @NonNull
    public static final Creator<UpdateAdServicesModuleStatesParams> CREATOR =
            new Creator<>() {
                @Override
                public UpdateAdServicesModuleStatesParams createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new UpdateAdServicesModuleStatesParams(in);
                }

                @Override
                public UpdateAdServicesModuleStatesParams[] newArray(int size) {
                    return new UpdateAdServicesModuleStatesParams[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);

        out.writeTypedList(mAdServicesModuleStateList);
        out.writeInt(mNotificationType);
    }

    /** Gets the AdServices module state for one module. */
    @ModuleStateCode
    public int getModuleState(@ModuleCode int module) {
        Optional<AdServicesModuleState> moduleState =
                mAdServicesModuleStateList.stream()
                        .filter(element -> element.getModule() == module)
                        .findFirst();
        return moduleState.map(AdServicesModuleState::getModuleState).orElse(MODULE_STATE_UNKNOWN);
    }

    /**
     * Returns the Adservices module state map associated with this result. Keys represent modules
     * and values represent module states.
     *
     * @hide
     */
    @NonNull
    @VisibleForTesting(visibility = PACKAGE)
    public Map<Integer, Integer> getModuleStateMap() {
        Map<Integer, Integer> moduleStatemap = new HashMap<>(mAdServicesModuleStateList.size());
        mAdServicesModuleStateList.forEach(
                moduleState ->
                        moduleStatemap.put(moduleState.getModule(), moduleState.getModuleState()));
        return moduleStatemap;
    }

    /** Returns the Notification type associated with this result. */
    @NotificationTypeCode
    @VisibleForTesting(visibility = PACKAGE)
    public int getNotificationType() {
        return mNotificationType;
    }

    @Override
    public String toString() {
        return "UpdateAdIdRequest{"
                + "mAdServicesModuleStateList="
                + mAdServicesModuleStateList
                + ", mNotificationType="
                + mNotificationType
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof UpdateAdServicesModuleStatesParams that)) {
            return false;
        }

        return Objects.equals(mAdServicesModuleStateList, that.mAdServicesModuleStateList)
                && (mNotificationType == that.mNotificationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdServicesModuleStateList, mNotificationType);
    }

    /** Builder for {@link UpdateAdServicesModuleStatesParams} objects. */
    public static final class Builder {
        private final List<AdServicesModuleState> mAdServicesModuleStateList = new ArrayList<>();
        @NotificationTypeCode private int mNotificationType;

        public Builder() {}

        /** Sets the AdServices module state for one module. */
        @NonNull
        public Builder setModuleState(@ModuleCode int module, @ModuleStateCode int moduleState) {
            this.mAdServicesModuleStateList.add(new AdServicesModuleState(module, moduleState));
            return this;
        }

        /** Sets the notification type. */
        @NonNull
        public Builder setNotificationType(@NotificationTypeCode int notificationType) {
            this.mNotificationType = notificationType;
            return this;
        }

        /** Builds a {@link UpdateAdServicesModuleStatesParams} instance. */
        @NonNull
        public UpdateAdServicesModuleStatesParams build() {
            return new UpdateAdServicesModuleStatesParams(
                    mAdServicesModuleStateList, this.mNotificationType);
        }
    }
}
