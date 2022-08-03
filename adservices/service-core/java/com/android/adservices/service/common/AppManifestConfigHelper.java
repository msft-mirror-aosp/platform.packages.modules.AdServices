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

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import com.android.adservices.LogUtil;
import com.android.adservices.service.exception.XmlParseException;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** Helper class for parsing and checking the app manifest config (<ad-services-config>). */
// TODO(b/213488783): Add persistence, so that lookup/parse is not on every request.
// Also consider if this should execute in the background.
public class AppManifestConfigHelper {
    public static final String AD_SERVICES_CONFIG_PROPERTY =
            "android.adservices.AD_SERVICES_CONFIG";

    private static XmlResourceParser getXmlParser(
            @NonNull Context context, @NonNull String appPackageName)
            throws PackageManager.NameNotFoundException, XmlParseException {
        PackageManager pm = context.getPackageManager();
        if (pm.getProperty(AD_SERVICES_CONFIG_PROPERTY, appPackageName) == null) {
            throw new XmlParseException("Property not found");
        }
        int resId = pm.getProperty(AD_SERVICES_CONFIG_PROPERTY, appPackageName).getResourceId();
        Resources resources = pm.getResourcesForApplication(appPackageName);
        return resources.getXml(resId);
    }

    /**
     * Parses the app's manifest config to determine whether this sdk is permitted to use the
     * Attribution API.
     *
     * <p>If there is a parse error, it returns false.
     *
     * @param context the context for the API call. This needs to be the context where the calling
     *     UID is that of the API caller.
     * @param appPackageName the package name of the app whose manifest config will be read.
     * @param sdk the name of the sdk that will be checked against app's manifest config.
     */
    // TODO(b/237444140): Update for adtech enrollment.
    public static boolean isAllowedAttributionAccess(
            @NonNull Context context, @NonNull String appPackageName, @NonNull String sdk) {
        try {
            XmlResourceParser in = getXmlParser(context, appPackageName);
            AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(in);
            return appManifestConfig.isAllowedAttributionAccess(sdk);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(e, "App manifest parse failed: NameNotFound.");
        } catch (XmlParseException | XmlPullParserException | IOException e) {
            LogUtil.e(e, "App manifest parse failed.");
        }
        return false;
    }

    /**
     * Parses the app's manifest config to determine whether this sdk is permitted to use the Custom
     * Audiences API.
     *
     * <p>If there is a parse error, it returns false.
     *
     * @param context the context for the API call. This needs to be the context where the calling
     *     UID is that of the API caller.
     * @param appPackageName the package name of the app whose manifest config will be read.
     * @param enrollmentId the enrollment id associate with the ad tech identifier.
     */
    public static boolean isAllowedCustomAudiencesAccess(
            @NonNull Context context,
            @NonNull String appPackageName,
            @NonNull String enrollmentId) {
        try {
            XmlResourceParser in = getXmlParser(context, appPackageName);
            AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(in);
            return appManifestConfig.isAllowedCustomAudiencesAccess(enrollmentId);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(e, "App manifest parse failed: NameNotFound.");
        } catch (XmlParseException | XmlPullParserException | IOException e) {
            LogUtil.e(e, "App manifest parse failed.");
        }
        return false;
    }

    /**
     * Parses the app's manifest config to determine whether this sdk is permitted to use the Topics
     * API.
     *
     * <p>If there is a parse error, it returns false.
     *
     * @param context the context for the API call. This needs to be the context where the calling
     *     UID is that of the API caller.
     * @param appPackageName the package name of the app whose manifest config will be read.
     * @param sdk the name of the sdk that will be checked against app's manifest config. // TODO:
     *     Update for adtech enrollment.
     */
    public static boolean isAllowedTopicsAccess(
            @NonNull Context context, @NonNull String appPackageName, @NonNull String sdk) {
        try {
            XmlResourceParser in = getXmlParser(context, appPackageName);
            AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(in);
            return appManifestConfig.isAllowedTopicsAccess(sdk);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(e, "App manifest parse failed: NameNotFound.");
        } catch (XmlParseException | XmlPullParserException | IOException e) {
            LogUtil.e(e, "App manifest parse failed.");
        }
        return false;
    }
}
