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

import java.util.List;

/** Topics part of the app manifest config (<ad-services-config>). */
public class AppManifestIncludesSdkLibraryConfig {
    private final List<String> mIncludesSdkLibraries;

    /**
     * Constructor.
     *
     * @param includesSdkLibraries corresponds to the list in the config.
     */
    public AppManifestIncludesSdkLibraryConfig(@NonNull List<String> includesSdkLibraries) {
        mIncludesSdkLibraries = includesSdkLibraries;
    }

    /** Getter for allowAdPartnersToAccess. */
    @NonNull
    public List<String> getIncludesSdkLibraries() {
        return mIncludesSdkLibraries;
    }
}
