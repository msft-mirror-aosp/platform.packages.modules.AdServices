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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesCommonManager.MODULE_MEASUREMENT;
import static android.adservices.common.AdServicesCommonManager.MODULE_ON_DEVICE_PERSONALIZATION;
import static android.adservices.common.AdServicesCommonManager.MODULE_PROTECTED_APP_SIGNALS;
import static android.adservices.common.AdServicesCommonManager.MODULE_PROTECTED_AUDIENCE;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_ENABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_TOPICS;
import static android.adservices.common.AdServicesCommonManager.NOTIFICATION_NONE;
import static android.adservices.common.AdServicesCommonManager.NOTIFICATION_ONGOING;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN;
import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_STATE;
import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_STATE_COMPAT;
import static android.adservices.common.AdServicesPermissions.MODIFY_ADSERVICES_STATE;
import static android.adservices.common.AdServicesPermissions.MODIFY_ADSERVICES_STATE_COMPAT;
import static android.adservices.common.AdServicesPermissions.UPDATE_PRIVILEGED_AD_ID;
import static android.adservices.common.AdServicesPermissions.UPDATE_PRIVILEGED_AD_ID_COMPAT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_ACTIVITY_DISABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.ConsentStatus.SERVICE_NOT_ENABLED;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__COMMON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_ADSERVICES_COMMON_STATES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SERVICES_ENTRY_POINT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_UPDATE_AD_ID_API_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.service.ui.constants.DebugMessages.BACK_COMPAT_FEATURE_ENABLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.CALLER_NOT_ALLOWED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.ENABLE_AD_SERVICES_API_CALLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.ENABLE_AD_SERVICES_API_DISABLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.ENABLE_AD_SERVICES_API_ENABLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.IS_AD_SERVICES_ENABLED_API_CALLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.SET_AD_SERVICES_ENABLED_API_CALLED_MESSAGE;
import static com.android.adservices.service.ui.constants.DebugMessages.UNAUTHORIZED_CALLER_MESSAGE;

import android.adservices.adid.AdId;
import android.adservices.common.AdServicesCommonStates;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.AdServicesStates;
import android.adservices.common.CallerMetadata;
import android.adservices.common.ConsentStatus;
import android.adservices.common.EnableAdServicesResponse;
import android.adservices.common.GetAdServicesCommonStatesParams;
import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IAdServicesCommonService;
import android.adservices.common.IAdServicesCommonStatesCallback;
import android.adservices.common.IEnableAdServicesCallback;
import android.adservices.common.IRequestAdServicesModuleOverridesCallback;
import android.adservices.common.IRequestAdServicesModuleUserChoicesCallback;
import android.adservices.common.IUpdateAdIdCallback;
import android.adservices.common.IsAdServicesEnabledResult;
import android.adservices.common.UpdateAdIdRequest;
import android.adservices.common.UpdateAdServicesModuleStatesParams;
import android.adservices.common.UpdateAdServicesUserChoicesParams;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.os.Trace;
import android.util.SparseIntArray;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.ui.UxEngine;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.shared.util.Clock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link IAdServicesCommonService}.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.S)
public class AdServicesCommonServiceImpl extends IAdServicesCommonService.Stub {

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    public final String ADSERVICES_STATUS_SHARED_PREFERENCE = "AdserviceStatusSharedPreference";
    private final Context mContext;
    private final UxEngine mUxEngine;
    private final UxStatesManager mUxStatesManager;

    private final AdServicesLogger mAdServicesLogger;
    private final Flags mFlags;
    private final DebugFlags mDebugFlags;
    private final AdIdWorker mAdIdWorker;
    private final Clock mClock;

    public AdServicesCommonServiceImpl(
            Context context,
            Flags flags,
            DebugFlags debugFlags,
            UxEngine uxEngine,
            UxStatesManager uxStatesManager,
            AdIdWorker adIdWorker,
            AdServicesLogger adServicesLogger,
            @NonNull Clock clock) {
        mContext = context;
        mFlags = flags;
        mDebugFlags = debugFlags;
        mUxEngine = uxEngine;
        mUxStatesManager = uxStatesManager;
        mAdIdWorker = adIdWorker;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
    }

    @Override
    @RequiresPermission(anyOf = {ACCESS_ADSERVICES_STATE, ACCESS_ADSERVICES_STATE_COMPAT})
    public void isAdServicesEnabled(@NonNull IAdServicesCommonCallback callback) {
        LogUtil.d(IS_AD_SERVICES_ENABLED_API_CALLED_MESSAGE);

        boolean hasAccessAdServicesStatePermission =
                PermissionHelper.hasAccessAdServicesStatePermission(mContext);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!hasAccessAdServicesStatePermission) {
                            LogUtil.e(UNAUTHORIZED_CALLER_MESSAGE);
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            return;
                        }

                        boolean isAdServicesEnabled = mFlags.getAdServicesEnabled();
                        if (mFlags.isBackCompatActivityFeatureEnabled()) {
                            LogUtil.d(BACK_COMPAT_FEATURE_ENABLED_MESSAGE);
                            isAdServicesEnabled &=
                                    PackageManagerCompatUtils.isAdServicesActivityEnabled(mContext);
                        }

                        // TO-DO (b/286664178): remove the block after API is fully ramped up.
                        if (mFlags.getEnableAdServicesSystemApi()
                                && ConsentManager.getInstance().getUx() != null) {
                            LogUtil.d(ENABLE_AD_SERVICES_API_ENABLED_MESSAGE);
                            // PS entry point should be hidden from unenrolled users.
                            isAdServicesEnabled &= mUxStatesManager.isEnrolledUser(mContext);
                        } else {
                            LogUtil.d(ENABLE_AD_SERVICES_API_DISABLED_MESSAGE);
                            // Reconsent is already handled by the enableAdServices API.
                            reconsentIfNeededForEU();
                        }

                        LogUtil.d("isAdServiceseEnabled: " + isAdServicesEnabled);
                        callback.onResult(
                                new IsAdServicesEnabledResult.Builder()
                                        .setAdServicesEnabled(isAdServicesEnabled)
                                        .build());
                    } catch (Exception e) {
                        try {
                            callback.onFailure(STATUS_INTERNAL_ERROR);
                        } catch (RemoteException re) {
                            LogUtil.e(re, "Unable to send result to the callback");
                            ErrorLogUtil.e(
                                    re,
                                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                        }
                    }
                });
    }

    /**
     * Set the adservices entry point Status from UI side, and also check adid zero-out status, and
     * Schedule notification if both adservices entry point enabled and adid not opt-out and
     * Adservice Is enabled
     */
    @Override
    @RequiresPermission(anyOf = {MODIFY_ADSERVICES_STATE, MODIFY_ADSERVICES_STATE_COMPAT})
    public void setAdServicesEnabled(boolean adServicesEntryPointEnabled, boolean adIdEnabled) {
        LogUtil.d(SET_AD_SERVICES_ENABLED_API_CALLED_MESSAGE);

        boolean hasModifyAdServicesStatePermission =
                PermissionHelper.hasModifyAdServicesStatePermission(mContext);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!hasModifyAdServicesStatePermission) {
                            // TODO(b/242578032): handle the security exception in a better way
                            LogUtil.d(UNAUTHORIZED_CALLER_MESSAGE);
                            return;
                        }

                        SharedPreferences preferences = getPrefs();

                        int adServiceEntryPointStatusInt =
                                adServicesEntryPointEnabled
                                        ? ADSERVICES_ENTRY_POINT_STATUS_ENABLE
                                        : ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt(
                                KEY_ADSERVICES_ENTRY_POINT_STATUS, adServiceEntryPointStatusInt);
                        if (!editor.commit()) {
                            LogUtil.e("saving to the sharedpreference failed");
                            ErrorLogUtil.e(
                                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                        }
                        LogUtil.d(
                                "adid status is "
                                        + adIdEnabled
                                        + ", adservice status is "
                                        + mFlags.getAdServicesEnabled());
                        LogUtil.d("entry point: " + adServicesEntryPointEnabled);

                        ConsentManager consentManager = ConsentManager.getInstance();
                        consentManager.setAdIdEnabled(adIdEnabled);
                        if (mFlags.getAdServicesEnabled() && adServicesEntryPointEnabled) {
                            // Check if it is reconsent for ROW.
                            if (reconsentIfNeededForROW()) {
                                LogUtil.d("Reconsent for ROW.");
                                ConsentNotificationJobService.schedule(mContext, adIdEnabled, true);
                            } else if (getFirstConsentStatus()) {
                                ConsentNotificationJobService.schedule(
                                        mContext, adIdEnabled, false);
                            }

                            if (ConsentManager.getInstance().getConsent().isGiven()) {
                                PackageChangedReceiver.enableReceiver(mContext, mFlags);
                                BackgroundJobsManager.scheduleAllBackgroundJobs(mContext);
                            }
                        }
                    } catch (Exception e) {
                        LogUtil.e(
                                "unable to save the adservices entry point status of "
                                        + e.getMessage());
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SERVICES_ENTRY_POINT_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                    }
                });
    }

    /** Init the AdServices Status Service. */
    public void init() {}

    /** Check EU device and reconsent logic and schedule the notification if needed. */
    public void reconsentIfNeededForEU() {
        boolean adserviceEnabled = mFlags.getAdServicesEnabled();
        if (adserviceEnabled
                && mFlags.getGaUxFeatureEnabled()
                && DeviceRegionProvider.isEuDevice(mContext)) {
            // Check if GA UX was notice before
            ConsentManager consentManager = ConsentManager.getInstance();
            if (!consentManager.wasGaUxNotificationDisplayed()) {
                // Check Beta notification displayed and user opt-in, we will re-consent
                SharedPreferences preferences = getPrefs();
                // Check the setAdServicesEnabled was called before
                if (preferences.contains(KEY_ADSERVICES_ENTRY_POINT_STATUS)
                        && consentManager.getConsent().isGiven()) {
                    ConsentNotificationJobService.schedule(mContext, false, true);
                }
            }
        }
    }

    /** Check if user is first time consent */
    public boolean getFirstConsentStatus() {
        ConsentManager consentManager = ConsentManager.getInstance();
        return (!consentManager.wasGaUxNotificationDisplayed()
                        && !consentManager.wasNotificationDisplayed())
                || mDebugFlags.getConsentNotificationDebugMode();
    }

    /** Check ROW device and see if it fit reconsent */
    public boolean reconsentIfNeededForROW() {
        ConsentManager consentManager = ConsentManager.getInstance();
        return mFlags.getGaUxFeatureEnabled()
                && !DeviceRegionProvider.isEuDevice(mContext)
                && !consentManager.wasGaUxNotificationDisplayed()
                && consentManager.wasNotificationDisplayed()
                && consentManager.getConsent().isGiven();
    }

    @Override
    @RequiresPermission(anyOf = {MODIFY_ADSERVICES_STATE, MODIFY_ADSERVICES_STATE_COMPAT})
    public void enableAdServices(
            @NonNull AdServicesStates adServicesStates,
            @NonNull IEnableAdServicesCallback callback) {
        LogUtil.d(ENABLE_AD_SERVICES_API_CALLED_MESSAGE);

        Trace.beginSection("AdServicesCommonService#EnableAdServices_PermissionCheck");
        boolean authorizedCaller = PermissionHelper.hasModifyAdServicesStatePermission(mContext);
        Trace.endSection();

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!authorizedCaller) {
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            LogUtil.d(UNAUTHORIZED_CALLER_MESSAGE);
                            return;
                        }

                        // TO-DO (b/286664178): remove the block after API is fully ramped up.
                        if (!mFlags.getEnableAdServicesSystemApi()) {
                            handleEnableAdServiceSuccess(
                                    callback, /* apiEnabled= */ false, /* success= */ false);
                            LogUtil.d("enableAdServices(): API is disabled.");
                            return;
                        }

                        // On T+ devices, {@link AdServicesCommon} only use the service that comes
                        // from AdServices APK. Modifications made by
                        // BackCompatInit affect only components within the ExtServices APK and have
                        // no effect on services originating from the AdServices APK.
                        // On S- devices, the AdServicesCommonService is solely
                        // contained within the ExtServices APK
                        if (mFlags.getEnableBackCompatInit()) {
                            LogUtil.d("BackCompatInit is enabled in enableAdServices().");
                            AdServicesBackCompatInit.getInstance().initializeComponents();

                            if (!PackageManagerCompatUtils.isAdServicesActivityEnabled(mContext)) {
                                callback.onFailure(STATUS_ADSERVICES_ACTIVITY_DISABLED);
                                LogUtil.d("BackCompatInit failed to enable rb activities.");
                                return;
                            }
                        }

                        Trace.beginSection("AdServicesCommonService#EnableAdServices_UxEngineFlow");
                        mUxEngine.start(adServicesStates);
                        Trace.endSection();

                        LogUtil.d("enableAdServices(): UxEngine started.");
                        handleEnableAdServiceSuccess(
                                callback, /* apiEnabled= */ true, /* success= */ true);
                    } catch (Exception e) {
                        LogUtil.e("enableAdServices() failed to complete: " + e.getMessage());
                    }
                });
    }

    /**
     * Updates {@link AdId} cache in {@link AdIdCacheManager} when the device changes {@link AdId}.
     * This API is used by AdIdProvider to update the {@link AdId} Cache.
     */
    @Override
    @RequiresPermission(anyOf = {UPDATE_PRIVILEGED_AD_ID, UPDATE_PRIVILEGED_AD_ID_COMPAT})
    public void updateAdIdCache(
            @NonNull UpdateAdIdRequest updateAdIdRequest, @NonNull IUpdateAdIdCallback callback) {
        boolean authorizedCaller = PermissionHelper.hasUpdateAdIdCachePermission(mContext);
        int callerUid = Binder.getCallingUid();

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!authorizedCaller) {
                            LogUtil.w(
                                    "Caller %d is not authorized to update AdId Cache!", callerUid);
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            return;
                        }

                        mAdIdWorker.updateAdId(updateAdIdRequest);

                        // The message in on debugging purpose.
                        callback.onResult("Success");
                    } catch (Exception e) {
                        LogUtil.e(e, "updateAdIdCache() failed to complete.");
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_UPDATE_AD_ID_API_ERROR,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID);

                        try {
                            callback.onFailure(STATUS_INTERNAL_ERROR);
                        } catch (RemoteException ex) {
                            LogUtil.e("Unable to send result to the callback " + ex.getMessage());
                        }
                    }
                });
    }

    /** add a new API for ODP to get the common states from the adservices service. */
    @Override
    @RequiresPermission(anyOf = {ACCESS_ADSERVICES_STATE, ACCESS_ADSERVICES_STATE_COMPAT})
    public void getAdServicesCommonStates(
            @Nonnull GetAdServicesCommonStatesParams param,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IAdServicesCommonStatesCallback callback) {
        Objects.requireNonNull(param);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        final long serviceStartTime = mClock.elapsedRealtime();
        final String packageName = param.getAppPackageName();
        final String sdkName = param.getSdkPackageName();
        LogUtil.i(packageName);
        boolean hasAccessAdServicesCommonStatePermission =
                PermissionHelper.hasAccessAdServicesCommonStatePermission(mContext, packageName);

        sBackgroundExecutor.execute(
                () -> {
                    int resultCode = STATUS_SUCCESS;
                    try {
                        // Check permissions
                        if (!hasAccessAdServicesCommonStatePermission) {
                            LogUtil.e(UNAUTHORIZED_CALLER_MESSAGE);
                            resultCode = STATUS_UNAUTHORIZED;
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            return;
                        }
                        // Check package in allowlist
                        boolean appCanUseGetCommonStatesService =
                                AllowLists.isPackageAllowListed(
                                        mFlags.getAdServicesCommonStatesAllowList(),
                                        param.getAppPackageName());
                        if (!appCanUseGetCommonStatesService) {
                            LogUtil.e(CALLER_NOT_ALLOWED_MESSAGE);
                            resultCode = STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST;
                            callback.onFailure(resultCode);
                            return;
                        }
                        ConsentManager consentManager = ConsentManager.getInstance();
                        if (mFlags.isGetAdServicesCommonStatesApiEnabled()) {
                            LogUtil.d("start getting states");
                            AdServicesCommonStates adservicesCommonStates =
                                    new AdServicesCommonStates.Builder()
                                            .setMeasurementState(
                                                    getConsentStatus(
                                                            consentManager,
                                                            AdServicesApiType.MEASUREMENTS))
                                            .setPaState(
                                                    getConsentStatus(
                                                            consentManager,
                                                            AdServicesApiType.FLEDGE))
                                            .build();
                            callback.onResult(
                                    new AdServicesCommonStatesResponse.Builder(
                                                    adservicesCommonStates)
                                            .build());
                            consentManager.setMeasurementDataReset(false);
                            consentManager.setPaDataReset(false);
                            return;
                        }
                        LogUtil.d("service is not started");
                        AdServicesCommonStates adservicesCommonStates =
                                new AdServicesCommonStates.Builder()
                                        .setMeasurementState(SERVICE_NOT_ENABLED)
                                        .setPaState(SERVICE_NOT_ENABLED)
                                        .build();
                        callback.onResult(
                                new AdServicesCommonStatesResponse.Builder(adservicesCommonStates)
                                        .build());
                    } catch (Exception e) {
                        LogUtil.e("get error " + e.getMessage());
                        resultCode = STATUS_INTERNAL_ERROR;
                        try {
                            callback.onFailure(STATUS_INTERNAL_ERROR);
                        } catch (RemoteException ex) {
                            LogUtil.e("Unable to send result to the callback " + ex.getMessage());
                        }
                    } finally {
                        mAdServicesLogger.logApiCallStats(
                                new ApiCallStats.Builder()
                                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__COMMON)
                                        .setApiName(
                                                AD_SERVICES_API_CALLED__API_NAME__GET_ADSERVICES_COMMON_STATES)
                                        .setAppPackageName(packageName)
                                        .setSdkPackageName(sdkName)
                                        .setLatencyMillisecond(
                                                getLatency(callerMetadata, serviceStartTime))
                                        .setResultCode(resultCode)
                                        .build());
                    }
                });
    }

    /** Sets AdServices feature states. */
    @Override
    @RequiresPermission(anyOf = {MODIFY_ADSERVICES_STATE, MODIFY_ADSERVICES_STATE_COMPAT})
    public void requestAdServicesModuleOverrides(
            UpdateAdServicesModuleStatesParams updateParams,
            IRequestAdServicesModuleOverridesCallback callback) {

        boolean authorizedCaller = PermissionHelper.hasModifyAdServicesStatePermission(mContext);
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!authorizedCaller) {
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            LogUtil.d(UNAUTHORIZED_CALLER_MESSAGE);
                            return;
                        }
                        boolean businessLogicMigrationEnabled =
                                FlagsFactory.getFlags()
                                        .getAdServicesConsentBusinessLogicMigrationEnabled();
                        if (!businessLogicMigrationEnabled) {
                            callback.onFailure(STATUS_KILLSWITCH_ENABLED);
                            LogUtil.d("Business logic migration flag not enabled");
                            return;
                        }
                        sendNotificationIfNeededAndUpdateState(updateParams);
                        callback.onSuccess();
                    } catch (Exception e) {
                        LogUtil.e(
                                "requestAdServicesModuleOverrides() failed to complete: "
                                        + e.getMessage());
                        try {
                            callback.onFailure(STATUS_INTERNAL_ERROR);
                        } catch (RemoteException ex) {
                            LogUtil.e("Unable to send result to the callback " + ex.getMessage());
                        }
                    }
                });
    }

    private void sendNotificationIfNeededAndUpdateState(
            UpdateAdServicesModuleStatesParams updateParams) {
        SparseIntArray moduleStates = updateParams.getModuleStates();
        int notificationType = updateParams.getNotificationType();
        ConsentManager consentManager = ConsentManager.getInstance();
        boolean hasAnyModuleStateChanges = false;
        boolean isPersonalizationBeingEnabled = false;
        boolean anyUserChoicesKnown = false;
        boolean isAnyToggleOnForAnyNewModule = false;
        boolean hasExistingPersonalization = false;
        boolean isVisibleNotificationType = notificationType != NOTIFICATION_NONE;
        boolean isOngoingNotificationType = notificationType == NOTIFICATION_ONGOING;

        // 1. determine conditions
        for (int i = 0; i < moduleStates.size(); i++) {
            int module = moduleStates.keyAt(i);
            int desiredState = moduleStates.valueAt(i);
            int curState = consentManager.getModuleState(module);
            int curUserChoice = consentManager.getUserChoice(module);

            if (curState != desiredState
                    && desiredState == MODULE_STATE_ENABLED
                    && module != MODULE_MEASUREMENT) {
                isPersonalizationBeingEnabled = true;
            }
            if (curState != desiredState) {
                hasAnyModuleStateChanges = true;
            }
            if (curUserChoice != USER_CHOICE_UNKNOWN) {
                anyUserChoicesKnown = true;
            }
            if (isAnyToggleOnForNewModule(consentManager, module, curState, desiredState)) {
                isAnyToggleOnForAnyNewModule = true;
            }
            if (module != MODULE_MEASUREMENT && curState == MODULE_STATE_ENABLED) {
                hasExistingPersonalization = true;
            }
        }

        // 2. determine all cases that require a notification
        boolean isFirstTimeNotification =
                isVisibleNotificationType && isPersonalizationBeingEnabled && !anyUserChoicesKnown;
        boolean isValidRenotifyWithExistingPersonalization =
                isVisibleNotificationType
                        && isPersonalizationBeingEnabled
                        && isAnyToggleOnForAnyNewModule
                        && hasExistingPersonalization;
        boolean isLimitedNotification =
                isVisibleNotificationType
                        && !isPersonalizationBeingEnabled
                        && hasAnyModuleStateChanges;

        // 3. schedule a notification if required
        if (isFirstTimeNotification
                || isValidRenotifyWithExistingPersonalization
                || isLimitedNotification) {
            ConsentNotificationJobService.scheduleNotificationV2(
                    mContext,
                    anyUserChoicesKnown,
                    isPersonalizationBeingEnabled,
                    isOngoingNotificationType);
        }

        // 4. reset any user choices of disabled modules
        List<AdServicesModuleUserChoice> adServicesUserChoiceList = new ArrayList<>();
        for (int i = 0; i < moduleStates.size(); i++) {
            int key = moduleStates.keyAt(i);
            int value = moduleStates.valueAt(i);
            if (value != MODULE_STATE_ENABLED) {
                adServicesUserChoiceList.add(
                        new AdServicesModuleUserChoice(key, USER_CHOICE_UNKNOWN));
            }
        }
        consentManager.setUserChoices(adServicesUserChoiceList);

        // 5. finally, set the module state
        boolean isPersonalizationEnabledFirstTimeForExistingUser =
                anyUserChoicesKnown && isPersonalizationBeingEnabled && !hasExistingPersonalization;
        if (isPersonalizationEnabledFirstTimeForExistingUser) {
            // if existing user having personalization being enabled for first time, then skip as
            // this is currently not allowed
            return;
        }
        consentManager.setModuleStates(moduleStates);
    }

    private static boolean isAnyToggleOnForNewModule(
            ConsentManager consentManager, int module, int curState, int desiredState) {
        if (desiredState == curState || desiredState != MODULE_STATE_ENABLED) {
            // same state OR not being enabled, therefore is not a new module being introduced
            return false;
        }
        Map<Integer, int[]> moduleToToggleMap =
                Map.of(
                        MODULE_MEASUREMENT, new int[] {MODULE_MEASUREMENT},
                        MODULE_PROTECTED_AUDIENCE, new int[] {MODULE_PROTECTED_AUDIENCE},
                        MODULE_TOPICS, new int[] {MODULE_TOPICS},
                        MODULE_PROTECTED_APP_SIGNALS, new int[] {MODULE_PROTECTED_AUDIENCE},
                        MODULE_ON_DEVICE_PERSONALIZATION,
                                new int[] {MODULE_PROTECTED_AUDIENCE, MODULE_MEASUREMENT});
        return Arrays.stream(moduleToToggleMap.getOrDefault(module, new int[] {}))
                .anyMatch(toggle -> consentManager.getUserChoice(toggle) == USER_CHOICE_OPTED_IN);
    }

    /** Sets AdServices feature user choices. */
    @Override
    @RequiresPermission(anyOf = {MODIFY_ADSERVICES_STATE, MODIFY_ADSERVICES_STATE_COMPAT})
    public void requestAdServicesModuleUserChoices(
            UpdateAdServicesUserChoicesParams updateParams,
            IRequestAdServicesModuleUserChoicesCallback callback) {

        boolean authorizedCaller = PermissionHelper.hasModifyAdServicesStatePermission(mContext);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        if (!authorizedCaller) {
                            callback.onFailure(STATUS_UNAUTHORIZED);
                            LogUtil.d(UNAUTHORIZED_CALLER_MESSAGE);
                            return;
                        }
                        boolean businessLogicMigrationEnabled =
                                FlagsFactory.getFlags()
                                        .getAdServicesConsentBusinessLogicMigrationEnabled();
                        if (!businessLogicMigrationEnabled) {
                            callback.onFailure(STATUS_KILLSWITCH_ENABLED);
                            LogUtil.d("Business logic migration flag not enabled");
                            return;
                        }
                        filterAndSetUserChoices(updateParams);
                        LogUtil.i("requestAdServicesModuleUserChoices");
                        callback.onSuccess();

                    } catch (Exception e) {
                        LogUtil.e(
                                "requestAdServicesModuleUserChoices() failed to complete: "
                                        + e.getMessage());
                    }
                });
    }

    private void filterAndSetUserChoices(UpdateAdServicesUserChoicesParams updateParams) {
        ConsentManager consentManager = ConsentManager.getInstance();
        List<AdServicesModuleUserChoice> adServicesFeatureUserChoiceList = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : updateParams.getUserChoiceMap().entrySet()) {
            // If current user choice is unknown, then set to desired user choice.
            // If setting to unknown user choice, then it is an explicit decision by the caller that
            // plans to override any previous user choice.
            if (consentManager.getUserChoice(entry.getKey()) == USER_CHOICE_UNKNOWN
                    || entry.getValue() == USER_CHOICE_UNKNOWN) {
                adServicesFeatureUserChoiceList.add(
                        new AdServicesModuleUserChoice(entry.getKey(), entry.getValue()));
            }
        }
        consentManager.setUserChoices(adServicesFeatureUserChoiceList);
    }

    private int getLatency(CallerMetadata metadata, long serviceStartTime) {
        long binderCallStartTimeMillis = metadata.getBinderElapsedTimestamp();
        long serviceLatency = mClock.elapsedRealtime() - serviceStartTime;
        // Double it to simulate the return binder time is same to call binder time
        long binderLatency = (serviceStartTime - binderCallStartTimeMillis) * 2;
        LogUtil.v(
                "binder call start time "
                        + binderCallStartTimeMillis
                        + ", servicve Start time "
                        + serviceStartTime
                        + ", service latency "
                        + serviceLatency);
        return (int) (serviceLatency + binderLatency);
    }

    private @ConsentStatus.ConsentStatusCode int getConsentStatus(
            ConsentManager consentManager, AdServicesApiType apiType) {
        if (apiType == AdServicesApiType.FLEDGE) {
            if (consentManager.isOdpFledgeConsentGiven()) {
                if (consentManager.isPaDataReset()) {
                    return ConsentStatus.WAS_RESET;
                }
                return ConsentStatus.GIVEN;
            }
            return ConsentStatus.REVOKED;
        }
        if (apiType == AdServicesApiType.MEASUREMENTS) {
            if (consentManager.isOdpMeasurementConsentGiven()) {
                if (consentManager.isMeasurementDataReset()) {
                    return ConsentStatus.WAS_RESET;
                }
                return ConsentStatus.GIVEN;
            }
            return ConsentStatus.REVOKED;
        }
        return ConsentStatus.REVOKED;
    }

    private void handleEnableAdServiceSuccess(
            IEnableAdServicesCallback callback, boolean apiEnabled, boolean success)
            throws RemoteException {
        callback.onResult(
                new EnableAdServicesResponse.Builder()
                        .setStatusCode(STATUS_SUCCESS)
                        .setApiEnabled(apiEnabled)
                        .setSuccess(success)
                        .build());
    }

    @SuppressWarnings("AvoidSharedPreferences") // Legacy usage
    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(
                ADSERVICES_STATUS_SHARED_PREFERENCE, Context.MODE_PRIVATE);
    }
}
