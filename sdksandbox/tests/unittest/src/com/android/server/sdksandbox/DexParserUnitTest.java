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

package com.android.server.sdksandbox.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.sdksandbox.DeviceSupportedBaseTest;
import com.android.server.sdksandbox.verifier.DexParser.DexEntry;
import com.android.server.sdksandbox.verifier.SerialDexLoader.DexSymbols;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link DexParser}. */
public class DexParserUnitTest extends DeviceSupportedBaseTest {
    private static final String TEST_PACKAGENAME = "com.android.codeproviderresources_1";
    private static final String BUFFERED_READER_READ_LINE_CLASSNAME = "Ljava/io/BufferedReader";
    private static final String BUFFERED_READER_READ_LINE_METHOD_STRING =
            "readLine;Ljava/lang/String;";
    private static final String RESOURCES_GET_DISPLAY_METRICS_CLASSNAME =
            "Landroid/content/res/Resources";
    private static final String RESOURCES_GET_DISPLAY_METRICS_STRING =
            "getDisplayMetrics;Landroid/util/DisplayMetrics;";

    private PackageManager mPackageManager;
    private DexParser mDexParser = new DexParserImpl();

    @Before
    public void setUp() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPackageManager = ctx.getPackageManager();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    @Test
    public void getDexList() throws Exception {
        File apkPathFile = getAppFile(TEST_PACKAGENAME);
        assertThat(apkPathFile.exists()).isTrue();
        List<DexEntry> dexList = mDexParser.getDexFilePaths(apkPathFile);

        assertWithMessage("Dex list should not be empty " + apkPathFile.getAbsolutePath())
                .that(dexList)
                .isNotEmpty();
    }

    @Test
    public void resultContainsCalledApi() throws Exception {
        File apkPathFile = getAppFile(TEST_PACKAGENAME);
        assertThat(apkPathFile.exists()).isTrue();
        List<DexEntry> dexList = mDexParser.getDexFilePaths(apkPathFile);

        DexSymbols dexLoadResult = new DexSymbols();
        boolean foundCalledApi = false;
        for (DexEntry dexEntry : dexList) {
            mDexParser.loadDexSymbols(
                dexEntry.getApkFile(), dexEntry.getDexEntry(), dexLoadResult);
            if (dexLoadResult.hasReferencedMethod(
                    BUFFERED_READER_READ_LINE_CLASSNAME,
                    BUFFERED_READER_READ_LINE_METHOD_STRING)) {
                foundCalledApi = true;
                break;
            }
        }

        assertThat(foundCalledApi).isTrue();
    }

    @Test
    public void resultDoesNotContainAbsentApi() throws Exception {
        File apkPathFile = getAppFile(TEST_PACKAGENAME);
        assertThat(apkPathFile.exists()).isTrue();
        List<DexEntry> dexList = mDexParser.getDexFilePaths(apkPathFile);

        DexSymbols dexLoadResult = new DexSymbols();
        boolean foundAbsentApi = false;
        for (DexEntry dexEntry : dexList) {
            mDexParser.loadDexSymbols(dexEntry.getApkFile(), dexEntry.getDexEntry(), dexLoadResult);
            if (dexLoadResult.hasReferencedMethod(
                    RESOURCES_GET_DISPLAY_METRICS_CLASSNAME,
                    RESOURCES_GET_DISPLAY_METRICS_STRING)) {
                foundAbsentApi = true;
                break;
            }
        }

        assertThat(foundAbsentApi).isFalse();
    }

    private File getAppFile(String packageName) throws Exception {
        ApplicationInfo applicationInfo =
                mPackageManager.getPackageInfo(
                                packageName,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
                                        | PackageManager.MATCH_ANY_USER)
                        .applicationInfo;
        File apkPathFile = new File(applicationInfo.sourceDir);
        return apkPathFile;
    }
}
