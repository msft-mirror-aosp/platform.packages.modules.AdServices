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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;

import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.IProtectedSignalsService;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
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
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandlerFactory;
import com.android.adservices.service.signals.updateprocessors.updateencoder.UpdateEncoderEventHandler;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.Objects;
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

    @NonNull
    private final UpdateSignalsProcessReportedLoggerFactory
            mUpdateSignalsProcessReportedLoggerFactory;

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
                                                .getProtectedSignalsFetchSignalUpdatesMaxSizeBytes()),
                                FlagsFactory.getFlags().getProtectedSignalsUpdateSchemaVersion()),
                        new UpdateProcessingOrchestrator(
                                ProtectedSignalsDatabase.getInstance().protectedSignalsDao(),
                                new UpdateProcessorSelector(
                                        new EvictionPriorityHandlerFactory(
                                                FlagsFactory.getFlags()
                                                        .getProtectedSignalsEnablePrioritizedEviction())),
                                new UpdateEncoderEventHandler(
                                        context,
                                        new ForcedEncoderFactory(
                                                        FlagsFactory.getFlags()
                                                                .getFledgeEnableForcedEncodingAfterSignalsUpdate(),
                                                        FlagsFactory.getFlags()
                                                                .getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds(),
                                                        context)
                                                .createInstance()),
                                new SignalEvictionController(
                                        FlagsFactory.getFlags()
                                                .getProtectedSignalsMaxSignalSizePerBuyerBytes(),
                                        FlagsFactory.getFlags()
                                                .getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes(),
                                        FlagsFactory.getFlags()
                                                .getProtectedSignalsEnablePrioritizedEviction()),
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
                EnrollmentDao.getInstance(),
                new UpdateSignalsProcessReportedLoggerFactory(
                        FlagsFactory.getFlags().getPasProductMetricsV1Enabled()));
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
            @NonNull
                    UpdateSignalsProcessReportedLoggerFactory
                            updateSignalsProcessReportedLoggerFactory) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(updateSignalsOrchestrator);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(protectedSignalsServiceFilter);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(updateSignalsProcessReportedLoggerFactory);

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
        mUpdateSignalsProcessReportedLoggerFactory = updateSignalsProcessReportedLoggerFactory;
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
        sLogger.v("Entering updateSignals");

        // Logs API deprecated.
        logStatsdForDeprecation(
                updateSignalsInput.getCallerPackageName().isEmpty()
                        ? EMPTY_PACKAGE_NAME
                        : updateSignalsInput.getCallerPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS);

        // Send back deprecation message throw callback
        try {
            updateSignalsCallback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
        }
    }

    private void logStatsdForDeprecation(String packageName, int apiName) {
        mAdServicesLogger.logFledgeApiCallStats(
                apiName, packageName, STATUS_ADSERVICES_DISABLED, /* latencyMs */ 0);
        sLogger.e("Got in-coming calls but ProtectedSignalsService APIs are deprecated.");
    }
}
