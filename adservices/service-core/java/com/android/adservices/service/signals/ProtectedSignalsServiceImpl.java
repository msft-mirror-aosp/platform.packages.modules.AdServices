/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals;

import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_NOT_GIVEN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_REVOKED_FOR_APP_AFTER_SETTING_FLEDGE_USE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_GET_CALLING_UID_ILLEGAL_STATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_GET_ENROLLMENT_AD_TECH_ID_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_BACKGROUND_CALLER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_CALLER_NOT_ALLOWED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_INTERNAL_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_UNAUTHORIZED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_INVALID_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_SERVICE_IMPL_NULL_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNABLE_SEND_RESULT_TO_CALLBACK;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNEXPECTED_ERROR_DURING_OPERATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_OTHER_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_UNSET;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.IProtectedSignalsService;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceExecutionLogger;
import com.android.adservices.service.stats.AdsRelevanceExecutionLoggerFactory;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.pas.UpdateSignalsApiCalledStats;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerNoLoggingImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Implementation of the Protected Signals service. */
@RequiresApi(Build.VERSION_CODES.S)
public class ProtectedSignalsServiceImpl extends IProtectedSignalsService.Stub {
    public static final long MAX_SIZE_BYTES = 10000;
    public static final String ADTECH_CALLER_NAME = "caller";
    public static final String CLASS_NAME = "ProtectedSignalsServiceImpl";
    public static final String FIELD_NAME = "updateUri";
    private static final String EMPTY_PACKAGE_NAME = "";
    private static final String EMPTY_SDK_NAME = "";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final UpdateSignalsOrchestrator mUpdateSignalsOrchestrator;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final DebugFlags mDebugFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;
    @NonNull private final ProtectedSignalsServiceFilter mProtectedSignalsServiceFilter;
    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLogger;

    private ProtectedSignalsServiceImpl(@NonNull Context context) {
        this(
                context,
                new UpdateSignalsOrchestrator(
                        AdServicesExecutors.getBackgroundExecutor(),
                        new UpdatesDownloader(
                                AdServicesExecutors.getLightWeightExecutor(),
                                new AdServicesHttpsClient(
                                        AdServicesExecutors.getBlockingExecutor(),
                                        FlagsFactory.getFlags()
                                                .getPasSignalsDownloadConnectionTimeoutMs(),
                                        FlagsFactory.getFlags()
                                                .getPasSignalsDownloadReadTimeoutMs(),
                                        FlagsFactory.getFlags()
                                                .getProtectedSignalsFetchSignalUpdatesMaxSizeBytes())),
                        new UpdateProcessingOrchestrator(
                                ProtectedSignalsDatabase.getInstance().protectedSignalsDao(),
                                new UpdateProcessorSelector(),
                                new UpdateEncoderEventHandler(
                                        context,
                                        new ForcedEncoderFactory(
                                                        FlagsFactory.getFlags()
                                                                .getFledgeEnableForcedEncodingAfterSignalsUpdate(),
                                                        FlagsFactory.getFlags()
                                                                .getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds(),
                                                        context)
                                                .createInstance()),
                                new SignalEvictionController(),
                                new ForcedEncoderFactory(
                                                FlagsFactory.getFlags()
                                                        .getFledgeEnableForcedEncodingAfterSignalsUpdate(),
                                                FlagsFactory.getFlags()
                                                        .getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds(),
                                                context)
                                        .createInstance()),
                        new AdTechUriValidator(ADTECH_CALLER_NAME, "", CLASS_NAME, FIELD_NAME),
                        Clock.systemUTC()),
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
                FlagsFactory.getFlags(),
                DebugFlags.getInstance(),
                CallingAppUidSupplierBinderImpl.create(),
                new ProtectedSignalsServiceFilter(
                        context,
                        new FledgeConsentFilter(
                                ConsentManager.getInstance(), AdServicesLoggerImpl.getInstance()),
                        FlagsFactory.getFlags(),
                        AppImportanceFilter.create(
                                context,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        FledgeAuthorizationFilter.create(
                                context, AdServicesLoggerImpl.getInstance()),
                        new FledgeAllowListsFilter(
                                FlagsFactory.getFlags(), AdServicesLoggerImpl.getInstance()),
                        new FledgeApiThrottleFilter(
                                Throttler.getInstance(), AdServicesLoggerImpl.getInstance())),
                EnrollmentDao.getInstance(),
                FlagsFactory.getFlags().getPasProductMetricsV1Enabled()
                        ? new UpdateSignalsProcessReportedLoggerImpl(
                                AdServicesLoggerImpl.getInstance(),
                                com.android.adservices.shared.util.Clock.getInstance())
                        : new UpdateSignalsProcessReportedLoggerNoLoggingImpl());
    }

    @VisibleForTesting
    public ProtectedSignalsServiceImpl(
            @NonNull Context context,
            @NonNull UpdateSignalsOrchestrator updateSignalsOrchestrator,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull ConsentManager consentManager,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull DebugFlags debugFlags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull ProtectedSignalsServiceFilter protectedSignalsServiceFilter,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(updateSignalsOrchestrator);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(protectedSignalsServiceFilter);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(updateSignalsProcessReportedLogger);

        mContext = context;
        mUpdateSignalsOrchestrator = updateSignalsOrchestrator;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mConsentManager = consentManager;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mDebugFlags = debugFlags;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mProtectedSignalsServiceFilter = protectedSignalsServiceFilter;
        mEnrollmentDao = enrollmentDao;
        mUpdateSignalsProcessReportedLogger = updateSignalsProcessReportedLogger;
    }

    /** Creates a new instance of {@link ProtectedSignalsServiceImpl}. */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static ProtectedSignalsServiceImpl create(@NonNull Context context) {
        return new ProtectedSignalsServiceImpl(context);
    }

    @Override
    public void updateSignals(
            @NonNull UpdateSignalsInput updateSignalsInput,
            @NonNull UpdateSignalsCallback updateSignalsCallback)
            throws RemoteException {

        mUpdateSignalsProcessReportedLogger.setUpdateSignalsStartTimestamp(
                com.android.adservices.shared.util.Clock.getInstance().elapsedRealtime());

        sLogger.v("Entering updateSignals");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
        String callerPackageName =
                updateSignalsInput == null
                        ? EMPTY_PACKAGE_NAME
                        : updateSignalsInput.getCallerPackageName();

        try {
            Objects.requireNonNull(updateSignalsInput);
            Objects.requireNonNull(updateSignalsCallback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logApiCallStats(
                    new ApiCallStats.Builder()
                            .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                            .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__FLEDGE)
                            .setApiName(apiName)
                            .setLatencyMillisecond(0)
                            .setResultCode(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT)
                            .setAppPackageName(callerPackageName)
                            .setSdkPackageName(EMPTY_SDK_NAME)
                            .build());
            mUpdateSignalsProcessReportedLogger.setAdservicesApiStatusCode(
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // TODO(b/376542959): replace this temporary solution for CEL inside Binder thread.
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    exception,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_SERVICE_IMPL_NULL_ARGUMENT,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
            // Rethrow because we want to fail fast
            throw exception;
        }

        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        callerPackageName,
                        new CallerMetadata.Builder()
                                .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                                .build(),
                        com.android.adservices.shared.util.Clock.getInstance(),
                        mAdServicesLogger,
                        mFlags,
                        apiName);
        AdsRelevanceExecutionLogger adsRelevanceExecutionLogger =
                adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext,
                updateSignalsInput.getCallerPackageName(),
                apiName,
                AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS);

        final int callerUid =
                getCallingUid(adsRelevanceExecutionLogger, mUpdateSignalsProcessReportedLogger);
        final DevContext devContext = mDevContextFilter.createDevContext();
        sLogger.v("Running updateSignals");
        mExecutorService.execute(
                () ->
                        doUpdateSignals(
                                updateSignalsInput,
                                updateSignalsCallback,
                                callerUid,
                                devContext,
                                adsRelevanceExecutionLogger,
                                mUpdateSignalsProcessReportedLogger));

        mUpdateSignalsProcessReportedLogger.logUpdateSignalsProcessReportedStats();
    }

    private void doUpdateSignals(
            UpdateSignalsInput input,
            UpdateSignalsCallback callback,
            int callerUid,
            DevContext devContext,
            AdsRelevanceExecutionLogger adsRelevanceExecutionLogger,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        sLogger.v("Entering doUpdateSignals");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;

        int resultCode = AdServicesStatusUtils.STATUS_UNSET;

        // Stats to log
        UpdateSignalsApiCalledStats.Builder jsonProcessingStatsBuilder = null;
        if (mFlags.getPasExtendedMetricsEnabled()) {
            // Stats to log
            jsonProcessingStatsBuilder = UpdateSignalsApiCalledStats.builder();
        }

        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                AdTechIdentifier buyer;
                try {
                    buyer =
                            mProtectedSignalsServiceFilter.filterRequestAndExtractIdentifier(
                                    input.getUpdateUri(),
                                    input.getCallerPackageName(),
                                    mFlags.getDisableFledgeEnrollmentCheck(),
                                    mFlags.getEnforceForegroundStatusForSignals(),
                                    // TODO (b/327187357): Move per-API/per-app consent into the
                                    //  filter
                                    /* enforceConsent= */ false,
                                    !mDebugFlags.getConsentNotificationDebugMode(),
                                    callerUid,
                                    apiName,
                                    PROTECTED_SIGNAL_API_UPDATE_SIGNALS,
                                    devContext);
                    shouldLog = true;
                } catch (Throwable t) {
                    throw new FilterException(t);
                }

                if (jsonProcessingStatsBuilder != null) {
                    /* You could save a DB call by building this into the filter, but it would
                     * make the code pretty complicated and be difficult to flag.
                     */

                    try {
                        EnrollmentData data =
                                mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(buyer);
                        if (data != null) {
                            jsonProcessingStatsBuilder.setAdTechId(data.getEnrollmentId());
                        }
                    } catch (Exception e) {
                        /* We blanket catch and ignore all exceptions here because
                         * we'd rather skip the logging than get a crash
                         */
                        sLogger.e(e, "Failed to get enrollment data for %s", buyer);
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_GET_ENROLLMENT_AD_TECH_ID_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                    }
                }

                // Fail silently for revoked per-API or per-app user consent
                // For UX notification or Privacy Sandbox opt-out failures, see the consent check in
                // the service filter
                // TODO (b/327187357): Move per-API/per-app consent into the filter
                if (mConsentManager.isPasFledgeConsentGiven()) {
                    if (!mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                            input.getCallerPackageName())) {
                        sLogger.v("Orchestrating signal update");
                        mUpdateSignalsOrchestrator
                                .orchestrateUpdate(
                                        input.getUpdateUri(),
                                        buyer,
                                        input.getCallerPackageName(),
                                        devContext,
                                        jsonProcessingStatsBuilder,
                                        updateSignalsProcessReportedLogger)
                                .get();
                        PeriodicEncodingJobService.scheduleIfNeeded(mContext, mFlags, false);
                        resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                    } else {
                        sLogger.v("Consent revoked");
                        resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                        ErrorLogUtil.e(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_REVOKED_FOR_APP_AFTER_SETTING_FLEDGE_USE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                    }
                } else {
                    sLogger.v("Consent revoked");
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                    ErrorLogUtil.e(
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FLEDGE_CONSENT_NOT_GIVEN,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                }
            } catch (ExecutionException exception) {
                sLogger.d(
                        exception,
                        "Error encountered in updateSignals, unpacking from ExecutionException"
                                + " and notifying caller");
                resultCode = notifyFailure(callback, exception.getCause());
                return;
            } catch (Exception exception) {
                sLogger.d(exception, "Error encountered in updateSignals, notifying caller");
                resultCode = notifyFailure(callback, exception);
                return;
            }
            callback.onSuccess();
        } catch (Exception exception) {
            sLogger.e(exception, "Unable to send result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            ErrorLogUtil.e(
                    exception,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNABLE_SEND_RESULT_TO_CALLBACK,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
        } finally {
            if (shouldLog) {
                adsRelevanceExecutionLogger.endAdsRelevanceApi(resultCode);
                updateSignalsProcessReportedLogger.setAdservicesApiStatusCode(resultCode);
            }
            if (jsonProcessingStatsBuilder != null) {
                if (jsonProcessingStatsBuilder.build().getJsonProcessingStatus()
                        == JSON_PROCESSING_STATUS_UNSET) {
                    if (resultCode == AdServicesStatusUtils.STATUS_SUCCESS) {
                        jsonProcessingStatsBuilder.setJsonProcessingStatus(
                                JSON_PROCESSING_STATUS_SUCCESS);
                    } else {
                        jsonProcessingStatsBuilder.setJsonProcessingStatus(
                                JSON_PROCESSING_STATUS_OTHER_ERROR);
                    }
                }

                /* Include adtech and package name only if the status is not success.
                 * Adtech name is added early if it was extracted successfully.
                 */
                if (jsonProcessingStatsBuilder.build().getJsonProcessingStatus()
                        == JSON_PROCESSING_STATUS_SUCCESS) {
                    jsonProcessingStatsBuilder.setAdTechId("");
                } else {
                    jsonProcessingStatsBuilder.setPackageUid(callerUid);
                }
                mAdServicesLogger.logUpdateSignalsApiCalledStats(
                        jsonProcessingStatsBuilder.build());
            }
        }
    }

    // TODO(b/297055198) Refactor this method into a utility class
    private int getCallingUid(
            AdsRelevanceExecutionLogger adsRelevanceExecutionLogger,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger)
            throws IllegalStateException {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            adsRelevanceExecutionLogger.endAdsRelevanceApi(
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            updateSignalsProcessReportedLogger.setAdservicesApiStatusCode(
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            // TODO(b/376542959): replace this temporary solution for CEL inside Binder thread.
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    illegalStateException,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_GET_CALLING_UID_ILLEGAL_STATE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
            throw illegalStateException;
        }
    }

    private int notifyFailure(UpdateSignalsCallback callback, Throwable t) throws RemoteException {
        sLogger.d(t, "Notifying caller about exception");
        int resultCode;

        boolean isFilterException = t instanceof FilterException;

        if (isFilterException) {
            if (t.getCause() instanceof ConsentManager.RevokedConsentException) {
                sLogger.v("Send success to caller for consent failure");
                callback.onSuccess();
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                // This return code may not be the most accurate (could be due to notification
                // failure or all APIs opted out), but filters have already logged the API response
                // at this point
                return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
            }
            resultCode = FilterException.getResultCode(t);
            logPasFilterExceptionCel(resultCode);
        } else if (t instanceof IllegalArgumentException) {
            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_INVALID_ARGUMENT,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
        } else {
            sLogger.d(t, "Unexpected error during operation");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNEXPECTED_ERROR_DURING_OPERATION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
        }

        callback.onFailure(
                new FledgeErrorResponse.Builder()
                        .setStatusCode(resultCode)
                        .setErrorMessage(t.getMessage())
                        .build());
        return resultCode;
    }

    private void logPasFilterExceptionCel(int resultCode) {
        switch (resultCode) {
            case AdServicesStatusUtils.STATUS_BACKGROUND_CALLER:
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_BACKGROUND_CALLER,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                break;
            case AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED:
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_CALLER_NOT_ALLOWED,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                break;
            case AdServicesStatusUtils.STATUS_UNAUTHORIZED:
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_UNAUTHORIZED,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                break;
            case AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED:
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                break;
            case AdServicesStatusUtils.STATUS_INTERNAL_ERROR:
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NOTIFY_FAILURE_FILTER_EXCEPTION_INTERNAL_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                break;
        }
    }
}
