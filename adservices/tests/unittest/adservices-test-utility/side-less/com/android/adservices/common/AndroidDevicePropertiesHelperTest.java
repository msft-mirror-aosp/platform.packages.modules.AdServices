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
package com.android.adservices.common;

import com.android.adservices.shared.testing.AndroidDevicePropertiesHelper;
import com.android.adservices.shared.testing.ScreenSize;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class AndroidDevicePropertiesHelperTest {

    @Rule public final Expect expect = Expect.create();

    @Test
    public void testMatchScreenSize() {
        expectScreenSizeMatch(ScreenSize.LARGE_SCREEN, /*isLargeScreen=*/ true);
        expectScreenSizeNotMatch(ScreenSize.LARGE_SCREEN, /*isLargeScreen=*/ false);

        expectScreenSizeNotMatch(ScreenSize.SMALL_SCREEN, /*isLargeScreen=*/ true);
        expectScreenSizeMatch(ScreenSize.SMALL_SCREEN, /*isLargeScreen=*/ false);
    }

    private void expectScreenSizeMatch(ScreenSize screenSize, boolean isLargeScreen) {
        expect.withMessage(
                        "Device screen size %s matches the screen size of %s",
                        isLargeScreen, screenSize)
                .that(AndroidDevicePropertiesHelper.matchScreenSize(screenSize, isLargeScreen))
                .isTrue();
    }

    private void expectScreenSizeNotMatch(ScreenSize screenSize, boolean isLargeScreen) {
        expect.withMessage(
                        "Device screen size %s matches the screen size of %s",
                        isLargeScreen, screenSize)
                .that(AndroidDevicePropertiesHelper.matchScreenSize(screenSize, isLargeScreen))
                .isFalse();
    }
}
