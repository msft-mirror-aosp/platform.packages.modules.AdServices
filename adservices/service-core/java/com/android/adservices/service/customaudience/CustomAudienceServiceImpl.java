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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ICustomAudienceService;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.AdServicesExecutors;
import com.android.adservices.service.devapi.CustomAudienceOverrider;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Implementation of the Custom Audience service. */
public class CustomAudienceServiceImpl extends ICustomAudienceService.Stub {
    // TODO(b/221861041): Remove warning suppression; context needed later for
    //  authorization/authentication
    @NonNull
    @SuppressWarnings("unused")
    private final Context mContext;

    @NonNull private final CustomAudienceImpl mCustomAudienceImpl;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    public CustomAudienceServiceImpl(@NonNull Context context) {
        this(
                context,
                CustomAudienceImpl.getInstance(context),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    CustomAudienceServiceImpl(
            @NonNull Context context,
            @NonNull CustomAudienceImpl customAudienceImpl,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceImpl);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        mContext = context;
        mCustomAudienceImpl = customAudienceImpl;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Adds a user to a custom audience.
     *
     * @hide
     */
    @Override
    public void joinCustomAudience(
            @NonNull CustomAudience customAudience, @NonNull ICustomAudienceCallback callback) {
        try {
            Objects.requireNonNull(customAudience);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        mExecutorService.execute(
                () -> {
                    int resultCode = AdServicesStatusUtils.STATUS_UNSET;
                    try {
                        try {
                            mCustomAudienceImpl.joinCustomAudience(customAudience);
                            callback.onSuccess();
                            // TODO(b/233681870): Investigate implementation of actual failures in
                            //  logs/metrics
                            resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                        } catch (NullPointerException | IllegalArgumentException exception) {
                            // TODO(b/230783716): We may not want catch NPE or IAE for this case.
                            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
                            callback.onFailure(
                                    new FledgeErrorResponse.Builder()
                                            .setStatusCode(resultCode)
                                            .setErrorMessage(exception.getMessage())
                                            .build());
                        } catch (Exception exception) {
                            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
                            callback.onFailure(
                                    new FledgeErrorResponse.Builder()
                                            .setStatusCode(resultCode)
                                            .setErrorMessage(exception.getMessage())
                                            .build());
                        }
                    } catch (Exception exception) {
                        LogUtil.e("Unable to send result to the callback", exception);
                        resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
                    } finally {
                        mAdServicesLogger.logFledgeApiCallStats(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, resultCode);
                    }
                });
    }

    /**
     * Attempts to remove a user from a custom audience.
     *
     * @hide
     */
    @Override
    public void leaveCustomAudience(
            @Nullable String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback) {
        try {
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        mExecutorService.execute(
                () -> {
                    int resultCode = AdServicesStatusUtils.STATUS_UNSET;
                    try {
                        mCustomAudienceImpl.leaveCustomAudience(owner, buyer, name);
                    } catch (Exception exception) {
                        LogUtil.e("Unexpected error leave custom audience.", exception);
                    }
                    try {
                        callback.onSuccess();
                        // TODO(b/233681870): Investigate implementation of actual failures in
                        //  logs/metrics
                        resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                    } catch (Exception exception) {
                        LogUtil.e("Unable to send result to the callback", exception);
                        resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
                    } finally {
                        mAdServicesLogger.logFledgeApiCallStats(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                resultCode);
                    }
                });
    }

    /**
     * Adds a custom audience override with the given information.
     *
     * @hide
     */
    @Override
    public void overrideCustomAudienceRemoteInfo(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            @NonNull String trustedBiddingData,
            @NonNull CustomAudienceOverrideCallback callback) {
        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(biddingLogicJS);
            Objects.requireNonNull(trustedBiddingData);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new IllegalStateException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext, customAudienceDao, mExecutorService, mAdServicesLogger);

        overrider.addOverride(owner, buyer, name, biddingLogicJS, trustedBiddingData, callback);
    }

    /**
     * Removes a custom audience override with the given information.
     *
     * @hide
     */
    @Override
    public void removeCustomAudienceRemoteInfoOverride(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull CustomAudienceOverrideCallback callback) {
        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new IllegalStateException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext, customAudienceDao, mExecutorService, mAdServicesLogger);

        overrider.removeOverride(owner, buyer, name, callback);
    }

    /**
     * Resets all custom audience overrides with the given information.
     *
     * @hide
     */
    @Override
    public void resetAllCustomAudienceOverrides(@NonNull CustomAudienceOverrideCallback callback) {
        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new IllegalStateException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext, customAudienceDao, mExecutorService, mAdServicesLogger);

        overrider.removeAllOverrides(callback);
    }
}
