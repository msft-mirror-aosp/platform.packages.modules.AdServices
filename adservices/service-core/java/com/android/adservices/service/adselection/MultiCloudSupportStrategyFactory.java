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

package com.android.adservices.service.adselection;

import com.android.adservices.service.common.CoordinatorOriginUriValidator;

/** Factory for {@link MultiCloudSupportStrategy} */
public class MultiCloudSupportStrategyFactory {

    private static class MultiCloudEnabledStrategy implements MultiCloudSupportStrategy {
        private String mAllowlist;

        MultiCloudEnabledStrategy(String allowlist) {
            this.mAllowlist = allowlist;
        }

        @Override
        public CoordinatorOriginUriValidator getCoordinatorOriginUriValidator() {
            return CoordinatorOriginUriValidator.createEnabledInstance(mAllowlist);
        }
    }

    private static class MultiCloudDisabledStrategy implements MultiCloudSupportStrategy {
        @Override
        public CoordinatorOriginUriValidator getCoordinatorOriginUriValidator() {
            return CoordinatorOriginUriValidator.createDisabledInstance();
        }
    }

    /** Get the strategy corresponding to whether multi-cloud feature is enabled or not. */
    public static MultiCloudSupportStrategy getStrategy(
            boolean multiCloudEnabled, String allowlist) {
        if (multiCloudEnabled) {
            return new MultiCloudEnabledStrategy(allowlist);
        }

        return new MultiCloudDisabledStrategy();
    }
}
