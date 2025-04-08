/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.adservices.common.AdServicesCommonManager.AdsPersonalizationStatus;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.flags.Flags;

import java.util.Objects;

/**
 * The request sent from from system applications to control the no ads personalization type in
 * Adservices.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_UI_ENABLE_SET_ADS_PERSONALIZATION_STATUS)
public final class AdsPersonalizationStatusParams implements Parcelable {
    @AdsPersonalizationStatus private final int mAdsPersonalizationStatus;

    public AdsPersonalizationStatusParams(@AdsPersonalizationStatus int adsPersonalizationStatus) {
        mAdsPersonalizationStatus = adsPersonalizationStatus;
    }

    private AdsPersonalizationStatusParams(Parcel in) {
        mAdsPersonalizationStatus = in.readInt();
    }

    @NonNull
    public static final Creator<AdsPersonalizationStatusParams> CREATOR =
            new Creator<>() {
                @Override
                public AdsPersonalizationStatusParams createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new AdsPersonalizationStatusParams(in);
                }

                @Override
                public AdsPersonalizationStatusParams[] newArray(int size) {
                    return new AdsPersonalizationStatusParams[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    public @AdsPersonalizationStatus int getAdsPersonalizationStatus() {
        return mAdsPersonalizationStatus;
    }

    /** @hide */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        out.writeInt(mAdsPersonalizationStatus);
    }

    @Override
    public String toString() {
        return "AdsPersonalizationParams{"
                + "mAdsPersonalizationType="
                + mAdsPersonalizationStatus
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AdsPersonalizationStatusParams that)) {
            return false;
        }

        return mAdsPersonalizationStatus == that.mAdsPersonalizationStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdsPersonalizationStatus);
    }
}
