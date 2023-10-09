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

import static com.android.adservices.common.DeviceSideDeviceConfigHelper.callWithDeviceConfigPermissions;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST;

import android.os.Build;

import com.android.adservices.experimental.AbstractFlagsRouletteRunner;
import com.android.adservices.experimental.AbstractFlagsRouletteRunner.FlagsRouletteState;
import com.android.adservices.service.Flags;
import com.android.adservices.service.PhFlags;
import com.android.modules.utils.build.SdkLevel;

public final class AdServicesFlagsSetterRule
        extends AbstractAdServicesFlagsSetterRule<AdServicesFlagsSetterRule> {

    // TODO(b/294423183): remove once legacy usage is gone
    private final boolean mUsedByLegacyHelper;

    private AdServicesFlagsSetterRule() {
        this(/* usedByLegacyHelper= */ false);
    }

    private AdServicesFlagsSetterRule(boolean usedByLegacyHelper) {
        super(
                AndroidLogger.getInstance(),
                namespace -> new DeviceSideDeviceConfigHelper(namespace, usedByLegacyHelper),
                DeviceSideSystemPropertiesHelper.getInstance());
        mUsedByLegacyHelper = usedByLegacyHelper;
    }

    private static AdServicesFlagsSetterRule withDefaultLogcatTags() {
        return new AdServicesFlagsSetterRule().setDefaultLogcatTags();
    }

    /** Factory method that only disables the global kill switch. */
    public static AdServicesFlagsSetterRule forGlobalKillSwitchDisabledTests() {
        return withDefaultLogcatTags().setGlobalKillSwitch(false);
    }

    /** Factory method for Topics end-to-end CTS tests. */
    public static AdServicesFlagsSetterRule forTopicsE2ETests() {
        return forGlobalKillSwitchDisabledTests()
                .setLogcatTag(LOGCAT_TAG_TOPICS, LOGCAT_LEVEL_VERBOSE)
                .setTopicsKillSwitch(false)
                .setTopicsOnDeviceClassifierKillSwitch(false)
                .setTopicsClassifierForceUseBundleFiles(true)
                .setDisableTopicsEnrollmentCheckForTests(true)
                .setEnableEnrollmentTestSeed(true)
                .setConsentManagerDebugMode(true)
                .setCompatModeFlags();
    }

    /** Factory method for AdId end-to-end CTS tests. */
    public static AdServicesFlagsSetterRule forAdidE2ETests(String packageName) {
        return forGlobalKillSwitchDisabledTests()
                .setAdIdKillSwitchForTests(false)
                .setAdIdRequestPermitsPerSecond(25.0)
                .setPpapiAppAllowList(packageName)
                .setCompatModeFlag();
    }

    /** Factory method for Measurement E2E CTS tests */
    public static AdServicesFlagsSetterRule forMeasurementE2ETests(String packageName) {
        return forGlobalKillSwitchDisabledTests()
                .setCompatModeFlags()
                .setMsmtApiAppAllowList(packageName)
                .setMsmtWebContextClientAllowList(packageName)
                .setConsentManagerDebugMode(true)
                .setOrCacheDebugSystemProperty(KEY_GLOBAL_KILL_SWITCH, false)
                .setOrCacheDebugSystemProperty(KEY_MEASUREMENT_KILL_SWITCH, false)
                .setOrCacheDebugSystemProperty(
                        KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH, false)
                .setOrCacheDebugSystemProperty(
                        KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH, false)
                .setOrCacheDebugSystemProperty(
                        KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH, false)
                .setOrCacheDebugSystemProperty(
                        KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH, false)
                .setOrCacheDebugSystemProperty(
                        KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH, false)
                .setOrCacheDebugSystemProperty(KEY_MEASUREMENT_API_STATUS_KILL_SWITCH, false)
                .setAdIdKillSwitchForTests(false);
    }

    /** Factory method for AdservicesCommonManager end-to-end CTS tests. */
    public static AdServicesFlagsSetterRule forCommonManagerE2ETests(String packageName) {
        return withDefaultLogcatTags().setCompatModeFlag().setPpapiAppAllowList(packageName);
    }

    /**
     * @deprecated temporary method used only by {@code CompatAdServicesTestUtils} and similar
     *     helpers, it will be remove once such helpers are replaced by this rule.
     */
    @Deprecated
    static AdServicesFlagsSetterRule forLegacyHelpers(Class<?> helperClass) {
        AdServicesFlagsSetterRule rule =
                new AdServicesFlagsSetterRule(/* usedByLegacyHelper= */ true);
        String testName = helperClass.getSimpleName();
        // This object won't be used as a JUnit rule, so we need to explicitly
        // initialize it
        rule.setInitialSystemProperties(testName);
        rule.setInitialFlags(testName);
        return rule;
    }

    // NOTE: add more factory methods as needed

    @Override
    protected int getDeviceSdk() {
        return Build.VERSION.SDK_INT;
    }

    @Override
    protected boolean isAtLeastS() {
        return SdkLevel.isAtLeastS();
    }

    @Override
    protected boolean isAtLeastT() {
        return SdkLevel.isAtLeastT();
    }

    @Override
    protected boolean isFlagManagedByRunner(String flag) {
        FlagsRouletteState roulette = AbstractFlagsRouletteRunner.getFlagsRouletteState();
        if (roulette == null || !roulette.flagNames.contains(flag)) {
            return false;
        }
        mLog.w(
                "Not setting flag %s as it's managed by %s (which manages %s)",
                flag, roulette.runnerName, roulette.flagNames);
        return true;
    }

    // NOTE: currently only used by device-side tests, so it's added directly here in order to use
    // the logic defined by PhFlags - if needed by hostside, we'll have to move it up and
    // re-implement that logic there.
    /** Calls {@link PhFlags#getAdIdRequestPerSecond()} with the proper permissions. */
    public float getAdIdRequestPerSecond() {
        try {
            return callWithDeviceConfigPermissions(
                    () -> PhFlags.getInstance().getAdIdRequestPermitsPerSecond());
        } catch (Throwable t) {
            float defaultValue = Flags.ADID_REQUEST_PERMITS_PER_SECOND;
            mLog.e(
                    t,
                    "FlagsConstants.getAdIdRequestPermitsPerSecond() failed, returning default"
                            + " value (%f)",
                    defaultValue);
            return defaultValue;
        }
    }

    /**
     * @deprecated only used by {@code CompatAdServicesTestUtils}
     */
    @Deprecated
    String getPpapiAppAllowList() {
        assertCalledByLegacyHelper();
        return mDeviceConfig.get(KEY_PPAPI_APP_ALLOW_LIST);
    }

    @Override
    protected boolean isCalledByLegacyHelper() {
        return mUsedByLegacyHelper;
    }
}
