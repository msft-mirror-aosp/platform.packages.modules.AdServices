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
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;

/** Get Topics Request. */
public final class GetTopicsRequest {
    /** Context of the application/sandbox which send out the request to getTopics API. */
    private final Context mContext;

    /** Name of Ads SDK that is involved in this request. */
    private final String mSdkName;

    private GetTopicsRequest(@NonNull Context context, @NonNull String sdkName) {
        mContext = context;
        mSdkName = sdkName;
    }

    /** Get the Context. */
    @NonNull
    public Context getContext() {
        return mContext;
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
     */
    public static final class Builder {
        private final Context mContext;
        private String mSdkName;

        public Builder(@NonNull Context context) {
            mContext = context;
        }

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
            if (null == mContext) {
                throw new IllegalArgumentException("Must set the context for GetTopicsRequest");
            }

            // First check if context is SandboxedSdkContext or not
            if (mContext instanceof SandboxedSdkContext) {
                String sdkNameFromSandboxedContext = ((SandboxedSdkContext) mContext).getSdkName();
                if (null == sdkNameFromSandboxedContext || sdkNameFromSandboxedContext.isEmpty()) {
                    throw new IllegalArgumentException(
                            "sdkNameFromSandboxedContext should not be null or empty");
                }
                if (mSdkName != null) {
                    throw new IllegalArgumentException(
                            "When calling PPAPI from Sandbox, caller should not set mSdkName");
                }
                mSdkName = sdkNameFromSandboxedContext;
            } else { // This is the case without the Sandbox.
                // Check if the caller set the mSdkName
                if (mSdkName == null) {
                    // When Sdk name is not set, we assume the App calls the Topics API directly.
                    // We set the Sdk name to empty to mark this.
                    mSdkName = EMPTY_SDK;
                }
            }

            return new GetTopicsRequest(mContext, mSdkName);
        }
    }
}