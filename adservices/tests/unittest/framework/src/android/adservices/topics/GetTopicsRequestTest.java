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
package android.adservices.topics;

import static android.adservices.topics.TopicsManager.EMPTY_SDK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.adservices.topics.GetTopicsRequest}
 */
@SmallTest
public final class GetTopicsRequestTest {
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final String SOME_SDK_NAME = "SomeSDKName";
    private static final String RESOURCES_PACKAGE = "com.android.codeproviderresources_1";
    private static final String CLIENT_PACKAGE_NAME = "com.android.client";
    private static final String SDK_NAME = "com.android.codeproviderresources";
    private static final String SDK_CE_DATA_DIR = "/data/misc_ce/0/sdksandbox/com.foo/sdk@123";
    private static final String SDK_DE_DATA_DIR = "/data/misc_de/0/sdksandbox/com.foo/sdk@123";

    @Test
    public void testSandboxedSdk_setSdkName() throws Exception {
        SandboxedSdkContext mSandboxedSdkContext =
                new SandboxedSdkContext(
                        InstrumentationRegistry.getContext(),
                        CLIENT_PACKAGE_NAME,
                        new ApplicationInfo(),
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR);

        // Sdk setting the SdkName even when running inside the sandbox.
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new GetTopicsRequest.Builder(mSandboxedSdkContext)
                            .setSdkName(SOME_SDK_NAME)
                            .build();
                });
    }

    @Test
    public void testSandboxedSdk_notSetSdkName() throws Exception {
        SandboxedSdkContext mSandboxedSdkContext =
                new SandboxedSdkContext(
                        InstrumentationRegistry.getContext(),
                        CLIENT_PACKAGE_NAME,
                        new ApplicationInfo(),
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR);
        GetTopicsRequest request = // don't call setSdkName
                new GetTopicsRequest.Builder(mSandboxedSdkContext).build();
        assertThat(request.getSdkName()).isEqualTo(SDK_NAME);
    }

    @Test
    public void testNonSandboxedSdk_nonNullSdkName() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder(sContext).setSdkName(SOME_SDK_NAME).build();

        assertThat(request.getSdkName()).isEqualTo(SOME_SDK_NAME);
    }

    @Test
    public void testNonSandboxedSdk_nullSdkName() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder(sContext)
                        // Not setting mSdkName making it null.
                        .build();
        // When sdkName is not set in builder, we will use EMPTY_SDK by default
        assertThat(request.getSdkName()).isEqualTo(EMPTY_SDK);
    }

    @Test
    public void testNonSandboxedSdk_nonNullContext() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder(sContext).setSdkName(SOME_SDK_NAME).build();

        assertThat(request.getSdkName()).isEqualTo(SOME_SDK_NAME);
    }

    @Test
    public void testNonSandboxedSdk_nullContext() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new GetTopicsRequest.Builder(/* context */ null)
                            .setSdkName(SOME_SDK_NAME)
                            .build();
                });
    }
}
