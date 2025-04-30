/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.sdksandbox.test.scenario.labclient;

import android.os.Bundle;
import android.platform.test.scenario.annotation.Scenario;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@Scenario
@RunWith(JUnit4.class)
public class OpenClientApp {
    private static UiDevice sUiDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private static final int WAIT_TIME_BEFORE_END_TEST_MS = 3000;
    private static final String CLIENT_APP_PACKAGE_NAME_KEY = "client-app-package-name";

    private static final Bundle sArgsBundle = InstrumentationRegistry.getArguments();

    protected String mClientAppPackageName = sArgsBundle.getString(CLIENT_APP_PACKAGE_NAME_KEY);

    @Test
    public void testOpenClientWithNSdks() throws Exception {
        sUiDevice.executeShellCommand("am start -n " + mClientAppPackageName + "/.MainActivity");
        // Allow metrics to stabilize after CUJ completion.
        Thread.sleep(WAIT_TIME_BEFORE_END_TEST_MS);
    }

    @AfterClass
    public static void closeApp() throws IOException {
        sUiDevice.pressHome();
    }
}
