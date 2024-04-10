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

package android.sdksandbox.test.scenario.testsdk;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.platform.test.scenario.annotation.Scenario;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Scenario
@RunWith(JUnit4.class)
public class LoadAd {
    private static final UiDevice sUiDevice = UiDevice.getInstance(getInstrumentation());
    private static final Bundle sArgsBundle = InstrumentationRegistry.getArguments();
    private static final Map<String, Boolean> sClientArgMap = new HashMap<>();

    private static final String CLIENT_APP_PACKAGE_NAME_KEY = "client-app-package-name";
    private static final String CLIENT_APP_ACTIVITY_NAME_KEY = "client-app-activity-name";
    // Prefix for matching boolean arguments to pass to client app.
    private static final String CLIENT_APP_ARG_KEY_PREFIX = "client-app-arg-";
    private static final int WAIT_TIME_BEFORE_END_TEST_MS = 3000;

    protected static String sPackageName;
    private static String sActivityName;
    private ClientAppUtils mClientAppUtils;

    /** Set up the arguments used to control the app under test. */
    @BeforeClass
    public static void setupArguments() {
        assertThat(sArgsBundle).isNotNull();
        sPackageName = sArgsBundle.getString(CLIENT_APP_PACKAGE_NAME_KEY);
        assertThat(sPackageName).isNotNull();
        sActivityName = sArgsBundle.getString(CLIENT_APP_ACTIVITY_NAME_KEY);
        assertThat(sActivityName).isNotNull();

        for (String argKey : sArgsBundle.keySet()) {
            if (argKey.startsWith(CLIENT_APP_ARG_KEY_PREFIX)) {
                sClientArgMap.put(
                        argKey.substring(CLIENT_APP_ARG_KEY_PREFIX.length()),
                        Boolean.parseBoolean(sArgsBundle.getString(argKey)));
            }
        }
    }

    @AfterClass
    public static void tearDown() throws IOException {
        if (sPackageName != null) {
            sUiDevice.executeShellCommand(ClientAppUtils.getStopAppCommand(sPackageName));
        }
    }

    @Before
    public void setup() throws Exception {
        mClientAppUtils = new ClientAppUtils(sUiDevice, sPackageName, sActivityName);
        sUiDevice.executeShellCommand(mClientAppUtils.getStartAppCommand(sClientArgMap));
        mClientAppUtils.initializeSdk();
    }

    @Test
    public void testLoadAd() throws Exception {
        mClientAppUtils.loadAd();
        Thread.sleep(WAIT_TIME_BEFORE_END_TEST_MS);
    }
}
