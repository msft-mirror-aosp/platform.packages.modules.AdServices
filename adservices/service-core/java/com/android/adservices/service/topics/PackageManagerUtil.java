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
package com.android.adservices.service.topics;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;

import com.google.common.collect.ImmutableMap;

import java.util.Objects;
import java.util.Set;

/** Util class that query PackageManager to retrieve app information */
public class PackageManagerUtil {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();

    private final Context mContext;
    private static final String EMPTY_STRING = "";

    public PackageManagerUtil(@NonNull Context context) {
        Objects.requireNonNull(context);
        mContext = context;
    }

    /**
     * This method is used to fetch {@link AppInfo} for the set of package names.
     *
     * @param packageNames set of package names.
     * @return map with package name to its corresponding {@link AppInfo}.
     */
    @NonNull
    public ImmutableMap<String, AppInfo> getAppInformation(@NonNull Set<String> packageNames) {
        ImmutableMap.Builder<String, AppInfo> appInfoMap = ImmutableMap.builder();
        for (String name : packageNames) {
            appInfoMap.put(name, getAppInformation(name));
        }
        return appInfoMap.build();
    }

    /**
     * This method is used to get App's name and description and put it into a custom class {@link
     * AppInfo}.
     *
     * @return An instance of {@link AppInfo} that contains app's name and description information.
     */
    @NonNull
    private AppInfo getAppInformation(@NonNull String packageName) {
        Objects.requireNonNull(packageName);
        // TODO(b/228071368): Implement better error handling for NameNotFoundException
        // and null applicationInfo
        String resultAppName = EMPTY_STRING, resultAppDescription = EMPTY_STRING;
        try {
            PackageManager packageManager = mContext.getPackageManager();
            ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(
                            packageName, /* PackageManager.ApplicationInfoFlags = */ 0);
            if (applicationInfo == null) {
                sLogger.e("ApplicationInfo get from packageManager is null");
                return new AppInfo(resultAppName, resultAppDescription);
            }

            CharSequence appDescription = applicationInfo.loadDescription(packageManager);
            CharSequence appName = packageManager.getApplicationLabel(applicationInfo);

            if (appDescription == null) {
                sLogger.e("AppDescription get from applicationInfo is null");
            } else {
                resultAppDescription = appDescription.toString();
            }
            if (appName == null) {
                sLogger.e("AppName get from packageManager is null");
            } else {
                resultAppName = appName.toString();
            }
        } catch (NameNotFoundException e) {
            sLogger.e("NameNotFoundException thrown when fetching AppDescription");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
        return new AppInfo(resultAppName, resultAppDescription);
    }
}
