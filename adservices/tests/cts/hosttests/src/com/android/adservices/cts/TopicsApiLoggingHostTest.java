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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_KILL_SWITCH;
import static com.android.os.adservices.AdservicesExtensionAtoms.AD_SERVICES_API_CALLED_FIELD_NUMBER;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.shared.testing.TestDeviceHelper;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.adservices.AdServicesApiCalled;
import com.android.os.adservices.AdservicesExtensionAtoms;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@SetFlagDisabled(KEY_TOPICS_KILL_SWITCH)
@SetFlagDisabled(KEY_MDD_BACKGROUND_TASK_KILL_SWITCH)
@SetFlagDisabled(KEY_ENFORCE_FOREGROUND_STATUS_TOPICS)
@SetFlagEnabled(KEY_ADSERVICES_ENABLED)
@SetFlagEnabled(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK)
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
public final class TopicsApiLoggingHostTest extends AdServicesHostSideTestCase {
    private static final String PACKAGE = "com.android.adservices.cts";
    private static final String CLASS = "TopicsApiLogActivity";
    private static final String CLASS_NAME = "TARGETING";
    private static final String API_NAME = "GET_TOPICS";
    private static final String SDK_NAME = "AdServicesCtsSdk";
    private static final String TARGET_PACKAGE_SUFFIX_TPLUS = "android.adservices.api";
    private static final String TARGET_PACKAGE_SUFFIX_SMINUS = "android.ext.services";

    @Rule(order = 10)
    public TestMetrics mMetrics = new TestMetrics();

    private String mTargetPackage;

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        // Set flags for test to run on devices with api level lower than 33 (S-)
        String suffix =
                sdkLevel.isAtLeastT() ? TARGET_PACKAGE_SUFFIX_TPLUS : TARGET_PACKAGE_SUFFIX_SMINUS;
        mTargetPackage = findPackageName(suffix);
        assertThat(mTargetPackage).isNotNull();
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Ignore("b/374761838")
    @Test
    public void testGetTopicsLog() throws Exception {
        ITestDevice device = getDevice();
        assertWithMessage("Device not set").that(device).isNotNull();

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdservicesExtensionAtoms.registerAllExtensions(registry);

        callTopicsAPI(mTargetPackage, device);

        // Fetch a list of happened log events and their data
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(device, registry);

        // We trigger only one event from activity, should only see one event in the list
        assertThat(data).hasSize(1);

        // Verify the log event data
        AdServicesApiCalled adServicesApiCalled =
                data.get(0).getAtom().getExtension(AdservicesExtensionAtoms.adServicesApiCalled);
        assertThat(adServicesApiCalled.getSdkPackageName()).isEqualTo(SDK_NAME);
        assertThat(adServicesApiCalled.getAppPackageName()).isEqualTo(PACKAGE);
        assertThat(adServicesApiCalled.getApiClass().toString()).isEqualTo(CLASS_NAME);
        assertThat(adServicesApiCalled.getApiName().toString()).isEqualTo(API_NAME);
    }

    private void callTopicsAPI(String apiName, ITestDevice device) throws Exception {
        // Upload the config.
        final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(apiName);

        ConfigUtils.addEventMetric(config, AD_SERVICES_API_CALLED_FIELD_NUMBER);
        ConfigUtils.uploadConfig(device, config);

        // Run the get topic activity that has logging event on the devices
        TestDeviceHelper.setTestDevice(device);
        TestDeviceHelper.startActivityWaitUntilCompletion(PACKAGE, CLASS);

        // Wait for the logging event to happen.
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    private String findPackageName(String suffix) throws DeviceNotAvailableException {
        return mDevice.getInstalledPackageNames().stream()
                .filter(s -> s.endsWith(suffix))
                .findFirst()
                .orElse(null);
    }
}
