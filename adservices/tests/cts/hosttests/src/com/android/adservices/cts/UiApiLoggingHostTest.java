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

package com.android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.adservices.common.AdServicesHostSideDeviceSupportedRule;
import com.android.adservices.common.AdServicesHostSideFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.HostSideSdkLevelSupportRule;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.AdServicesSettingsUsageReported;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test to check that Ui API logging to StatsD
 *
 * <p>The activity simply called Ui Settings Page which trigger the log event, and then check it in
 * statsD.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class UiApiLoggingHostTest extends AdServicesHostSideTestCase {
    private static final String CLASS =
            "com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity";
    private static final String TARGET_PACKAGE = "com.google.android.adservices.api";
    private static final String TARGET_PACKAGE_AOSP = "com.android.adservices.api";

    private static final String TARGET_EXT_ADSERVICES_PACKAGE = "com.google.android.ext.services";
    private static final String TARGET_EXT_ADSERVICES_PACKAGE_AOSP = "com.android.ext.services";
    private String mTargetPackage;
    private String mTargetPackageAosp;

    @Rule(order = 0)
    public final HostSideSdkLevelSupportRule sdkLevel = HostSideSdkLevelSupportRule.forAnyLevel();

    @Rule(order = 1)
    public final AdServicesHostSideDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesHostSideDeviceSupportedRule();

    @Rule(order = 2)
    public final AdServicesHostSideFlagsSetterRule flags =
            AdServicesHostSideFlagsSetterRule.forCompatModeEnabledTests()
                    .setTopicsKillSwitch(false)
                    .setAdServicesEnabled(true)
                    .setMddBackgroundTaskKillSwitch(true)
                    .setConsentManagerDebugMode(true)
                    .setDisableTopicsEnrollmentCheckForTests(true);

    @Rule(order = 3)
    public TestMetrics metricsRule = new TestMetrics();

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        // Set flags for test to run on devices with api level lower than 33 (S-)
        if (!sdkLevel.isAtLeastT()) {
            mTargetPackage = TARGET_EXT_ADSERVICES_PACKAGE;
            mTargetPackageAosp = TARGET_EXT_ADSERVICES_PACKAGE_AOSP;
        } else {
            mTargetPackage = TARGET_PACKAGE;
            mTargetPackageAosp = TARGET_PACKAGE_AOSP;
        }
    }
    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    // TODO(b/245400146): Get package Name for Topics API instead of running the test twice.
    @Test
    public void testStartSettingMainActivityAndGetUiLog() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        rebootIfSMinus();
        startSettingMainActivity(mTargetPackage, device, /* isAosp */ false);

        // Fetch a list of happened log events and their data
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(device);

        // Adservice Package Name is different in aosp and non-aosp devices. Attempt again with the
        // other
        // package Name if it fails at the first time;
        if (data.isEmpty()) {
            ConfigUtils.removeConfig(getDevice());
            ReportUtils.clearReports(getDevice());
            startSettingMainActivity(mTargetPackageAosp, device, /* isAosp */ true);
            data = ReportUtils.getEventMetricDataList(device);
        }

        // We trigger only one event from activity, should only see one event in the list
        assertThat(data).hasSize(1);

        // Verify the log event data
        AtomsProto.AdServicesSettingsUsageReported adServicesSettingsUsageReported =
                data.get(0).getAtom().getAdServicesSettingsUsageReported();
        assertThat(adServicesSettingsUsageReported.getAction())
                .isEqualTo(
                        AdServicesSettingsUsageReported.AdServiceSettingsName
                                .PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED);
    }

    private void rebootIfSMinus() throws DeviceNotAvailableException, InterruptedException {
        if (!sdkLevel.isAtLeastT()) {
            ITestDevice device = getDevice();
            device.reboot();
            device.waitForDeviceAvailable();
            // Sleep 5 mins to wait for AdExtBootCompletedReceiver execution
            Thread.sleep(300 * 1000 /* ms */);
        }
    }

    private void startSettingMainActivity(String apiName, ITestDevice device, boolean isAosp)
            throws Exception {
        // Upload the config.
        final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(apiName);

        ConfigUtils.addEventMetric(config, Atom.AD_SERVICES_SETTINGS_USAGE_REPORTED_FIELD_NUMBER);
        ConfigUtils.uploadConfig(device, config);
        // Start the ui main activity, it will make a ui log call
        startUiMainActivity(device, isAosp);
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
    }

    public void startUiMainActivity(ITestDevice device, boolean isAosp)
            throws DeviceNotAvailableException {
        String packageName = isAosp ? mTargetPackageAosp : mTargetPackage;
        device.executeShellCommand("am start -n " + packageName + "/" + CLASS);
    }
}
