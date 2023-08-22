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
import static com.android.adservices.service.FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST;

import android.os.Build;

import com.android.adservices.experimental.AbstractFlagsRouletteRunner;
import com.android.adservices.experimental.AbstractFlagsRouletteRunner.FlagsRouletteState;
import com.android.adservices.service.Flags;
import com.android.adservices.service.PhFlags;
import com.android.modules.utils.build.SdkLevel;

// TODO(b/295321663): rename to AdServicesFlagsSetterRule - it was temporary renamed to
// DeviceSideAdServicesFlagsSetterRule to minimize git diff in the superclass
public final class DeviceSideAdServicesFlagsSetterRule
        extends AbstractAdServicesFlagsSetterRule<DeviceSideAdServicesFlagsSetterRule> {

    // TODO(b/294423183): remove once legacy usage is gone
    private final boolean mUsedByLegacyHelper;

    private DeviceSideAdServicesFlagsSetterRule() {
        this(/* usedByLegacyHelper= */ false);
    }

    private DeviceSideAdServicesFlagsSetterRule(boolean usedByLegacyHelper) {
        super(
                AndroidLogger.getInstance(),
                namespace -> new DeviceSideDeviceConfigHelper(namespace),
                new DeviceSideSystemPropertiesHelper());
        mUsedByLegacyHelper = usedByLegacyHelper;
    }

    /** Factory method that only disables the global kill switch. */
    public static DeviceSideAdServicesFlagsSetterRule forGlobalKillSwitchDisabledTests() {
        return newInstance(
                new DeviceSideAdServicesFlagsSetterRule(), rule -> rule.setGlobalKillSwitch(false));
    }

    /** Factory method for Topics end-to-end CTS tests. */
    public static DeviceSideAdServicesFlagsSetterRule forTopicsE2ETests() {
        return newInstance(
                forGlobalKillSwitchDisabledTests(),
                rule ->
                        rule.setTopicsKillSwitch(false)
                                .setTopicsOnDeviceClassifierKillSwitch(false)
                                .setTopicsClassifierForceUseBundleFiles(true)
                                .setDisableTopicsEnrollmentCheckForTests(true)
                                .setEnableEnrollmentTestSeed(true)
                                .setConsentManagerDebugMode(true)
                                .setCompatModeFlags());
    }

    /** Factory method for AdId end-to-end CTS tests. */
    public static DeviceSideAdServicesFlagsSetterRule forAdidE2ETests(String packageName) {
        return newInstance(
                forGlobalKillSwitchDisabledTests(),
                rule ->
                        rule.setAdIdKillSwitchForTests(false)
                                .setAdIdRequestPermitsPerSecond(25.0)
                                .setPpapiAppAllowList(packageName)
                                .setCompatModeFlag());
    }

    /**
     * @deprecated temporary method used only by {@code CompatAdServicesTestUtils} and similar
     *     helpers, it will be remove once such helpers are replaced by this rule.
     */
    @Deprecated
    static DeviceSideAdServicesFlagsSetterRule forLegacyHelpers(Class<?> helperClass) {
        return newInstance(
                new DeviceSideAdServicesFlagsSetterRule(/* usedByLegacyHelper= */ true),
                rule -> {
                    // This object won't be used as a JUnit rule, so we need to explicitly
                    // initialize it
                    String testName = helperClass.getSimpleName();
                    rule.setInitialSystemProperties(testName);
                    rule.setInitialFlags(testName);
                });
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
    public float getAdIdRequestPerSecond() throws Exception {
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
    String getPpapiAppAllowList() throws Exception {
        assertCalledByLegacyHelper();
        return mDeviceConfig.get(KEY_PPAPI_APP_ALLOW_LIST);
    }

    /**
     * @deprecated only used by {@code CompatAdServicesTestUtils}
     */
    @Deprecated
    String getMsmtApiAppAllowList() throws Exception {
        assertCalledByLegacyHelper();
        return mDeviceConfig.get(KEY_MSMT_API_APP_ALLOW_LIST);
    }

    @Override
    protected void assertCalledByLegacyHelper() {
        if (!mUsedByLegacyHelper) {
            throw new UnsupportedOperationException("Only available for legacy helpers");
        }
    }
}
