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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.ScreenSize.LARGE_SCREEN;

/** Helper class to check Android Device Properties. */
public final class AndroidDevicePropertiesHelper {

    /** TODO(b/324919960): document / clarify */
    public static boolean matchScreenSize(ScreenSize screenSize, boolean isLargeScreenDevice) {
        if (screenSize.equals(LARGE_SCREEN)) {
            return isLargeScreenDevice;
        }
        return !isLargeScreenDevice;
    }

    private AndroidDevicePropertiesHelper() {
        throw new UnsupportedOperationException();
    }
}
