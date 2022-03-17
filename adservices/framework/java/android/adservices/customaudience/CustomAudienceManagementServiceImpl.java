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

package android.adservices.customaudience;

import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;

/**
 * Implementation of the Custom Audience Management service.
 *
 * @hide
 */
public class CustomAudienceManagementServiceImpl extends ICustomAudienceManagementService.Stub {
    // TODO(b/221861041): Remove warning suppression; context needed later for
    //  authorization/authentication
    @SuppressWarnings("unused")
    private final Context mContext;

    public CustomAudienceManagementServiceImpl(Context context) {
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
