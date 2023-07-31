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

package com.android.tests.sdksandbox;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.AdServicesCommon;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class SdkSandboxSmallModuleTestApp {

    private static final String TAG = "SdkSandboxSmallModuleTestApp";
    private static final String[] AD_SERVICES = {
        AdServicesCommon.ACTION_TOPICS_SERVICE,
        AdServicesCommon.ACTION_CUSTOM_AUDIENCE_SERVICE,
        AdServicesCommon.ACTION_AD_SELECTION_SERVICE,
        AdServicesCommon.ACTION_MEASUREMENT_SERVICE,
        AdServicesCommon.ACTION_ADID_SERVICE,
        AdServicesCommon.ACTION_APPSETID_SERVICE,
        AdServicesCommon.ACTION_AD_SERVICES_COMMON_SERVICE,
    };

    @Rule public final Expect mExpect = Expect.create();

    private Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES);
    }

    @After
    public void tearDown() {}

    @Test
    public void installSmallModulePendingReboot() throws Exception {
        TestApp apexWithoutApk =
                new TestApp(
                        "ApexWithoutApk",
                        "com.android.adservices",
                        1,
                        /*isApex=*/ true,
                        "com.android.adservices.withoutapk.test.apex");
        Install.single(apexWithoutApk).setStaged().commit();
    }

    @Test
    public void testVerifyAdServicesAreUnavailable_preSmallModuleInstall() throws Exception {
        // Before small module is installed, all ad services should be available
        for (String service : AD_SERVICES) {
            mExpect.withMessage("%s is available", service)
                    .that(isAdServiceAvailable(service))
                    .isTrue();
        }
    }

    @Test
    public void testVerifyAdServicesAreUnavailable_postSmallModuleInstall() throws Exception {
        // Before small module is installed, all ad services should be unavailable
        for (String service : AD_SERVICES) {
            mExpect.withMessage("%s is available", service)
                    .that(isAdServiceAvailable(service))
                    .isFalse();
        }
    }

    /** Query PackageManager for exported services from AdServices APK. */
    private boolean isAdServiceAvailable(String serviceName) throws Exception {
        PackageManager pm = mContext.getPackageManager();

        Intent serviceIntent = new Intent(serviceName);
        List<ResolveInfo> resolveInfos =
                pm.queryIntentServicesAsUser(
                        serviceIntent,
                        PackageManager.GET_SERVICES
                                | PackageManager.MATCH_SYSTEM_ONLY
                                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        UserHandle.SYSTEM);

        ServiceInfo serviceInfo =
                AdServicesCommon.resolveAdServicesService(resolveInfos, serviceIntent.getAction());

        return serviceInfo != null;
    }
}
