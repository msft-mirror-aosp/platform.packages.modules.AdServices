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

package com.android.adservices.service.common.compat;

import static com.google.common.truth.Truth.assertThat;

import android.os.Process;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Assume;
import org.junit.Test;

@MockStatic(Process.class)
@MockStatic(SdkLevel.class)
public final class ProcessCompatUtilsTest extends AdServicesExtendedMockitoTestCase {
    private static final int UID = 100;

    @Test
    public void testIsSdkSandboxUid_onSMinus_notSdkSandboxUid() {
        mocker.mockIsAtLeastT(false);
        assertThat(ProcessCompatUtils.isSdkSandboxUid(UID)).isFalse();
    }

    @Test
    public void testIsSdkSandboxUid_onTPlus_sdkSandboxUId() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandboxUid(UID));
        assertThat(ProcessCompatUtils.isSdkSandboxUid(UID)).isTrue();
    }

    @Test
    public void testIsSdkSandboxUid_onTPlus_notSdkSandboxUId() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ExtendedMockito.doReturn(false).when(() -> Process.isSdkSandboxUid(UID));
        assertThat(ProcessCompatUtils.isSdkSandboxUid(UID)).isFalse();
    }
}
