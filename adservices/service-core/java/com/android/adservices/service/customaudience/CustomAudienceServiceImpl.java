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
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
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
    @NonNull private final Context mContext;
    @NonNull private final CustomAudienceImpl mCustomAudienceImpl;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final Flags mFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    private CustomAudienceServiceImpl(@NonNull Context context) {
        this(
                context,
                CustomAudienceImpl.getInstance(context),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                ConsentManager.getInstance(context),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance(),
                AppImportanceFilter.create(
                        context,
                        AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation()),
                FlagsFactory.getFlags(),
                CallingAppUidSupplierBinderImpl.create());
    }

    /** Creates a new instance of {@link CustomAudienceServiceImpl}. */
    public static CustomAudienceServiceImpl create(@NonNull Context context) {
        return new CustomAudienceServiceImpl(context);
    }

    @VisibleForTesting
    public CustomAudienceServiceImpl(
            @NonNull Context context,
            @NonNull CustomAudienceImpl customAudienceImpl,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull ConsentManager consentManager,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull Flags flags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceImpl);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        mContext = context;
        mCustomAudienceImpl = customAudienceImpl;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mConsentManager = consentManager;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mAppImportanceFilter = appImportanceFilter;
        mFlags = flags;
        mCallingAppUidSupplier = callingAppUidSupplier;
    }

    /**
     * Adds a user to a custom audience.
     *
     * @hide
     */
    @Override
    public void joinCustomAudience(
            @NonNull CustomAudience customAudience, @NonNull ICustomAudienceCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
        try {
            Objects.requireNonNull(customAudience);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        final int callerUid = getCallingUid(apiName);
        mExecutorService.execute(() -> doJoinCustomAudience(customAudience, callback, callerUid));
    }

    /** Try to join the custom audience and signal back to the caller using the callback. */
    private void doJoinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull ICustomAudienceCallback callback,
            final int callerUid) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(callback);

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                mFledgeAuthorizationFilter.assertCallingPackageName(
                        customAudience.getOwnerPackageName(), callerUid, apiName);

                if (mFlags.getEnforceForegroundStatusForFledgeCustomAudience()) {
                    mAppImportanceFilter.assertCallerIsInForeground(
                            customAudience.getOwnerPackageName(), apiName, null);
                }

                shouldLog = true;

                // Fail silently for revoked user consent
                if (!mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mContext.getPackageManager(), customAudience.getOwnerPackageName())) {
                    mCustomAudienceImpl.joinCustomAudience(customAudience);
                    BackgroundFetchJobService.scheduleIfNeeded(mContext, mFlags, false);
                    // TODO(b/233681870): Investigate implementation of actual failures
                    //  in logs/metrics
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }

                callback.onSuccess();
            } catch (NullPointerException | IllegalArgumentException exception) {
                // TODO(b/230783716): We may not want catch NPE or IAE for this case.
                resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
                notifyFailure(callback, resultCode, exception);
            } catch (WrongCallingApplicationStateException exception) {
                resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
                notifyFailure(callback, resultCode, exception);
            } catch (IllegalStateException exception) {
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
                notifyFailure(callback, resultCode, exception);
            } catch (SecurityException exception) {
                resultCode = AdServicesStatusUtils.STATUS_UNAUTHORIZED;
                notifyFailure(callback, resultCode, exception);
            } catch (Exception exception) {
                LogUtil.e(exception, "Exception joining CA: ");
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
                notifyFailure(callback, resultCode, exception);
            }
        } catch (Exception exception) {
            LogUtil.e(exception, "Unable to send result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
            }
        }
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
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
        try {
            Objects.requireNonNull(ownerPackageName);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        final int callerUid = getCallingUid(apiName);
        mExecutorService.execute(
                () -> doLeaveCustomAudience(ownerPackageName, buyer, name, callback, callerUid));
    }

    /** Try to leave the custom audience and signal back to the caller using the callback. */
    private void doLeaveCustomAudience(
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback,
            final int callerUid) {
        Objects.requireNonNull(ownerPackageName);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(callback);

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                mFledgeAuthorizationFilter.assertCallingPackageName(
                        ownerPackageName, callerUid, apiName);

                if (mFlags.getEnforceForegroundStatusForFledgeCustomAudience()) {
                    mAppImportanceFilter.assertCallerIsInForeground(
                            ownerPackageName, apiName, null);
                }

                shouldLog = true;

                // Fail silently for revoked user consent
                if (!mConsentManager.isFledgeConsentRevokedForApp(
                        mContext.getPackageManager(), ownerPackageName)) {
                    // TODO(b/233681870): Investigate implementation of actual failures
                    //  in logs/metrics
                    mCustomAudienceImpl.leaveCustomAudience(ownerPackageName, buyer, name);
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }
            } catch (WrongCallingApplicationStateException exception) {
                resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
                notifyFailure(callback, resultCode, exception);
                return;
            } catch (SecurityException exception) {
                resultCode = AdServicesStatusUtils.STATUS_UNAUTHORIZED;
                notifyFailure(callback, resultCode, exception);
                return;
            } catch (Exception exception) {
                LogUtil.e(
                        exception,
                        "Unexpected error leave custom audience: " + exception.getMessage());
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }

            callback.onSuccess();
            // TODO(b/233681870): Investigate implementation of actual failures in
            //  logs/metrics
        } catch (Exception exception) {
            LogUtil.e(exception, "Unable to send result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
            }
        }
    }

    /**
     * Adds a custom audience override with the given information.
     *
     * <p>If the owner does not match the calling package name, fail silently.
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
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(biddingLogicJS);
            Objects.requireNonNull(trustedBiddingSignals);
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
                        mContext.getPackageManager(),
                        mConsentManager,
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
        final int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
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
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeOverride(owner, buyer, name, callback);
    }

    /**
     * Resets all custom audience overrides for a given caller.
     *
     * @hide
     */
    @Override
    public void resetAllCustomAudienceOverrides(@NonNull CustomAudienceOverrideCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        final int callerUid = getCallingUid(apiName);

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
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeAllOverrides(callback, callerUid);
    }

    private int getCallingUid(int apiNameLoggingId) throws IllegalStateException {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw illegalStateException;
        }
    }
}
