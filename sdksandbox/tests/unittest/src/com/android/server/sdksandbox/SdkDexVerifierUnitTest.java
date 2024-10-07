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
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.sdksandbox.DeviceSupportedBaseTest;
import com.android.server.sdksandbox.proto.Verifier.AllowedApi;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisList;
import com.android.server.sdksandbox.verifier.SdkDexVerifier.VerificationResult;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SdkDexVerifierUnitTest extends DeviceSupportedBaseTest {

    private static final String TEST_PACKAGENAME = "com.android.codeproviderresources_1";

    private static final List<AllowedApi> ALL_ALLOWED_APIS =
            List.of(
                    AllowedApi.newBuilder().setClassName("Landroid/*").setAllow(true).build(),
                    AllowedApi.newBuilder().setClassName("Landroidx/*").setAllow(true).build(),
                    AllowedApi.newBuilder().setClassName("Lcom/android/*").setAllow(true).build(),
                    AllowedApi.newBuilder()
                            .setClassName("Lcom/google/android/*")
                            .setAllow(true)
                            .build(),
                    AllowedApi.newBuilder().setClassName("Ljava/*").setAllow(true).build(),
                    AllowedApi.newBuilder().setClassName("Ljavax/*").setAllow(true).build());

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();
    private final SdkDexVerifier mVerifier = SdkDexVerifier.getInstance();

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    @Test
    public void getApiTokens_fullyQualifiedRule() {
        final AllowedApi apiRuleFull =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getCurrentInputMethodSubtype")
                        .addParameters("V")
                        .setReturnType("Landroid/view/inputmethod/InputMethodSubtype")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getCurrentInputMethodSubtype",
            "V",
            "Landroid/view/inputmethod/InputMethodSubtype"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleFull);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_classAndMethodRule() {
        final AllowedApi apiRuleClassMethod =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getCurrentInputMethodSubtype")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getCurrentInputMethodSubtype",
            null
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassMethod);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_multiParam() {
        final AllowedApi apiRuleMultiParam =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getInputMethodListAsUser")
                        .addParameters("I") // int, according to DEX TypeDescriptor Semantics
                        .addParameters("I")
                        .setReturnType("Ljava/util/List")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getInputMethodListAsUser",
            "I",
            "I",
            "Ljava/util/List"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleMultiParam);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_classReturn() {
        final AllowedApi apiRuleClassReturn =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setReturnType("Ljava/util/List")
                        .build();
        String[] expectedKeys = {
            "Landroid", "view", "inputmethod", "InputMethodManager", null, "Ljava/util/List"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassReturn);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_classAndParams() {
        final AllowedApi apiRuleClassParam =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .addParameters("V")
                        .build();
        String[] expectedKeys = {
            "Landroid", "view", "inputmethod", "InputMethodManager", null, "V", null
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassParam);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void startDexVerification_loadApisFails() throws Exception {
        ApiAllowlistProvider failAllowlistProvider =
                new ApiAllowlistProvider("allowlist_doesn't_exist");
        TestOutcomeReceiver callback = new TestOutcomeReceiver();
        SdkDexVerifier verifier =
                new SdkDexVerifier(
                        new SdkDexVerifier.Injector(
                                failAllowlistProvider,
                                new SerialDexLoader(new DexParserImpl(), MAIN_HANDLER)));

        verifier.startDexVerification(
                "apk_that_doesn't_get_verified",
                "com.test.unverified_test_app",
                33,
                mContext,
                callback);

        assertThat(callback.getLastError()).isNotNull();
        assertThat(callback.getLastError().getMessage())
                .isEqualTo("allowlist_doesn't_exist not found.");
    }

    @Test
    public void startDexVerification_apkNotFound() throws Exception {
        TestOutcomeReceiver callback = new TestOutcomeReceiver();

        mVerifier.startDexVerification(
                "bogusPath", "com.test.nonexistent_test_app", 33, mContext, callback);

        assertThat(callback.getLastError()).isNotNull();
        assertThat(callback.getLastError().getMessage())
                .isEqualTo("Apk to verify not found: bogusPath");
    }

    @Test
    public void startDexVerification_passVerify() throws Exception {
        AllowedApisList allowlist =
                AllowedApisList.newBuilder().addAllAllowedApis(ALL_ALLOWED_APIS).build();
        ApiAllowlistProvider fakeAllowlistProvider = new FakeAllowlistProvider(allowlist);
        TestOutcomeReceiver callback = new TestOutcomeReceiver();
        SdkDexVerifier verifier =
                new SdkDexVerifier(
                        new SdkDexVerifier.Injector(
                                fakeAllowlistProvider,
                                new SerialDexLoader(new DexParserImpl(), MAIN_HANDLER)));

        verifier.startDexVerification(
                getPackageLocation(TEST_PACKAGENAME), TEST_PACKAGENAME, 33, mContext, callback);

        assertThat(callback.getResult().hasPassed()).isTrue();
        assertThat(callback.getResult().getRestrictedUsages()).isEmpty();
    }

    @Test
    public void startDexVerification_failVerify() throws Exception {
        ArrayList<AllowedApi> allowedApis = new ArrayList<AllowedApi>(ALL_ALLOWED_APIS);
        allowedApis.add(
                AllowedApi.newBuilder()
                        .setClassName("Ljava/lang/String/*")
                        .setAllow(false)
                        .build());
        AllowedApisList allowlist =
                AllowedApisList.newBuilder().addAllAllowedApis(allowedApis).build();
        ApiAllowlistProvider fakeAllowlistProvider = new FakeAllowlistProvider(allowlist);
        TestOutcomeReceiver callback = new TestOutcomeReceiver();
        SdkDexVerifier verifier =
                new SdkDexVerifier(
                        new SdkDexVerifier.Injector(
                                fakeAllowlistProvider,
                                new SerialDexLoader(new DexParserImpl(), MAIN_HANDLER)));

        verifier.startDexVerification(
                getPackageLocation(TEST_PACKAGENAME), TEST_PACKAGENAME, 33, mContext, callback);

        assertThat(callback.getResult().hasPassed()).isFalse();
        assertThat(callback.getResult().getRestrictedUsages().size()).isGreaterThan(0);
        assertThat(callback.getResult().getRestrictedUsages().get(0))
                .startsWith("Ljava/lang/String");
    }

    private String getPackageLocation(String packageName) throws Exception {
        ApplicationInfo applicationInfo =
                mPackageManager.getPackageInfo(
                                packageName,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
                                        | PackageManager.MATCH_ANY_USER)
                        .applicationInfo;
        return applicationInfo.sourceDir;
    }

    private static class FakeAllowlistProvider extends ApiAllowlistProvider {
        Map<Long, AllowedApisList> mFakeAllowlist;

        FakeAllowlistProvider(AllowedApisList allowlist) {
            super();
            mFakeAllowlist = Map.of(33L, allowlist);
        }

        @Override
        public Map<Long, AllowedApisList> loadPlatformApiAllowlist() {
            return mFakeAllowlist;
        }
    }

    private static class TestOutcomeReceiver
            implements OutcomeReceiver<VerificationResult, Exception> {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private Exception mLastError;
        private VerificationResult mResult;

        public Exception getLastError() throws Exception {
            assertWithMessage("Latch timed out").that(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mLastError;
        }

        public VerificationResult getResult() throws Exception {
            assertWithMessage("Verification completed in 5 seconds")
                    .that(mLatch.await(5, TimeUnit.SECONDS))
                    .isTrue();
            return mResult;
        }

        @Override
        public void onResult(VerificationResult result) {
            mResult = result;
            mLatch.countDown();
        }

        @Override
        public void onError(Exception e) {
            mLastError = e;
            mLatch.countDown();
        }
    }
}
