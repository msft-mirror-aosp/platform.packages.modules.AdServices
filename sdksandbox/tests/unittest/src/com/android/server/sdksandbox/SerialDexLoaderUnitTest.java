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

package com.android.server.sdksandbox.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.android.server.sdksandbox.DeviceSupportedBaseTest;
import com.android.server.sdksandbox.verifier.DexParser.DexEntry;
import com.android.server.sdksandbox.verifier.SerialDexLoader.DexSymbols;
import com.android.server.sdksandbox.verifier.SerialDexLoader.VerificationHandler;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SerialDexLoaderUnitTest extends DeviceSupportedBaseTest {
    private static final String TAG = "SdkSandboxVerifierTest";
    private static final File APK_PATH_FILE = new File("apk_path");
    private static final DexEntry DEX_ENTRY = new DexEntry(APK_PATH_FILE, "classes.dex");
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final int PM_SDK_FLAGS =
            PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES | PackageManager.MATCH_ANY_USER;

    private Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private FakeDexParser mFakeParser = new FakeDexParser();
    private SerialDexLoader mSerialDexLoader = new SerialDexLoader(mFakeParser, MAIN_HANDLER);
    private PackageManager mSpyPackageManager;
    private ApplicationInfo mApplicationInfo = new ApplicationInfo();
    private PackageInfo mPackageInfo = new PackageInfo();

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        mContext = Mockito.spy(mContext);
        mSpyPackageManager = Mockito.spy(mContext.getPackageManager());
        mApplicationInfo.sourceDir = APK_PATH_FILE.getAbsolutePath();
        mPackageInfo.applicationInfo = mApplicationInfo;
        Mockito.doReturn(mSpyPackageManager).when(mContext).getPackageManager();
    }

    @Test
    public void loadSingleDex_succeeds() throws Exception {
        TestVerificationHandler handler =
                new TestVerificationHandler(/* countdownToFailVerify= */ 100);

        mFakeParser.setDexLists(Arrays.asList(DEX_ENTRY));

        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename", mContext, handler);

        assertThat(handler.getPassedVerification()).isTrue();
        assertThat(handler.getLoadedDexCount()).isEqualTo(1);
    }

    @Test
    public void loadMultiple_succeeds() throws Exception {
        // Load and verify first dex
        mFakeParser.setDexLists(Arrays.asList(DEX_ENTRY));
        TestVerificationHandler handler1 =
                new TestVerificationHandler(/* countdownToFailVerify= */ 100);
        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename1", mContext, handler1);
        assertThat(handler1.getPassedVerification()).isTrue();
        assertThat(handler1.getLoadedDexCount()).isEqualTo(1);

        // Load and verify two dexes more
        TestVerificationHandler handler2 =
                new TestVerificationHandler(/* countdownToFailVerify= */ 100);
        mFakeParser.setDexLists(
                Arrays.asList(
                        new DexEntry(APK_PATH_FILE, "classes1.dex"),
                        new DexEntry(APK_PATH_FILE, "classes2.dex")));
        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename2", mContext, handler2);
        assertThat(handler2.getPassedVerification()).isTrue();
        assertThat(handler2.getLoadedDexCount()).isEqualTo(2);
    }

    @Test
    public void failedVerification_nextSucceeds() throws Exception {
        // Verification of classes1.dex fails
        List<DexEntry> dexEntries =
                Arrays.asList(
                        new DexEntry(APK_PATH_FILE, "classes1.dex"),
                        new DexEntry(APK_PATH_FILE, "classes2.dex"));
        mFakeParser.setDexLists(dexEntries);
        TestVerificationHandler handler1 =
                new TestVerificationHandler(/* countdownToFailVerify= */ 0);
        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename1", mContext, handler1);
        assertThat(handler1.getPassedVerification()).isFalse();
        assertThat(handler1.getLoadedDexCount()).isEqualTo(1);

        // Verification of next dex files succeed
        mFakeParser.setDexLists(dexEntries);
        TestVerificationHandler handler2 =
                new TestVerificationHandler(/* countdownToFailVerify= */ 100);
        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename2", mContext, handler2);
        assertThat(handler2.getPassedVerification()).isTrue();
        assertThat(handler2.getLoadedDexCount()).isEqualTo(2);
    }

    @Test
    public void failedSingleVerification() throws Exception {
        TestVerificationHandler handler =
                new TestVerificationHandler(/* countdownToFailVerify= */ 0);
        mFakeParser.setDexLists(Arrays.asList(DEX_ENTRY));
        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename", mContext, handler);

        assertThat(handler.getPassedVerification()).isFalse();
        assertThat(handler.getLoadedDexCount()).isEqualTo(1);
    }

    @Test
    public void getDexListFails_handlesException() throws Exception {
        TestVerificationHandler handler =
                new TestVerificationHandler(/* countdownToFailVerify= */ 0);
        mFakeParser.throwExceptionOnGetDexListWithMessage("Error getting dex list");
        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename", mContext, handler);

        assertThat(handler.getThrownException())
                .hasMessageThat()
                .contains("Error getting dex list");
    }

    @Test
    public void loadSymbolsFails_handlesException() throws Exception {
        TestVerificationHandler handler =
                new TestVerificationHandler(/* countdownToFailVerify= */ 0);
        mFakeParser.setDexLists(Arrays.asList(DEX_ENTRY));
        mFakeParser.throwExceptionOnLoadDexSymbols(/* countdownToError= */ 0);
        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename", mContext, handler);

        assertThat(handler.getThrownException())
                .hasMessageThat()
                .contains("apk files not found for com.test.packagename");
    }

    @Test
    public void verifiesTmpAndInstalledDexes_succeeds() throws Exception {
        Mockito.doReturn(mPackageInfo)
                .when(mSpyPackageManager)
                .getPackageInfo("com.test.packagename", PM_SDK_FLAGS);
        TestVerificationHandler handler =
                new TestVerificationHandler(/* countdownToFailVerify= */ 100);
        mFakeParser.setDexLists(
                Arrays.asList(
                        new DexEntry(APK_PATH_FILE, "classes1.dex"),
                        new DexEntry(APK_PATH_FILE, "classes2.dex"),
                        new DexEntry(APK_PATH_FILE, "classes3.dex")));
        mFakeParser.throwExceptionOnLoadDexSymbols(/* countdownToError= */ 1);

        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename", mContext, handler);
        assertThat(handler.getPassedVerification()).isTrue();
        assertThat(handler.getLoadedDexCount()).isEqualTo(3);
    }

    @Test
    public void installedDexFailsVerify() throws Exception {
        Mockito.doReturn(mPackageInfo)
                .when(mSpyPackageManager)
                .getPackageInfo("com.test.packagename", PM_SDK_FLAGS);
        TestVerificationHandler handler =
                new TestVerificationHandler(/* countdownToFailVerify= */ 2);
        mFakeParser.setDexLists(
                Arrays.asList(
                        new DexEntry(APK_PATH_FILE, "classes1.dex"),
                        new DexEntry(APK_PATH_FILE, "classes2.dex"),
                        new DexEntry(APK_PATH_FILE, "classes3.dex"),
                        new DexEntry(APK_PATH_FILE, "classes4.dex")));
        mFakeParser.throwExceptionOnLoadDexSymbols(/* countdownToError= */ 1);

        mSerialDexLoader.queueApkToLoad(APK_PATH_FILE, "com.test.packagename", mContext, handler);
        assertThat(handler.getPassedVerification()).isFalse();
        assertThat(handler.getLoadedDexCount()).isEqualTo(3);
    }

    @Test
    public void testDexLoadResultCount() throws Exception {
        DexSymbols dexLoadResult = new DexSymbols();
        dexLoadResult.addReferencedMethod("class1", "method1");
        dexLoadResult.addReferencedMethod("class2", "method2");
        dexLoadResult.addReferencedMethod("class2", "method3");

        assertThat(dexLoadResult.getReferencedMethodCount()).isEqualTo(3);
    }

    @Test
    public void testDexLoadResult_sameClass() throws Exception {
        DexSymbols dexLoadResult = new DexSymbols();
        dexLoadResult.addReferencedMethod("class1", "method1");
        dexLoadResult.addReferencedMethod("class1", "method2");

        assertThat(dexLoadResult.getReferencedMethodAtIndex(1)).isEqualTo("method2");
        assertThat(dexLoadResult.getClassForMethodAtIndex(1)).isEqualTo("class1");
    }

    @Test
    public void testDexLoadResult_differentClass() throws Exception {
        DexSymbols dexLoadResult = new DexSymbols();
        dexLoadResult.addReferencedMethod("class1", "method1");
        dexLoadResult.addReferencedMethod("class2", "method2");

        assertThat(dexLoadResult.getReferencedMethodAtIndex(0)).isEqualTo("method1");
        assertThat(dexLoadResult.getClassForMethodAtIndex(0)).isEqualTo("class1");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(1)).isEqualTo("method2");
        assertThat(dexLoadResult.getClassForMethodAtIndex(1)).isEqualTo("class2");
    }

    @Test
    public void testDexLoadResult_manyClassesManyMethods() throws Exception {
        DexSymbols dexLoadResult = new DexSymbols();
        dexLoadResult.addReferencedMethod("class1", "method1");
        dexLoadResult.addReferencedMethod("class1", "method2");
        dexLoadResult.addReferencedMethod("class2", "method3");
        dexLoadResult.addReferencedMethod("class2", "method4");
        dexLoadResult.addReferencedMethod("class2", "method5");
        dexLoadResult.addReferencedMethod("class3", "method6");

        assertThat(dexLoadResult.getReferencedMethodAtIndex(0)).isEqualTo("method1");
        assertThat(dexLoadResult.getClassForMethodAtIndex(0)).isEqualTo("class1");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(1)).isEqualTo("method2");
        assertThat(dexLoadResult.getClassForMethodAtIndex(1)).isEqualTo("class1");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(2)).isEqualTo("method3");
        assertThat(dexLoadResult.getClassForMethodAtIndex(2)).isEqualTo("class2");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(3)).isEqualTo("method4");
        assertThat(dexLoadResult.getClassForMethodAtIndex(3)).isEqualTo("class2");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(4)).isEqualTo("method5");
        assertThat(dexLoadResult.getClassForMethodAtIndex(4)).isEqualTo("class2");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(5)).isEqualTo("method6");
        assertThat(dexLoadResult.getClassForMethodAtIndex(5)).isEqualTo("class3");
    }

    @Test
    public void testDexLoadResult_clear() throws Exception {
        DexSymbols dexLoadResult = new DexSymbols();
        dexLoadResult.addReferencedMethod("class1", "method1");
        dexLoadResult.addReferencedMethod("class1", "method2");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(1)).isEqualTo("method2");
        assertThat(dexLoadResult.getClassForMethodAtIndex(1)).isEqualTo("class1");

        dexLoadResult.clearAndSetDexEntry("fakeDexEntry");
        dexLoadResult.addReferencedMethod("class2", "method3");
        dexLoadResult.addReferencedMethod("class2", "method4");
        assertThat(dexLoadResult.getReferencedMethodAtIndex(1)).isEqualTo("method4");
        assertThat(dexLoadResult.getClassForMethodAtIndex(1)).isEqualTo("class2");
    }

    @Test
    public void testDexLoadResult_hasReferencedMethod() throws Exception {
        DexSymbols dexLoadResult = new DexSymbols();
        dexLoadResult.addReferencedMethod("class1", "method1");
        dexLoadResult.addReferencedMethod("class1", "method2");

        assertThat(dexLoadResult.hasReferencedMethod("class1", "method1")).isTrue();
        assertThat(dexLoadResult.hasReferencedMethod("class1", "method2")).isTrue();
        assertThat(dexLoadResult.hasReferencedMethod("class1", "method3")).isFalse();
        assertThat(dexLoadResult.hasReferencedMethod("class2", "method1")).isFalse();
    }

    private static class FakeDexParser implements DexParser {
        List<DexEntry> mFakeDexList;
        IOException mExceptionOnGetDexList;
        IOException mExceptionOnLoadDexSymbols;
        int mCountdownToError = 0;

        public void setDexLists(List<DexEntry> fakeDexList) {
            mFakeDexList = fakeDexList;
        }

        public void throwExceptionOnGetDexListWithMessage(String exceptionMessage) {
            mExceptionOnGetDexList = new IOException(exceptionMessage);
        }

        public void throwExceptionOnLoadDexSymbols(int countdownToError) {
            mCountdownToError = countdownToError;
            mExceptionOnLoadDexSymbols = new IOException();
        }

        @Override
        public List<DexEntry> getDexFilePaths(File apkPathFile) throws IOException {
            if (mExceptionOnGetDexList != null) {
                throw mExceptionOnGetDexList;
            }
            return mFakeDexList;
        }

        @Override
        public void loadDexSymbols(File apkFile, String dexEntry, DexSymbols dexLoadResult)
                throws IOException {
            if (mExceptionOnLoadDexSymbols != null && mCountdownToError == 0) {
                mCountdownToError--;
                throw mExceptionOnLoadDexSymbols;
            }
            mCountdownToError--;
        }
    }

    private static class TestVerificationHandler implements VerificationHandler {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private int mCountdownToFailVerify;
        private int mLoadedDexCount = 0;
        private boolean mPassed = false;
        private Exception mException;

        TestVerificationHandler(int countdownToFailVerify) {
            mCountdownToFailVerify = countdownToFailVerify;
        }

        public int getLoadedDexCount() throws Exception {
            assertWithMessage("Latch timed out").that(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mLoadedDexCount;
        }

        public boolean getPassedVerification() throws Exception {
            assertWithMessage("Latch timed out").that(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mPassed;
        }

        public Exception getThrownException() throws Exception {
            assertWithMessage("Latch timed out").that(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mException;
        }

        @Override
        public boolean verify(DexSymbols result) {
            mLoadedDexCount++;
            if (mCountdownToFailVerify <= 0) {
                return false;
            }
            mCountdownToFailVerify--;
            return true;
        }

        @Override
        public void onVerificationCompleteForPackage(boolean passed) {
            mPassed = passed;
            mLatch.countDown();
        }

        @Override
        public void onVerificationErrorForPackage(Exception e) {
            mException = e;
            mLatch.countDown();
        }
    }
}
