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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Tests {@link SandboxedSdkContext} APIs.
 */
@RunWith(JUnit4.class)
public class SandboxedSdkContextUnitTest {

    private SandboxedSdkContext mSandboxedSdkContext;
    private static final String SDK_NAME = "com.android.codeproviderresources";
    private static final String RESOURCES_PACKAGE = "com.android.codeproviderresources_1";
    private static final String TEST_INTEGER_KEY = "test_integer";
    private static final int TEST_INTEGER_VALUE = 1234;
    private static final String TEST_STRING_KEY = "test_string";
    private static final String TEST_STRING_VALUE = "Test String";
    private static final String TEST_ASSET_FILE = "test-asset.txt";
    private static final String TEST_ASSET_VALUE = "This is a test asset";
    private static final String SDK_CE_DATA_DIR = "/data/misc_ce/0/sdksandbox/com.foo/sdk@123";
    private static final String SDK_DE_DATA_DIR = "/data/misc_de/0/sdksandbox/com.foo/sdk@123";

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                RESOURCES_PACKAGE,
                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        mSandboxedSdkContext = new SandboxedSdkContext(
                InstrumentationRegistry.getContext(), info, SDK_NAME,
                SDK_CE_DATA_DIR, SDK_DE_DATA_DIR);
    }

    @Test
    public void testResources() {
        Resources resources = mSandboxedSdkContext.getResources();
        assertThat(resources).isNotNull();
        int integerId = resources.getIdentifier(TEST_INTEGER_KEY, "integer", RESOURCES_PACKAGE);
        assertThat(resources.getInteger(integerId)).isEqualTo(TEST_INTEGER_VALUE);
        int stringId = resources.getIdentifier(TEST_STRING_KEY, "string", RESOURCES_PACKAGE);
        assertThat(resources.getString(stringId)).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testAssets() throws Exception {
        AssetManager assets = mSandboxedSdkContext.getAssets();
        assertThat(assets).isNotNull();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open(TEST_ASSET_FILE)));
        String readAsset = reader.readLine();
        assertThat(readAsset).isEqualTo(TEST_ASSET_VALUE);
    }

    @Test
    public void testGetDataDir_CredentialEncrypted() throws Exception {
        assertThat(mSandboxedSdkContext.getDataDir().toString()).isEqualTo(SDK_CE_DATA_DIR);

        Context superClass = mSandboxedSdkContext;
        assertThat(superClass.getDataDir().toString()).isEqualTo(SDK_CE_DATA_DIR);

        Context ceContext = mSandboxedSdkContext.createCredentialProtectedStorageContext();
        assertThat(ceContext.isCredentialProtectedStorage()).isTrue();
        assertThat(ceContext.getDataDir().toString()).isEqualTo(SDK_CE_DATA_DIR);
    }

    @Test
    public void testGetDataDir_DeviceEncrypted() throws Exception {
        Context deContext = mSandboxedSdkContext.createDeviceProtectedStorageContext();
        assertThat(deContext.isDeviceProtectedStorage()).isTrue();
        assertThat(deContext.getDataDir().toString()).isEqualTo(SDK_DE_DATA_DIR);

        Context superClass = deContext;
        assertThat(superClass.getDataDir().toString()).isEqualTo(SDK_DE_DATA_DIR);
    }
}
