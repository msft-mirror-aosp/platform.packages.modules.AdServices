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
package android.sdksandbox.test.scenario.sampleclient;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Helper class for Sdk Sandbox e2e perf tests. */
public class SdkSandboxTestHelper {
    private static final UiDevice sUiDevice = UiDevice.getInstance(getInstrumentation());

    private static final long UI_NAVIGATION_WAIT_MS = 1000;
    private static final String SANDBOX_TEST_CLIENT_APP = "com.android.sdksandboxclient";
    private static final String LOAD_BUTTON = "load_code_button";
    private static final String RENDER_BUTTON = "request_surface_button";

    /** Open sandbox client test app using shell command line. */
    public void openClientApp() throws Exception {
        sUiDevice.executeShellCommand(
                "am start " + SANDBOX_TEST_CLIENT_APP + "/" + ".MainActivity");
    }

    /** Load sdk on sandbox client test app by clicking the loadSdk button. */
    public void loadSandboxSdk() {
        if (getLoadSdkButton() != null) {
            getLoadSdkButton().click();
        } else {
            throw new RuntimeException("Did not find 'Load SDK' button.");
        }

        // wait until loadSdk
        SystemClock.sleep(TimeUnit.SECONDS.toMillis(2));

        assertThat(getLoadSdkButton().getText()).isEqualTo("Unload SDK");
    }

    /** Remote render ad on sandbox client test app by clicking request surface button. */
    public void remoteRenderAd() {
        if (getRequestSurfaceButton() != null) {
            getRequestSurfaceButton().click();
        } else {
            throw new RuntimeException("Did not find 'Load Surface Package' button.");
        }
    }

    public static void closeClientApp() throws IOException {
        sUiDevice.executeShellCommand("am force-stop " + SANDBOX_TEST_CLIENT_APP);
    }

    private UiObject2 getLoadSdkButton() {
        return sUiDevice.wait(
                Until.findObject(By.res(SANDBOX_TEST_CLIENT_APP, LOAD_BUTTON)),
                UI_NAVIGATION_WAIT_MS);
    }

    private UiObject2 getRequestSurfaceButton() {
        return sUiDevice.wait(
                Until.findObject(By.res(SANDBOX_TEST_CLIENT_APP, RENDER_BUTTON)),
                UI_NAVIGATION_WAIT_MS);
    }
}
