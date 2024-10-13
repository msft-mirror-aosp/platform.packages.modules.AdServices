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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.atLeastOnce;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Build;
import android.os.SystemProperties;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

public final class BuildCompatUtilsTest extends AdServicesExtendedMockitoTestCase {

    @Test
    @MockStatic(Build.class)
    @SpyStatic(BuildCompatUtils.class)
    public void testIsDebuggable_SPlus_debuggable() {
        doReturn(true).when(Build::isDebuggable);
        assertWithMessage("BuildCompatUtils.isDebuggable")
                .that(BuildCompatUtils.isDebuggable())
                .isTrue();
        verify(Build::isDebuggable, atLeastOnce());
        verify(BuildCompatUtils::computeIsDebuggable_R, never());
    }

    @Test
    @MockStatic(Build.class)
    @SpyStatic(BuildCompatUtils.class)
    public void testIsDebuggable_SPlus_notDebuggable() {
        doReturn(false).when(Build::isDebuggable);
        assertWithMessage("BuildCompatUtils.isDebuggable")
                .that(BuildCompatUtils.isDebuggable())
                .isFalse();
        verify(Build::isDebuggable, atLeastOnce());
        verify(BuildCompatUtils::computeIsDebuggable_R, never());
    }

    @Test
    @MockStatic(SdkLevel.class)
    @SpyStatic(BuildCompatUtils.class)
    public void testIsDebuggable_RMinus_debuggable() {
        mocker.mockIsAtLeastS(false);
        doReturn(true).when(BuildCompatUtils::computeIsDebuggable_R);
        assertWithMessage("BuildCompatUtils.isDebuggable")
                .that(BuildCompatUtils.isDebuggable())
                .isTrue();
        verify(BuildCompatUtils::computeIsDebuggable_R);
    }

    @Test
    @MockStatic(SdkLevel.class)
    @SpyStatic(BuildCompatUtils.class)
    public void testIsDebuggable_RMinus_notDebuggable() {
        mocker.mockIsAtLeastS(false);
        doReturn(false).when(BuildCompatUtils::computeIsDebuggable_R);
        assertWithMessage("BuildCompatUtils.isDebuggable")
                .that(BuildCompatUtils.isDebuggable())
                .isFalse();
        verify(BuildCompatUtils::computeIsDebuggable_R);
    }

    @Test
    @SpyStatic(SystemProperties.class)
    public void testComputeIsDebuggable_notDebuggable() {
        doReturn(0).when(() -> SystemProperties.getInt(anyString(), anyInt()));
        assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                .that(BuildCompatUtils.computeIsDebuggable_R())
                .isFalse();
        verify(() -> SystemProperties.getInt("ro.debuggable", 0));
    }

    @Test
    @SpyStatic(SystemProperties.class)
    public void testComputeIsDebuggable_debuggable() {
        doReturn(1).when(() -> SystemProperties.getInt(anyString(), anyInt()));
        assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                .that(BuildCompatUtils.computeIsDebuggable_R())
                .isTrue();
        verify(() -> SystemProperties.getInt("ro.debuggable", 0));
    }
}
