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

import static com.android.adservices.common.TestDeviceHelper.ADSERVICES_SETTINGS_INTENT;
import static com.android.adservices.common.TestDeviceHelper.startActivity;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.RequiresSdkLevelLessThanT;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to check that ExtServices activities are enabled by AdExtBootCompletedReceiver on S
 *
 * <p>ExtServices activities are disabled by default so that there are no duplicate activities on T+
 * devices. AdExtBootCompletedReceiver handles the BootCompleted initialization and changes
 * activities to enabled on Android S devices
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AdExtServicesBootCompleteReceiverHostTest extends AdServicesHostSideTestCase {

    @Rule(order = 0)
    public SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forCompatModeEnabledTests();

    // TODO(b/295269584): improve rule to support range of versions.
    @Test
    @RequiresSdkLevelLessThanT(reason = "It's for S only")
    public void testExtBootCompleteReceiver() throws Exception {
        // reboot the device
        mDevice.reboot();
        mDevice.waitForDeviceAvailable();
        // Sleep 5 mins to wait for AdBootCompletedReceiver execution
        Thread.sleep(300 * 1000);

        startActivity(ADSERVICES_SETTINGS_INTENT);
    }
}
