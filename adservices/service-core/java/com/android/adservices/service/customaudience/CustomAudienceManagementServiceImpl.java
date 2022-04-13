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

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ICustomAudienceManagementService;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesExecutors;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation of the Custom Audience Management service.
 */
public class CustomAudienceManagementServiceImpl extends ICustomAudienceManagementService.Stub {
    // TODO(b/221861041): Remove warning suppression; context needed later for
    //  authorization/authentication
    @NonNull
    @SuppressWarnings("unused")
    private final Context mContext;

    @NonNull
    private final CustomAudienceManagementImpl mCustomAudienceManagement;

    @NonNull
    private final Executor mExecutor;

    public CustomAudienceManagementServiceImpl(@NonNull Context context) {
        this(context, CustomAudienceManagementImpl.getInstance(context),
                AdServicesExecutors.getBackgroundExecutor());
    }

    @VisibleForTesting
    CustomAudienceManagementServiceImpl(@NonNull Context context,
            @NonNull CustomAudienceManagementImpl customAudienceManagement,
            @NonNull Executor executor) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceManagement);
        mContext = context;
        mCustomAudienceManagement = customAudienceManagement;
        mExecutor = executor;
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

        mExecutor.execute(() -> {
            try {
                try {
                    mCustomAudienceManagement.joinCustomAudience(customAudience);
                    callback.onSuccess();
                } catch (Exception exception) {
                    callback.onFailure(new FledgeErrorResponse.Builder()
                            .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                            .setErrorMessage(exception.getMessage())
                            .build()
                    );
                }
            } catch (Exception exception) {
                LogUtil.e("Unable to send result to the callback", exception);
            }
        });
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

        mExecutor.execute(() -> {
            try {
                try {
                    mCustomAudienceManagement.leaveCustomAudience(owner, buyer, name);
                    callback.onSuccess();
                } catch (Exception exception) {
                    callback.onFailure(new FledgeErrorResponse.Builder()
                            .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                            .setErrorMessage(exception.getMessage())
                            .build()
                    );
                }
            } catch (Exception exception) {
                LogUtil.e("Unable to send result to the callback", exception);
            }
        });
    }
}
