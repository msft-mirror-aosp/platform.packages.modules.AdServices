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

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxSmallModuleTestApp {

    private static final String TAG = "SdkSandboxSmallModuleTestApp";

    private Context mContext;

    @Before
    public void setup() {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES);
    }

    @After
    public void tearDown() {}

    @Test
    public void installApexWithoutApkPendingReboot() throws Exception {
        TestApp apexWithoutApk =
                new TestApp(
                        "ApexWithoutApk",
                        "com.android.adservices",
                        1,
                        /*isApex=*/ true,
                        "com.android.adservices.withoutapk.test.apex");
        Install.single(apexWithoutApk).setStaged().commit();
    }
}
