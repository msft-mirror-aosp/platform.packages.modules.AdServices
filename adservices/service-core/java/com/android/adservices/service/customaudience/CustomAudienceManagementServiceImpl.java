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

package com.android.adservices.service.customaudience;

import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceManagementResponse;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ICustomAudienceManagementService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;

import java.util.Objects;

/**
 * Implementation of the Custom Audience Management service.
 *
 * @hide
 */
public class CustomAudienceManagementServiceImpl extends ICustomAudienceManagementService.Stub {
    // TODO(b/221861041): Remove warning suppression; context needed later for
    //  authorization/authentication
    @NonNull
    @SuppressWarnings("unused")
    private final Context mContext;

    public CustomAudienceManagementServiceImpl(@NonNull Context context) {
        Objects.requireNonNull(context);
        mContext = context;
    }

    /**
     * Adds a user to a custom audience.
     *
     * @hide
     */
    @Override
    public void joinCustomAudience(@NonNull CustomAudience customAudience,
            @NonNull ICustomAudienceCallback callback) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(callback);

        // TODO(b/225988784): Offload work to thread pool
        try {
            callback.onResult(
                    new CustomAudienceManagementResponse.Builder()
                            .setStatusCode(CustomAudienceManagementResponse.STATUS_INTERNAL_ERROR)
                            .setErrorMessage("Not Implemented!")
                            .build());
        } catch (RemoteException e) {
            LogUtil.e("Unable to send result to the callback", e);
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Attempts to remove a user from a custom audience.
     *
     * @hide
     */
    @Override
    public void leaveCustomAudience(@NonNull String owner, @NonNull String buyer,
            @NonNull String name, @NonNull ICustomAudienceCallback callback) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(callback);

        // TODO(b/225988784): Offload work to thread pool
        try {
            callback.onResult(
                    new CustomAudienceManagementResponse.Builder()
                            .setStatusCode(CustomAudienceManagementResponse.STATUS_INTERNAL_ERROR)
                            .setErrorMessage("Not Implemented!")
                            .build());
        } catch (RemoteException e) {
            LogUtil.e("Unable to send result to the callback", e);
            throw new RuntimeException(e.getMessage());
        }
    }
}
