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

package com.android.adservices.common;

import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

/** Class to place back-compat Adservices related helper methods */
public class CompatAdServicesTestUtils {
    private static final int PPAPI_ONLY = 1;
    private static final int PPAPI_AND_SYSTEM_SERVER_SOURCE_OF_TRUTH = 2;
    private static final int APPSEARCH_ONLY = 3;

    private CompatAdServicesTestUtils() {
        /* cannot be instantiated */
    }

    /**
     * Common flags that need to be set to enable back-compat and avoid invoking system server
     * related code on S- before running various PPAPI related tests.
     */
    public static void setFlags() {
        if (SdkLevel.isAtLeastT()) {
            // Do nothing; this method is intended to set flags for Android S- only.
            return;
        } else if (SdkLevel.isAtLeastS()) {
            setEnableBackCompatFlag(true);
            setBlockedTopicsSourceOfTruth(APPSEARCH_ONLY);
            setConsentSourceOfTruth(APPSEARCH_ONLY);
            setEnableAppSearchConsentData(true);
            setEnableMeasurementRollbackAppSearchKillSwitch(false);
        } else {
            setEnableBackCompatFlag(true);
            // TODO (b/285208753): Update flags once AppSearch is supported on R.
            setBlockedTopicsSourceOfTruth(PPAPI_ONLY);
            setConsentSourceOfTruth(PPAPI_ONLY);
            setEnableAppSearchConsentData(false);
            setEnableMeasurementRollbackAppSearchKillSwitch(true);
        }
    }

    /** Reset back-compat related flags to their default values after test execution. */
    public static void resetFlagsToDefault() {
        if (SdkLevel.isAtLeastT()) {
            // Do nothing; this method is intended to set flags for Android S- only.
            return;
        }
        setEnableBackCompatFlag(false);
        // TODO (b/285208753): Set to AppSearch always once it's supported on R.
        setBlockedTopicsSourceOfTruth(SdkLevel.isAtLeastS() ? APPSEARCH_ONLY : PPAPI_ONLY);
        setConsentSourceOfTruth(SdkLevel.isAtLeastS() ? APPSEARCH_ONLY : PPAPI_ONLY);
        setEnableAppSearchConsentData(SdkLevel.isAtLeastS());
        setEnableMeasurementRollbackAppSearchKillSwitch(!SdkLevel.isAtLeastS());
    }

    public static void setPpapiAppAllowList(String allowList) {
        ShellUtils.runShellCommand(
                "device_config put adservices ppapi_app_allow_list " + allowList);
    }

    public static String getAndOverridePpapiAppAllowList(String packageName) {
        String mPreviousAppAllowList =
                ShellUtils.runShellCommand("device_config get adservices ppapi_app_allow_list");
        setPpapiAppAllowList(mPreviousAppAllowList + "," + packageName);
        return mPreviousAppAllowList;
    }

    public static void setMsmtApiAppAllowList(String allowList) {
        ShellUtils.runShellCommand(
                "device_config put adservices msmt_api_app_allow_list " + allowList);
    }

    public static String getAndOverrideMsmtApiAppAllowList(String packageName) {
        String mPreviousAppAllowList =
                ShellUtils.runShellCommand("device_config get adservices msmt_api_app_allow_list");
        setMsmtApiAppAllowList(mPreviousAppAllowList + "," + packageName);
        return mPreviousAppAllowList;
    }

    private static void setEnableBackCompatFlag(boolean isEnabled) {
        ShellUtils.runShellCommand("device_config put adservices enable_back_compat " + isEnabled);
    }

    private static void setConsentSourceOfTruth(int source) {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_source_of_truth " + source);
    }

    private static void setBlockedTopicsSourceOfTruth(int source) {
        ShellUtils.runShellCommand(
                "device_config put adservices blocked_topics_source_of_truth " + source);
    }

    private static void setEnableAppSearchConsentData(boolean isEnabled) {
        ShellUtils.runShellCommand(
                "device_config put adservices enable_appsearch_consent_data " + isEnabled);
    }

    private static void setEnableMeasurementRollbackAppSearchKillSwitch(boolean isEnabled) {
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_rollback_deletion_app_search_kill_switch "
                        + isEnabled);
    }
}
