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

package android.adservices.test.scenario.adservices.utils;

import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

/** Utils for back-compat CB tests. */
public final class CompatTestUtils {
    private static final int PPAPI_ONLY_SOURCE_OF_TRUTH = 1;
    private static final int PPAPI_AND_SYSTEM_SERVER_SOURCE_OF_TRUTH = 2;
    private static final String ADSERVICES_PACKAGE = "com.google.android.adservices.api";
    private static final String COMPAT_ADSERVICES_PACKAGE = "com.google.android.ext.adservices.api";

    private CompatTestUtils() {
        /* cannot be instantiated */
    }

    /**
     * Flags that need to be set to avoid invoking system server related code on S- before CB tests.
     */
    public static void setFlags() {
        setBlockedTopicsSourceOfTruth(PPAPI_ONLY_SOURCE_OF_TRUTH);
        setConsentSourceOfTruth(PPAPI_ONLY_SOURCE_OF_TRUTH);
        // Measurement rollback check requires loading AdServicesManagerService's Binder from the
        // SdkSandboxManager via getSystemService() which is not supported on S-. By disabling
        // measurement rollback, we omit invoking that code.
        disableMeasurementRollbackDelete();
    }

    /** Reset system-server related flags to their default values after CB tests. */
    public static void resetFlagsToDefault() {
        setBlockedTopicsSourceOfTruth(PPAPI_AND_SYSTEM_SERVER_SOURCE_OF_TRUTH);
        setConsentSourceOfTruth(PPAPI_AND_SYSTEM_SERVER_SOURCE_OF_TRUTH);
        enableMeasurementRollbackDelete();
    }

    /**
     * AdServices on S- is accessible via ExtServices apex with a different package name compared to
     * T+. Returns appropriate package name based on SDK level.
     */
    public static String getAdServicesPackageName() {
        return SdkLevel.isAtLeastT() ? ADSERVICES_PACKAGE : COMPAT_ADSERVICES_PACKAGE;
    }

    private static void setConsentSourceOfTruth(int source) {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_source_of_truth " + source);
    }

    private static void setBlockedTopicsSourceOfTruth(int source) {
        ShellUtils.runShellCommand(
                "device_config put adservices blocked_topics_source_of_truth " + source);
    }

    private static void disableMeasurementRollbackDelete() {
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_rollback_deletion_kill_switch true");
    }

    private static void enableMeasurementRollbackDelete() {
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_rollback_deletion_kill_switch false");
    }
}
