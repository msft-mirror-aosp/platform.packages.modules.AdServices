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


import android.annotation.NonNull;
import android.annotation.Nullable;

/** Get Topics Request. */
public final class GetTopicsRequest {

    /** Name of Ads SDK that is involved in this request. */
    private final String mAdsSdkName;

    private GetTopicsRequest(@Nullable String adsSdkName) {
        mAdsSdkName = adsSdkName;
    }

    /** Get the Sdk Name. */
    @NonNull
    public String getAdsSdkName() {
        return mAdsSdkName;
    }

    /**
     * Builds a {@link GetTopicsRequest} instance.
     *
     * <p>This should be called by either the app itself or by SDK running inside the Sandbox.
     */
    @NonNull
    public static GetTopicsRequest create() {
        return new GetTopicsRequest(/* adsSdkName */ null);
    }

    /**
     * Create a {@link GetTopicsRequest} instance with the provided Ads Sdk Name.
     *
     * <p>This should be called by SDKs running outside of the Sandbox.
     *
     * @param adsSdkName the Ads Sdk Name.
     */
    @NonNull
    public static GetTopicsRequest createWithAdsSdkName(@NonNull String adsSdkName) {
        // This is the case the SDK calling without the Sandbox.
        // Check if the caller set the adsSdkName
        if (adsSdkName == null) {
            throw new IllegalArgumentException(
                    "When calling Topics API outside of the Sandbox, caller should set Ads Sdk"
                            + " Name");
        }

        return new GetTopicsRequest(adsSdkName);
    }
}