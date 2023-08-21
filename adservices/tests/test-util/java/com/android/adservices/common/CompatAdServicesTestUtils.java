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

/**
 * Class to place back-compat Adservices related helper methods.
 *
 * @deprecated tests should use {@link AdServicesFlagsSetterRule} instead.
 */
@Deprecated
public final class CompatAdServicesTestUtils {

    private static final AdServicesFlagsSetterRule sRule =
            AdServicesFlagsSetterRule.forLegacyHelpers(CompatAdServicesTestUtils.class);

    private CompatAdServicesTestUtils() {
        /* cannot be instantiated */
    }

    /**
     * Common flags that need to be set to enable back-compat and avoid invoking system server
     * related code on S- before running various PPAPI related tests.
     */
    public static void setFlags() {
        sRule.setCompatModeFlags();
    }

    /** Reset back-compat related flags to their default values after test execution. */
    public static void resetFlagsToDefault() {
        sRule.resetCompatModeFlags();
    }

    public static void setPpapiAppAllowList(String allowList) {
        sRule.setPpapiAppAllowList(allowList);
    }

    public static String getAndOverridePpapiAppAllowList(String packageName) {
        String previousAppAllowList = sRule.getPpapiAppAllowList();
        setPpapiAppAllowList(packageName); // this method takes care of the separator
        return previousAppAllowList;
    }

    public static void setMsmtApiAppAllowList(String allowList) {
        sRule.setMsmtApiAppAllowList(allowList);
    }

    public static String getAndOverrideMsmtApiAppAllowList(String packageName) {
        String previousAppAllowList = sRule.getMsmtApiAppAllowList();
        setMsmtApiAppAllowList(packageName); // this method takes care of the separator
        return previousAppAllowList;
    }
}
