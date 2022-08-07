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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ICustomAudienceService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.SdkRuntimeUtil;
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

    @NonNull private final CustomAudienceImpl mCustomAudienceImpl;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final Flags mFlags;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    public CustomAudienceServiceImpl(@NonNull Context context) {
        this(
                context,
                CustomAudienceImpl.getInstance(context),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance(),
                AppImportanceFilter.create(
                        context,
                        AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation()),
                FlagsFactory.getFlags());
    }

    @VisibleForTesting
    public CustomAudienceServiceImpl(
            @NonNull Context context,
            @NonNull CustomAudienceImpl customAudienceImpl,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull Flags flags) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceImpl);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        mCustomAudienceImpl = customAudienceImpl;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mAppImportanceFilter = appImportanceFilter;
        mFlags = flags;
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

        assertCallingPackageName(
                customAudience.getOwnerPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        mExecutorService.execute(
                () -> {
                    int resultCode = AdServicesStatusUtils.STATUS_UNSET;
                    try {
                        try {
                            if (mFlags.getEnforceForegroundStatusForFledgeCustomAudience()) {
                                mAppImportanceFilter.assertCallerIsInForeground(
                                        customAudience.getOwnerPackageName(),
                                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                        null);
                            }

                            mCustomAudienceImpl.joinCustomAudience(customAudience);
                            callback.onSuccess();
                            // TODO(b/233681870): Investigate implementation of actual failures in
                            //  logs/metrics
                            resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                        } catch (NullPointerException | IllegalArgumentException exception) {
                            // TODO(b/230783716): We may not want catch NPE or IAE for this case.
                            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
                            notifyFailure(callback, resultCode, exception);
                        } catch (WrongCallingApplicationStateException backgorundCaller) {
                            resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
                            notifyFailure(callback, resultCode, backgorundCaller);
                        } catch (IllegalStateException illegalStateException) {
                            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
                            notifyFailure(callback, resultCode, illegalStateException);
                        } catch (Exception exception) {
                            LogUtil.e(exception, "Exception joining CA: ");
                            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
                            notifyFailure(callback, resultCode, exception);
                        }
                    } catch (Exception exception) {
                        LogUtil.e(exception, "Unable to send result to the callback");
                        resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
                    } finally {
                        mAdServicesLogger.logFledgeApiCallStats(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, resultCode);
                    }
                });
    }

    private void notifyFailure(
            ICustomAudienceCallback callback, int resultCode, Exception exception)
            throws RemoteException {
        callback.onFailure(
                new FledgeErrorResponse.Builder()
                        .setStatusCode(resultCode)
                        .setErrorMessage(exception.getMessage())
                        .build());
    }

    /**
     * Attempts to remove a user from a custom audience.
     *
     * @hide
     */
    @Override
    public void leaveCustomAudience(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback) {
        try {
            Objects.requireNonNull(owner);
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

        assertCallingPackageName(owner, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        mExecutorService.execute(
                () -> {
                    int resultCode = AdServicesStatusUtils.STATUS_UNSET;
                    try {
                        try {
                            if (mFlags.getEnforceForegroundStatusForFledgeCustomAudience()) {
                                mAppImportanceFilter.assertCallerIsInForeground(
                                        owner,
                                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                        null);
                            }
                            mCustomAudienceImpl.leaveCustomAudience(owner, buyer, name);
                            resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                        } catch (WrongCallingApplicationStateException exception) {
                            resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
                            callback.onFailure(
                                    new FledgeErrorResponse.Builder()
                                            .setErrorMessage(exception.getMessage())
                                            .setStatusCode(resultCode)
                                            .build());
                        } catch (Exception exception) {
                            LogUtil.e(
                                    exception,
                                    "Unexpected error leave custom audience: "
                                            + exception.getMessage());
                            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
                        }

                        if (resultCode != AdServicesStatusUtils.STATUS_BACKGROUND_CALLER) {
                            callback.onSuccess();
                        }
                        // TODO(b/233681870): Investigate implementation of actual failures in
                        //  logs/metrics
                    } catch (Exception exception) {
                        LogUtil.e(exception, "Unable to send result to the callback");
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
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull CustomAudienceOverrideCallback callback) {
        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(biddingLogicJS);
            Objects.requireNonNull(trustedBiddingSignals);
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
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.addOverride(owner, buyer, name, biddingLogicJS, trustedBiddingSignals, callback);
    }

    /**
     * Removes a custom audience override with the given information.
     *
     * @hide
     */
    @Override
    public void removeCustomAudienceRemoteInfoOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
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
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeOverride(owner, buyer, name, callback);
    }

    /**
     * Resets all custom audience overrides with the given information.
     *
     * @hide
     */
    @Override
    public void resetAllCustomAudienceOverrides(@NonNull CustomAudienceOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeAllOverrides(callback, getCallingUid(apiName));
    }

    private int getCallingUid(int apiNameLoggingId) {
        try {
            return SdkRuntimeUtil.getCallingAppUid(Binder.getCallingUidOrThrow());
        } catch (IllegalStateException illegalStateException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw illegalStateException;
        }
    }

    private void assertCallingPackageName(String owner, int apiNameLoggingId) {
        mFledgeAuthorizationFilter.assertCallingPackageName(
                owner, getCallingUid(apiNameLoggingId), apiNameLoggingId);
    }
}
