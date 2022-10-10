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

package com.android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.AdServicesApiCalled;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test to check that Topics API logging to StatsD
 *
 * <p>When this test builds, it also builds {@link com.android.adservices.cts.TopicsApiLogActivity}
 * into an APK which it then installed at runtime and started. The activity simply called getTopics
 * service which trigger the log event, and then gets uninstalled.
 *
 * <p>Instead of extending DeviceTestCase, this JUnit4 test extends IDeviceTest and is run with
 * tradefed's DeviceJUnit4ClassRunner
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TopicsApiLoggingHostTest implements IDeviceTest {

    private static final String PACKAGE = "com.android.adservices.cts";
    private static final String CLASS = "TopicsApiLogActivity";
    private static final String TARGET_PACKAGE = "com.google.android.adservices.api";
    private static final String TARGET_PACKAGE_AOSP = "com.android.adservices.api";
    private static final String SDK_NAME = "AdservicesCtsSdk";

    @Rule public TestMetrics mMetrics = new TestMetrics();

    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        // We need to turn the Consent Manager into debug mode
        overrideConsentManagerDebugMode();
        disableMddBackgroundTasks(true);
        overrideDisableTopicsEnrollmentCheck("1");
        overrideAdservicesGlobalKillSwitch(true);
    }

    @After
    public void tearDown() throws Exception {
        overrideAdservicesGlobalKillSwitch(false);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    // TODO(b/245400146): Get package Name for Topics API instead of running the test twice.
    @Test
    public void testGetTopicsLog() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        callTopicsAPI(TARGET_PACKAGE, device);

        // Fetch a list of happened log events and their data
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(device);

        // Topics API Name is different in aosp and non-aosp devices. Attempt again with the other
        // package Name if it fails at the first time;
        if (data.isEmpty()) {
            ConfigUtils.removeConfig(getDevice());
            ReportUtils.clearReports(getDevice());
            callTopicsAPI(TARGET_PACKAGE_AOSP, device);
            data = ReportUtils.getEventMetricDataList(device);
        }

        // We trigger only one event from activity, should only see one event in the list
        assertThat(data).hasSize(1);

        // Verify the log event data
        AdServicesApiCalled adServicesApiCalled = data.get(0).getAtom().getAdServicesApiCalled();
        assertThat(adServicesApiCalled.getSdkPackageName()).isEqualTo(SDK_NAME);
        assertThat(adServicesApiCalled.getAppPackageName()).isEqualTo(PACKAGE);
        assertThat(adServicesApiCalled.getApiClass())
                .isEqualTo(AdServicesApiCalled.AdServicesApiClassType.TARGETING);
        assertThat(adServicesApiCalled.getApiName())
                .isEqualTo(AdServicesApiCalled.AdServicesApiName.GET_TOPICS);
    }

    private void callTopicsAPI(String apiName, ITestDevice device) throws Exception {
        // Upload the config.
        final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(apiName);

        ConfigUtils.addEventMetric(config, Atom.AD_SERVICES_API_CALLED_FIELD_NUMBER);
        ConfigUtils.uploadConfig(device, config);

        // Run the get topic activity that has logging event on the devices
        // 4th argument is actionKey and 5th is actionValue, which is the extra data that passed
        // to the activity via an Intent, we don't need to provide extra values, thus passing
        // in null here
        DeviceUtils.runActivity(
                device, PACKAGE, CLASS, /* actionKey */ null, /* actionValue */ null);

        // Wait for activity to finish and logging event to happen
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    // Switch on/off for MDD service. Default value is false, which means MDD is enabled.
    private void disableMddBackgroundTasks(boolean isSwitchedOff)
            throws DeviceNotAvailableException {
        getDevice()
                .executeShellCommand(
                        "setprop debug.adservices.mdd_background_task_kill_switch "
                                + isSwitchedOff);
    }

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val)
            throws DeviceNotAvailableException {
        // Setting it to 1 here disables the Topics' enrollment check.
        getDevice()
                .executeShellCommand(
                        "setprop debug.adservices.disable_topics_enrollment_check " + val);
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    // If isOverride = true, override global_kill_switch to OFF to allow adservices
    // If isOverride = false, override global_kill_switch to meaningless value so that PhFlags will
    // use the default value.
    private void overrideAdservicesGlobalKillSwitch(boolean isOverride)
            throws DeviceNotAvailableException {
        String overrideString = isOverride ? "false" : "null";
        getDevice()
                .executeShellCommand(
                        "setprop debug.adservices.global_kill_switch " + overrideString);
    }
}
