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

package android.adservices.common;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

/** Unit tests for {@link SandboxedSdkContextUtils} */
public final class SandboxedSdkContextUtilsTest extends AdServicesMockitoTestCase {
    @Test
    public void testGetAsSandboxedSdkContext_inputIsNotSandboxedSdkContext() {
        expect.withMessage("null context")
                .that(SandboxedSdkContextUtils.getAsSandboxedSdkContext(null))
                .isNull();
        expect.withMessage("mock context")
                .that(SandboxedSdkContextUtils.getAsSandboxedSdkContext(mMockContext))
                .isNull();
        expect.withMessage("real context")
                .that(SandboxedSdkContextUtils.getAsSandboxedSdkContext(mContext))
                .isNull();
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testGetAsSandboxedSdkContext_inputIsSandboxedSdkContext() {
        Context sandboxedSdkContext =
                new SandboxedSdkContext(
                        /* baseContext= */ mContext,
                        /* classLoader= */ mContext.getClassLoader(),
                        /* clientPackageName= */ mContext.getPackageName(),
                        /* info= */ mContext.getApplicationInfo(),
                        /* sdkName= */ "sdkName",
                        /* sdkCeDataDir= */ null,
                        /* sdkDeDataDir= */ null);
        assertThat(SandboxedSdkContextUtils.getAsSandboxedSdkContext(sandboxedSdkContext))
                .isSameInstanceAs(sandboxedSdkContext);
    }
}
