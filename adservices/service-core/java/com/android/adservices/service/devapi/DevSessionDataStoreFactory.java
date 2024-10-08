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

package com.android.adservices.service.devapi;

import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

/** Factory that produces {@link DevSessionDataStore}. */
public class DevSessionDataStoreFactory {

    /**
     * @return an instance of {@link DevSessionDataStore}.
     */
    public static DevSessionDataStore get() {
        return get(FlagsFactory.getFlags().getDeveloperModeFeatureEnabled());
    }

    /**
     * @return an instance of {@link DevSessionDataStore} for testing.
     * @param developerModeFeatureEnabled if {@code true} then return a production instance.
     */
    @VisibleForTesting
    public static DevSessionDataStore get(boolean developerModeFeatureEnabled) {
        return developerModeFeatureEnabled
                ? DevSessionProtoDataStore.getInstance()
                : new DevSessionDataStoreNoOp();
    }
}
