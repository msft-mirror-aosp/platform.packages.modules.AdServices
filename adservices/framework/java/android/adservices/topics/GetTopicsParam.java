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

package android.adservices.topics;

import static android.adservices.topics.TopicsManager.EMPTY_SDK;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represent input params to the getTopics API.
 *
 * @hide
 */
public final class GetTopicsParam implements Parcelable {
    private final String mSdkName;
    private final String mAppPackageName;

    private GetTopicsParam(@NonNull String sdkName, String appPackageName) {
        mSdkName = sdkName;
        mAppPackageName = appPackageName;
    }

    private GetTopicsParam(@NonNull Parcel in) {
        mSdkName = in.readString();
        mAppPackageName = in.readString();
    }

    public static final @NonNull Creator<GetTopicsParam> CREATOR =
            new Parcelable.Creator<GetTopicsParam>() {
                @Override
                public GetTopicsParam createFromParcel(Parcel in) {
                    return new GetTopicsParam(in);
                }

                @Override
                public GetTopicsParam[] newArray(int size) {
                    return new GetTopicsParam[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeString(mSdkName);
        out.writeString(mAppPackageName);
    }

    /** Get the Sdk Name. */
    @NonNull
    public String getSdkName() {
        return mSdkName;
    }

    /** Get the App PackageName. */
    @NonNull
    public String getAppPackageName() {
        return mAppPackageName;
    }

    /** Builder for {@link GetTopicsParam} objects. */
    public static final class Builder {
        private String mSdkName;
        private String mAppPackageName;

        public Builder() {}

        /**
         * Set the Sdk Name. When the app calls the Topics API directly without using a SDK, don't
         * set this field.
         */
        public @NonNull Builder setSdkName(@NonNull String sdkName) {
            mSdkName = sdkName;
            return this;
        }

        /** Set the App PackageName. */
        public @NonNull Builder setAppPackageName(@NonNull String appPackageName) {
            mAppPackageName = appPackageName;
            return this;
        }

        /** Builds a {@link GetTopicsParam} instance. */
        public @NonNull GetTopicsParam build() {
            if (mSdkName == null) {
                // When Sdk name is not set, we assume the App calls the Topics API directly.
                // We set the Sdk name to empty to mark this.
                mSdkName = EMPTY_SDK;
            }

            if (mAppPackageName == null || mAppPackageName.isEmpty()) {
                throw new IllegalArgumentException("App PackageName must not be empty or null");
            }

            return new GetTopicsParam(mSdkName, mAppPackageName);
        }
    }
}
