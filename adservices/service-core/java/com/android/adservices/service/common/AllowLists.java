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

package com.android.adservices.service.common;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;

/** Utility class to handle AllowList for Apps and SDKs. */
public class AllowLists {
    @VisibleForTesting static final String ALLOW_ALL = "*";

    private static final String SPLITTER = ",";

    /**
     * A utility to check if an app package exists in the provided allow-list. The allow-list to
     * search is split by {@link #SPLITTER} without any white spaces. E.g. of a valid allow list -
     * "abc.package1.app,com.package2.app,com.package3.xyz" If the provided parameter {@code
     * appPackageName} exists in the allow-list (e.g. com.package2.app), then the method returns
     * true, false otherwise.
     */
    public static boolean isPackageAllowListed(
            @NonNull String allowList, @NonNull String appPackageName) {
        if (ALLOW_ALL.equals(allowList)) {
            return true;
        }

        // TODO(b/237686242): Cache the AllowList so that we don't need to read from Flags and split
        // on every API call.
        return Arrays.asList(allowList.split(SPLITTER)).contains(appPackageName);
    }
}
