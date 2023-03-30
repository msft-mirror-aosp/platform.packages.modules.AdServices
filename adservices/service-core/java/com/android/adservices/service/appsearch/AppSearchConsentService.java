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

package com.android.adservices.service.appsearch;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.RequiresApi;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.GlobalSearchSession;
import androidx.appsearch.app.PackageIdentifier;
import androidx.appsearch.platformstorage.PlatformStorage;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.consent.ConsentConstants;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class provides an interface to read/write consent data to AppSearch. This is used as the
 * source of truth for S-. When a device upgrades from S- to T+, the consent is initialized from
 * AppSearch.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppSearchConsentService {
    // Timeout for AppSearch write query in milliseconds.
    private static final int TIMEOUT = 2000;
    // This is used to convert the current package name belonging to AdExtServices to the
    // corresponding package name for AdServices.
    private static final String EXTSERVICES_PACKAGE_NAME_SUBSTRING = "ext.";
    private static final String DATABASE_NAME = "adservices_consent";

    // Required for allowing AdServices apk access to read consent written by ExtServices module.
    private String mAdservicesPackageName;
    private static final String ADSERVICES_SHA =
            "686d5c450e00ebe600f979300a29234644eade42f24ede07a073f2bc6b94a3a2";
    private Context mContext;

    private ListenableFuture<AppSearchSession> mSearchSession;

    // When reading across APKs, a GlobalSearchSession is needed, hence we use it when reading.
    private ListenableFuture<GlobalSearchSession> mGlobalSearchSession;
    private Executor mExecutor = AdServicesExecutors.getBackgroundExecutor();

    private PackageIdentifier mPackageIdentifier;

    private AppSearchConsentService(Context context) {
        mContext = context;
        mSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(mContext, DATABASE_NAME).build());
        mGlobalSearchSession =
                PlatformStorage.createGlobalSearchSessionAsync(
                        new PlatformStorage.GlobalSearchContext.Builder(mContext).build());
        mAdservicesPackageName = getAdServicesPackageName(mContext);
        mPackageIdentifier =
                new PackageIdentifier(
                        mAdservicesPackageName, new Signature(ADSERVICES_SHA).toByteArray());
    }

    /** Get an instance of AppSearchConsentService. */
    public static AppSearchConsentService getInstance(@NonNull Context context) {
        return new AppSearchConsentService(context);
    }

    /**
     * Get the consent for this user ID for this API type, as stored in AppSearch. Returns false if
     * the database doesn't exist in AppSearch.
     */
    public boolean getConsent(@NonNull String apiType) {
        return AppSearchConsentDao.readConsentData(
                mGlobalSearchSession, mExecutor, getUserIdentifierFromBinderCallingUid(), apiType);
    }

    /**
     * Sets the consent for this user ID for this API type in AppSearch. If we do not get
     * confirmation that the write was successful, then we throw an exception so that user does not
     * incorrectly think that the consent is updated.
     */
    public void setConsent(@NonNull String apiType, @NonNull Boolean consented) {
        String uid = getUserIdentifierFromBinderCallingUid();
        // The ID of the row needs to unique per row. For a given user, we store multiple rows, one
        // per each apiType.
        AppSearchConsentDao dao =
                new AppSearchConsentDao(
                        getRowId(uid, apiType),
                        uid,
                        AppSearchConsentDao.NAMESPACE,
                        apiType,
                        consented.toString());
        try {
            dao.writeConsentData(mSearchSession, mPackageIdentifier, mExecutor)
                    .get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            LogUtil.e("Failed to write consent to AppSearch ", e);
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        }
    }

    /** Returns the row ID that should be unique for the consent namespace. */
    @VisibleForTesting
    String getRowId(@NonNull String uid, @NonNull String apiType) {
        return uid + "_" + apiType;
    }

    /** Returns the User Identifier from the CallingUid. */
    @VisibleForTesting
    String getUserIdentifierFromBinderCallingUid() {
        return "" + UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
    }

    /**
     * This method returns the package name of the AdServices APK from AdServices apex (T+). On an
     * S- device, it removes the "ext." substring from the package name.
     */
    @VisibleForTesting
    static String getAdServicesPackageName(Context context) {
        Intent serviceIntent = new Intent(AdServicesCommon.ACTION_TOPICS_SERVICE);
        List<ResolveInfo> resolveInfos =
                context.getPackageManager()
                        .queryIntentServicesAsUser(
                                serviceIntent,
                                PackageManager.GET_SERVICES
                                        | PackageManager.MATCH_SYSTEM_ONLY
                                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                                UserHandle.SYSTEM);
        final ServiceInfo serviceInfo =
                AdServicesCommon.resolveAdServicesService(resolveInfos, serviceIntent.getAction());
        if (serviceInfo != null) {
            // Return the AdServices package name based on the current package name.
            String packageName = serviceInfo.packageName;
            if (packageName == null || packageName.isEmpty()) {
                throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
            }
            return packageName.replace(EXTSERVICES_PACKAGE_NAME_SUBSTRING, "");
        }
        throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }
}
