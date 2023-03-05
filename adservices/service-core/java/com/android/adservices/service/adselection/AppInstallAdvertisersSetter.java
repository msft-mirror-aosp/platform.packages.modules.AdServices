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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SET_APP_INSTALL_ADVERTISERS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;

import android.adservices.adselection.SetAppInstallAdvertisersCallback;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.os.Build;
import android.os.LimitExceededException;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBAppInstallPermissions;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Encapsulates the Set App Install Advertisers logic */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppInstallAdvertisersSetter {

    private static final String AD_TECH_IDENTIFIER_ERROR_MESSAGE_SCOPE = "app install adtech set";
    private static final String AD_TECH_IDENTIFIER_ERROR_MESSAGE_ROLE = "adtech";

    @NonNull private final AppInstallDao mAppInstallDao;
    @NonNull private final ListeningExecutorService mExecutorService;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final ConsentManager mConsentManager;
    private final int mCallerUid;

    public AppInstallAdvertisersSetter(
            @NonNull AppInstallDao appInstallDao,
            @NonNull ExecutorService executor,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull final Flags flags,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final ConsentManager consentManager,
            int callerUid) {
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(consentManager);

        mAppInstallDao = appInstallDao;
        mExecutorService = MoreExecutors.listeningDecorator(executor);
        mAdServicesLogger = adServicesLogger;
        mCallerUid = callerUid;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mConsentManager = consentManager;
    }

    /**
     * Sets the app install advertisers for the caller.
     *
     * <p>Stores the association between the listed adtechs and the caller in the app install
     * database.
     *
     * @param input object containing the package name of the caller and a list of adtechs
     * @param callback callback function to be called in case of success or failure
     */
    public void setAppInstallAdvertisers(
            @NonNull SetAppInstallAdvertisersInput input,
            @NonNull SetAppInstallAdvertisersCallback callback) {
        LogUtil.v("Executing setAppInstallAdvertisers API");

        FluentFuture.from(
                        mExecutorService.submit(
                                () ->
                                        doSetAppInstallAdvertisers(
                                                input.getAdvertisers(),
                                                input.getCallerPackageName())))
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LogUtil.v("SetAppInstallAdvertisers succeeded!");
                                invokeSuccess(callback, AdServicesStatusUtils.STATUS_SUCCESS);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "SetAppInstallAdvertisers invocation failed!");
                                if (t instanceof ConsentManager.RevokedConsentException) {
                                    invokeSuccess(
                                            callback,
                                            AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
                                } else {
                                    notifyFailureToCaller(callback, t);
                                }
                            }
                        },
                        mExecutorService);
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull SetAppInstallAdvertisersCallback callback,
            int statusCode,
            String errorMessage) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
            resultCode = statusCode;
        } catch (RemoteException e) {
            // TODO(b/269724912) Unit test this block
            LogUtil.e(e, "Unable to send failed result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS, resultCode, 0);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void invokeSuccess(@NonNull SetAppInstallAdvertisersCallback callback, int resultCode) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            // TODO(b/269724912) Unit test this block
            LogUtil.e(e, "Unable to send successful result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS, resultCode, 0);
        }
    }

    private void notifyFailureToCaller(
            @NonNull SetAppInstallAdvertisersCallback callback, @NonNull Throwable t) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, t.getMessage());
        } else if (t instanceof WrongCallingApplicationStateException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, t.getMessage());
        } else if (t instanceof FledgeAllowListsFilter.AppNotAllowedException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED, t.getMessage());
        } else if (t instanceof FledgeAuthorizationFilter.CallerMismatchException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_UNAUTHORIZED, t.getMessage());
        } else if (t instanceof LimitExceededException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, t.getMessage());
        } else {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage());
        }
    }

    private Void doSetAppInstallAdvertisers(
            Set<AdTechIdentifier> advertisers, String callerPackageName) {
        validateRequest(advertisers, callerPackageName);

        ArrayList<DBAppInstallPermissions> permissions = new ArrayList<>();
        for (AdTechIdentifier advertiser : advertisers) {
            permissions.add(
                    new DBAppInstallPermissions.Builder()
                            .setPackageName(callerPackageName)
                            .setBuyer(advertiser)
                            .build());
        }
        mAppInstallDao.setAdTechsForPackage(callerPackageName, permissions);
        return null;
    }

    private void validateRequest(Set<AdTechIdentifier> advertisers, String callerPackageName) {
        LogUtil.v("Validating setAppInstallAdvertisers Request");
        mAdSelectionServiceFilter.filterRequest(
                null,
                callerPackageName,
                true,
                false,
                mCallerUid,
                AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                FLEDGE_API_SET_APP_INSTALL_ADVERTISERS);
        (new AdvertiserSetValidator(
                        new AdTechIdentifierValidator(
                                AD_TECH_IDENTIFIER_ERROR_MESSAGE_SCOPE,
                                AD_TECH_IDENTIFIER_ERROR_MESSAGE_ROLE)))
                .validate(advertisers);
        if (mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(callerPackageName)) {
            throw new ConsentManager.RevokedConsentException();
        }
    }
}
