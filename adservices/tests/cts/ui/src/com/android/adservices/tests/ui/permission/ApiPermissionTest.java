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
package com.android.adservices.tests.ui.permission;

import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_ENABLED;

import android.adservices.common.AdServicesCommonManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.UiUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** API permission test for AdServices consent notification. */
@RunWith(AndroidJUnit4.class)
public class ApiPermissionTest {

    private AdServicesCommonManager mCommonManager;
    private UiDevice mDevice;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mCommonManager = AdServicesCommonManager.get(sContext);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Verify that that a calling app that does not hold the signature permission can not invoke the
     * API and trigger a consent notification.
     */
    @Test
    public void testApiPermission() throws Exception {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mCommonManager,
                (cm) -> cm.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED));

        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ true);
    }
}
