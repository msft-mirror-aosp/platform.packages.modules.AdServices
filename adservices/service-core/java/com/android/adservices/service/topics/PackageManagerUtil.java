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

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import com.android.adservices.LogUtil;

import java.util.Objects;

/**
 * Util class that query PackageManager to retrieve app information
 */
public class PackageManagerUtil {

    private final Context mContext;
    private final String mPackageName;
    private static final String EMPTY_STRING = "";

    public PackageManagerUtil(@NonNull Context context, @NonNull String packageName) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(context);
        mContext = context;
        mPackageName = packageName;
    }

    /**
    * This method is used to get App's name and description
    * and put it into a custom class AppInfo
    * @return An instance of AppInfo that contains app's name and description information
    */
    @NonNull
    public AppInfo getAppInformation() {
        // TODO(b/228071368): Implement better error handling for NameNotFoundException
        // and null applicationInfo
        String resultAppName = EMPTY_STRING, resultAppDescription = EMPTY_STRING;
        try {
            PackageManager packageManager = mContext.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(mPackageName,
                /* PackageManager.ApplicationInfoFlags = */ 0);
            if (applicationInfo == null) {
                LogUtil.e("ApplicationInfo get from packageManager is null");
                return new AppInfo(resultAppName, resultAppDescription);
            }

            CharSequence appDescription = applicationInfo.loadDescription(packageManager);
            CharSequence appName = packageManager.getApplicationLabel(applicationInfo);

            if (appDescription == null) {
                LogUtil.e("AppDescription get from applicationInfo is null");
            } else {
                resultAppDescription = appDescription.toString();
            }
            if (appName == null) {
                LogUtil.e("AppName get from packageManager is null");
            } else {
                resultAppName = appName.toString();
            }
        } catch (NameNotFoundException e) {
            LogUtil.e("NameNotFoundException thrown when fetching AppDescription");
        }
        return new AppInfo(resultAppName, resultAppDescription);
    }
}
