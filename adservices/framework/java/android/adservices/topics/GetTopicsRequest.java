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

/**
 * Get Topics Request.
 *
 * @hide
 */
// TODO(223684663): unhide this
public class GetTopicsRequest {

    private final String mSdkName;

    private GetTopicsRequest(@NonNull String sdkName) {
        mSdkName = sdkName;
    }
    /**
     * Get the Sdk Name.
     */
    @NonNull
    public String getSdkName() {
        return mSdkName;
    }

    /**
     * Builder for {@link GetTopicsRequest} objects.
     *
     * @hide
     */
    public static final class Builder {
        private String mSdkName;

        public Builder() {}

        /**
         * Set the Sdk Name. When the app calls the Topics API directly without using a SDK, don't
         * set this field.
         */
        public @NonNull Builder setSdkName(@NonNull String sdkName) {
            mSdkName = sdkName;
            return this;
        }

        /** Builds a {@link GetTopicsRequest} instance. */
        public @NonNull GetTopicsRequest build() {
            if (mSdkName == null) {
                // When Sdk name is not set, we assume the App calls the Topics API directly.
                // We set the Sdk name to empty to mark this.
                mSdkName = EMPTY_SDK;
            }

            return new GetTopicsRequest(mSdkName);
        }
    }
}
