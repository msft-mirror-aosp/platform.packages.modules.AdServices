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

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.shared.testing.AndroidSdk.PRE_T;
import static com.android.adservices.shared.testing.TestDeviceHelper.startActivity;

import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.shared.testing.BackgroundLogReceiver;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Test to check that ExtServices activities are enabled by AdExtBootCompletedReceiver on S
 *
 * <p>ExtServices activities are disabled by default so that there are no duplicate activities on T+
 * devices. AdExtBootCompletedReceiver handles the BootCompleted initialization and changes
 * activities to enabled on Android S devices
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@RequiresSdkRange(atMost = PRE_T, reason = "It's for S only")
@SetFlagEnabled(KEY_ADSERVICES_ENABLED)
public final class AdExtServicesBootCompleteReceiverHostTest extends AdServicesHostSideTestCase {
    private static final String LOGCAT_COMMAND = "logcat -s adservices";

    private static final String LOG_FROM_BOOTCOMPLETE_RECEIVER =
            "AdExtBootCompletedReceiver onReceive invoked";
    private static final String ADSERVICES_SETTINGS_INTENT = "android.adservices.ui.SETTINGS";

    @Test
    public void testExtBootCompleteReceiver() throws Exception {
        // reboot the device
        mDevice.reboot();
        mDevice.waitForDeviceAvailable();

        // Start log collection, keep going until the boot complete receiver runs or times out.
        BackgroundLogReceiver logcatReceiver =
                new BackgroundLogReceiver.Builder()
                        .setDevice(mDevice)
                        .setLogCatCommand(LOGCAT_COMMAND)
                        .setEarlyStopCondition(stopIfBootCompleteReceiverLogOccurs())
                        .build();

        // Wait for up to 5 minutes for AdBootCompletedReceiver execution
        logcatReceiver.collectLogs(/* timeoutMilliseconds= */ 5 * 60 * 1000);

        // The log receiver will block until the log line occurs. The log line happens at the start
        // of the receiver execution, so give it a few more seconds to complete execution.
        TimeUnit.SECONDS.sleep(/* timeout= */ 2);

        // Try to launch the settings intent, and check for failure.
        startActivity(ADSERVICES_SETTINGS_INTENT);
    }

    private Predicate<String[]> stopIfBootCompleteReceiverLogOccurs() {
        return (s) -> Arrays.stream(s).anyMatch(t -> t.contains(LOG_FROM_BOOTCOMPLETE_RECEIVER));
    }
}
