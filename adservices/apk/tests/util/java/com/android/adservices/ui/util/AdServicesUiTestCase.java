/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.ui.util;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.app.KeyguardManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

/**
 * Base class for all settings UI unit tests.
 *
 * <p>Contains basic device setup and teardown methods.
 */
@DisableGlobalKillSwitch
@SetAllLogcatTags
@SetCompatModeFlags
@SetFlagTrue(KEY_GA_UX_FEATURE_ENABLED)
public abstract class AdServicesUiTestCase extends AdServicesExtendedMockitoTestCase {

    public static final int LAUNCH_TIMEOUT_MS = 5_000;

    // TODO(b/384798806): called realFlags because it's "really" changing the Flags using
    // DeviceConfig (and superclass will eventually provide a flags object that uses
    // AdServicesFakeFlagsSetterRule). Ideally this class should use that same flags, but it doesn't
    // support DebugFlags (we'll need to wait until the DebugFlags logic is moved to its own rule).
    @Rule(order = 11)
    public final AdServicesFlagsSetterRule realFlags = AdServicesFlagsSetterRule.newInstance();

    protected final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    protected Context mSpyContext;

    @Before
    public final void setUpDevice() throws Exception {
        mSpyContext = spy(appContext.get());

        // Unlock the device if required
        KeyguardManager keyguardManager = mSpyContext.getSystemService(KeyguardManager.class);
        if (keyguardManager.isKeyguardLocked()) {
            mDevice.swipe(
                    mDevice.getDisplayWidth() / 2,
                    mDevice.getDisplayHeight(),
                    mDevice.getDisplayWidth() / 2,
                    0,
                    50);
        }
        // Start from the home screen
        mDevice.pressHome();
        mDevice.setOrientationNatural();

        // Wait for launcher
        String launcherPackage = mDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        mDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT_MS);
    }

    @After
    public final void takeScreenshotAndKillProcess() throws Exception {
        ApkTestUtil.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + getTestName() + "_");

        AdservicesTestHelper.killAdservicesProcess(appContext.get());
    }
}
