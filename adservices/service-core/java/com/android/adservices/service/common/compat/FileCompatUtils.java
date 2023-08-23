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

import com.android.modules.utils.build.SdkLevel;

/** Utility class for handling file names in a backward-compatible manner */
public final class FileCompatUtils {
    private static final String ADSERVICES_PREFIX = "adservices_";

    private FileCompatUtils() {
        // prevent instantiation
    }

    /**
     * returns the appropriate filename to use based on Android version, prepending "adservices_"
     * for S-. The underscore is for human readability. The code for deleting files after OTA will
     * check only for "adservices" so files that begin with this already do not need to be updated
     */
    public static String getAdservicesFilename(String basename) {
        return SdkLevel.isAtLeastT() ? basename : ADSERVICES_PREFIX + basename;
    }
}
