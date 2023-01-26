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

import static org.junit.Assert.assertNull;

import android.test.mock.MockContext;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link SandboxedSdkContextUtils} */
@SmallTest
public final class SandboxedSdkContextUtilsTest {
    @Test
    public void testGetAsSandboxedSdkContext() {
        assertNull(SandboxedSdkContextUtils.getAsSandboxedSdkContext(null));
        assertNull(SandboxedSdkContextUtils.getAsSandboxedSdkContext(new MockContext()));

        // TODO(b/266693584): add test to validate SandboxedSdkContext handling.
    }
}
