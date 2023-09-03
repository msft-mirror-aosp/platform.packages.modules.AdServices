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

import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_FETCH_SIGNAL_UPDATES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.FetchSignalUpdatesCallback;
import android.adservices.signals.FetchSignalUpdatesInput;
import android.adservices.signals.IProtectedSignalsService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Implementation of the Protected Signals service. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ProtectedSignalsServiceImpl extends IProtectedSignalsService.Stub {

    public static final int TIMEOUT_MS = 5000;
    public static final long MAX_SIZE_BYTES = 10000;
    public static final String ADTECH_CALLER_NAME = "caller";
    public static final String CLASS_NAME = "ProtectedSignalsServiceImpl";
    public static final String FIELD_NAME = "fetchUri";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final FetchOrchestrator mFetchOrchestrator;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;

    @NonNull private final CustomAudienceServiceFilter mCustomAudienceServiceFilter;

    private ProtectedSignalsServiceImpl(@NonNull Context context) {
        this(
                context,
                new FetchOrchestrator(
                        AdServicesExecutors.getBackgroundExecutor(),
                        new UpdatesDownloader(
                                AdServicesExecutors.getLightWeightExecutor(),
                                new AdServicesHttpsClient(
                                        AdServicesExecutors.getBlockingExecutor(),
                                        TIMEOUT_MS,
                                        TIMEOUT_MS,
                                        MAX_SIZE_BYTES)),
                        new UpdateProcessingOrchestrator(
                                ProtectedSignalsDatabase.getInstance(context).protectedSignalsDao(),
                                new UpdateProcessorSelector(),
                                new UpdateEncoderEventHandler(context)),
                        new AdTechUriValidator(ADTECH_CALLER_NAME, "", CLASS_NAME, FIELD_NAME)),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                ConsentManager.getInstance(context),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance(),
                FlagsFactory.getFlags(),
                CallingAppUidSupplierBinderImpl.create(),
                new CustomAudienceServiceFilter(
                        context,
                        ConsentManager.getInstance(context),
                        FlagsFactory.getFlags(),
                        AppImportanceFilter.create(
                                context,
                                AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        FledgeAuthorizationFilter.create(
                                context, AdServicesLoggerImpl.getInstance()),
                        new FledgeAllowListsFilter(
                                FlagsFactory.getFlags(), AdServicesLoggerImpl.getInstance()),
                        Throttler.getInstance(FlagsFactory.getFlags())));
    }

    @VisibleForTesting
    public ProtectedSignalsServiceImpl(
            @NonNull Context context,
            @NonNull FetchOrchestrator fetchOrchestrator,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull ConsentManager consentManager,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull CustomAudienceServiceFilter customAudienceServiceFilter) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(fetchOrchestrator);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(customAudienceServiceFilter);

        mContext = context;
        mFetchOrchestrator = fetchOrchestrator;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mConsentManager = consentManager;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mCustomAudienceServiceFilter = customAudienceServiceFilter;
    }

    /** Creates a new instance of {@link ProtectedSignalsServiceImpl}. */
    public static ProtectedSignalsServiceImpl create(@NonNull Context context) {
        return new ProtectedSignalsServiceImpl(context);
    }

    @Override
    public void fetchSignalUpdates(
            @NonNull FetchSignalUpdatesInput fetchSignalUpdatesInput,
            @NonNull FetchSignalUpdatesCallback fetchSignalUpdatesCallback)
            throws RemoteException {
        sLogger.v("Entering fetchSignalUpdates");

        // TODO(b/296586554) Add API id
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        try {
            Objects.requireNonNull(fetchSignalUpdatesInput);
            Objects.requireNonNull(fetchSignalUpdatesCallback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(
                mContext, fetchSignalUpdatesInput.getCallerPackageName(), apiName);

        final int callerUid = getCallingUid(apiName);
        final DevContext devContext = mDevContextFilter.createDevContext();
        sLogger.v("Running fetchSignalUpdates");
        mExecutorService.execute(
                () ->
                        doFetchSignalUpdates(
                                fetchSignalUpdatesInput,
                                fetchSignalUpdatesCallback,
                                callerUid,
                                devContext));
    }

    private void doFetchSignalUpdates(
            FetchSignalUpdatesInput input,
            FetchSignalUpdatesCallback callback,
            int callerUid,
            DevContext devContext) {
        sLogger.v("Entering doFetchSignalUpdates");

        // TODO(b/296586554) Add API id
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                // Filter and validate request -- the custom audience filter does what we need so
                // I don't see much value in creating a new one
                AdTechIdentifier buyer =
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                input.getFetchUri(),
                                input.getCallerPackageName(),
                                mFlags.getDisableFledgeEnrollmentCheck(),
                                // We will always enforce the foreground check, I don't see a reason
                                // to make a flags for this like other APIs do
                                true,
                                true,
                                callerUid,
                                apiName,
                                PROTECTED_SIGNAL_API_FETCH_SIGNAL_UPDATES,
                                devContext);
                shouldLog = true;

                // Fail silently for revoked user consent
                if (!mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        input.getCallerPackageName())) {
                    sLogger.v("Fetching signal updates");
                    mFetchOrchestrator
                            .orchestrateFetch(
                                    input.getFetchUri(), buyer, input.getCallerPackageName())
                            .get();
                    // TODO(b/294900127) Schedule background job for periodically running encoding
                    // logic
                    // PeriodicEncodingJobService.scheduleIfNeeded(mContext, mFlags, false);
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    sLogger.v("Consent revoked");
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }
            } catch (Exception exception) {
                sLogger.d(exception, "Error encountered in fetchSignalUpdates, notifying caller");
                resultCode = notifyFailure(callback, exception);
                return;
            }

            callback.onSuccess();
        } catch (Exception exception) {
            sLogger.e(exception, "Unable to send result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode, 0);
            }
        }
    }

    // TODO(b/297055198) Refactor this method into a utility class
    private int getCallingUid(int apiNameLoggingId) throws IllegalStateException {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw illegalStateException;
        }
    }

    private int notifyFailure(FetchSignalUpdatesCallback callback, Throwable t)
            throws RemoteException {
        int resultCode;

        boolean isFilterException = t instanceof FilterException;

        if (isFilterException) {
            resultCode = FilterException.getResultCode(t);
        } else if (t instanceof IllegalArgumentException) {
            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        } else {
            sLogger.d(t, "Unexpected error during operation");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        }

        callback.onFailure(
                new FledgeErrorResponse.Builder()
                        .setStatusCode(resultCode)
                        .setErrorMessage(t.getMessage())
                        .build());
        return resultCode;
    }
}
