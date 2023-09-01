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

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.common.AdServicesHostSideFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.HostSideSdkLevelSupportRule;
import com.android.adservices.common.RequiresSdkLevelLessThanT;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.device.BackgroundDeviceAction;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

/** Test to check if com.google.android.ext.services failed to mount */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AdExtServicesFailedToMountHostTest extends AdServicesHostSideTestCase {

    private static String sWildcardString = ".*";
    private static String sExtservicesString = "com\\.google\\.android\\.ext\\.services";
    private static String sFailedToMountString = "Failed to mount";
    private static String sNoSuchString = "No such file or directory";

    @Rule(order = 0)
    public final HostSideSdkLevelSupportRule sdkLevel = HostSideSdkLevelSupportRule.forAnyLevel();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 1)
    public final AdServicesHostSideFlagsSetterRule flags =
            AdServicesHostSideFlagsSetterRule.forCompatModeEnabledTests();

    @Test
    @RequiresSdkLevelLessThanT(reason = "Test is for ExtServices only")
    public void testLogcatDoesNotContainError() throws Exception {
        // reboot the device
        mDevice.reboot();
        mDevice.waitForDeviceAvailable();

        AdservicesLogcatReceiver logcatReceiver =
                new AdservicesLogcatReceiver("receiver", " logcat");
        logcatReceiver.start(mDevice);

        // Sleep 5 min to allow time for the error to occur
        Thread.sleep(300 * 1000);
        logcatReceiver.stop();

        String regex =
                sExtservicesString
                        + sWildcardString
                        + sFailedToMountString
                        + sWildcardString
                        + sNoSuchString;
        Pattern errorPattern = Pattern.compile(regex);
        assertWithMessage("logcat matches regex (%s)", regex)
                .that(logcatReceiver.patternMatches(errorPattern))
                .isFalse();
    }

    // TODO: b/288892905 consolidate with existing logcat receiver
    private static class AdservicesLogcatReceiver extends MultiLineReceiver {
        private volatile boolean mCancelled = false;
        private final StringBuilder mBuilder = new StringBuilder();
        private final String mName;
        private final String mLogcatCmd;
        private BackgroundDeviceAction mBackgroundDeviceAction;

        AdservicesLogcatReceiver(String name, String logcatCmd) {
            this.mName = name;
            this.mLogcatCmd = logcatCmd;
        }

        @Override
        public void processNewLines(String[] lines) {
            if (lines.length == 0) {
                return;
            }
            mBuilder.append(String.join("\n", lines));
        }

        @Override
        public boolean isCancelled() {
            return mCancelled;
        }

        public void start(ITestDevice device) {
            mBackgroundDeviceAction =
                    new BackgroundDeviceAction(mLogcatCmd, mName, device, this, 0);
            mBackgroundDeviceAction.start();
        }

        public void stop() {
            if (mBackgroundDeviceAction != null) mBackgroundDeviceAction.cancel();
            if (isCancelled()) return;
            mCancelled = true;
        }

        public boolean patternMatches(Pattern pattern) {
            return mBuilder.length() > 0 && pattern.matcher(mBuilder).find();
        }
    }
}
