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

import com.android.adservices.service.Flags;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;

/** Utility class to handle AllowList for Apps and SDKs. */
public class AllowLists {
    @VisibleForTesting static final String ALLOW_ALL = "*";

    private static final String SPLITTER = ",";

    /** Check if an app is allowed to call Topics API */
    public static boolean appCanUsePpapi(@NonNull Flags flags, @NonNull String appPackageName) {
        if (ALLOW_ALL.equals(flags.getPpapiAppAllowList())) {
            return true;
        }

        // TODO(b/237686242): Cache the AllowList so that we don't need to read from Flags and split
        // on every API call.
        return Arrays.asList(flags.getPpapiAppAllowList().split(SPLITTER)).contains(appPackageName);
    }
}
