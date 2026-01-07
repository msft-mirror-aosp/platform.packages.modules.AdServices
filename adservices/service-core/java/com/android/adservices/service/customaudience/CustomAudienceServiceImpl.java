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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ICustomAudienceService;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateCallback;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateInput;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Implementation of the Custom Audience service. */
@RequiresApi(Build.VERSION_CODES.S)
public class CustomAudienceServiceImpl extends ICustomAudienceService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final CustomAudienceImpl mCustomAudienceImpl;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final Flags mFlags;
    @NonNull private final DebugFlags mDebugFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;

    @NonNull private final CustomAudienceServiceFilter mCustomAudienceServiceFilter;
    @NonNull private final AdFilteringFeatureFactory mAdFilteringFeatureFactory;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    private CustomAudienceServiceImpl(@NonNull Context context) {
        this(
                context,
                CustomAudienceImpl.getInstance(),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                ConsentManager.getInstance(),
                DevContextFilter.create(
                        context,
                        BinderFlagReader.readFlag(
                                () ->
                                        DebugFlags.getInstance()
                                                .getDeveloperSessionFeatureEnabled())),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance(),
                AppImportanceFilter.create(
                        context,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation(),
                        BinderFlagReader.readFlag(
                                () -> FlagsFactory.getFlags().getEnableGetBindingUidImportance())),
                FlagsFactory.getFlags(),
                DebugFlags.getInstance(),
                CallingAppUidSupplierBinderImpl.create(),
                new CustomAudienceServiceFilter(
                        context,
                        new FledgeConsentFilter(
                                ConsentManager.getInstance(), AdServicesLoggerImpl.getInstance()),
                        FlagsFactory.getFlags(),
                        AppImportanceFilter.create(
                                context,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation(),
                                BinderFlagReader.readFlag(
                                        () ->
                                                FlagsFactory.getFlags()
                                                        .getEnableGetBindingUidImportance())),
                        FledgeAuthorizationFilter.create(
                                context, AdServicesLoggerImpl.getInstance()),
                        new FledgeAllowListsFilter(
                                FlagsFactory.getFlags(), AdServicesLoggerImpl.getInstance()),
                        new FledgeApiThrottleFilter(
                                Throttler.getInstance(), AdServicesLoggerImpl.getInstance())),
                new AdFilteringFeatureFactory(
                        SharedStorageDatabase.getInstance().appInstallDao(),
                        SharedStorageDatabase.getInstance().frequencyCapDao(),
                        FlagsFactory.getFlags()));
    }

    /** Creates a new instance of {@link CustomAudienceServiceImpl}. */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
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
            @NonNull DebugFlags debugFlags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull CustomAudienceServiceFilter customAudienceServiceFilter,
            @NonNull AdFilteringFeatureFactory adFilteringFeatureFactory) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceImpl);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(customAudienceServiceFilter);

        mContext = context;
        mCustomAudienceImpl = customAudienceImpl;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mConsentManager = consentManager;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mAppImportanceFilter = appImportanceFilter;
        mFlags = flags;
        mDebugFlags = debugFlags;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mCustomAudienceServiceFilter = customAudienceServiceFilter;
        mAdFilteringFeatureFactory = adFilteringFeatureFactory;
    }

    /**
     * Adds a user to a custom audience.
     *
     * @hide
     */
    @Override
    public void joinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull String ownerPackageName,
            @NonNull ICustomAudienceCallback callback) {

        // Logs API deprecated.
        logStatsdForDeprecation(
                ownerPackageName, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        // Send back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE);
        }
    }

    /**
     * Adds the user to the {@link CustomAudience} fetched from a {@code fetchUri}
     *
     * @hide
     */
    @Override
    public void fetchAndJoinCustomAudience(
            @NonNull FetchAndJoinCustomAudienceInput input,
            @NonNull FetchAndJoinCustomAudienceCallback callback) {

        // Logs API deprecated.
        logStatsdForDeprecation(
                input.getCallerPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE);
        }
    }

    @Override
    public void scheduleCustomAudienceUpdate(
            ScheduleCustomAudienceUpdateInput input,
            ScheduleCustomAudienceUpdateCallback callback) {

        // Logs API deprecated.
        logStatsdForDeprecation(
                input.getCallerPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE);
        }
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

        // Logs API deprecated.
        logStatsdForDeprecation(
                ownerPackageName, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE);
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
            long biddingLogicJsVersion,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull CustomAudienceOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);
        }
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
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);
        }
    }

    /**
     * Resets all custom audience overrides for a given caller.
     *
     * @hide
     */
    @Override
    public void resetAllCustomAudienceOverrides(@NonNull CustomAudienceOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);
        }
    }

    private void logStatsdForDeprecation(String packageName, int apiName) {
        mAdServicesLogger.logFledgeApiCallStats(
                apiName, packageName, STATUS_ADSERVICES_DISABLED, /* latencyMs */ 0);
        sLogger.e("Got in-coming calls but CustomAudienceService APIs are deprecated.");
    }
}
