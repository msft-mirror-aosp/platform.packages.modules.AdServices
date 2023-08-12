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
package com.android.adservices.common;

/** See {@link AbstractAdServicesDeviceSupportedRule}. */
public final class AdServicesDeviceSupportedRule extends AbstractAdServicesDeviceSupportedRule {

    /** Creates a rule using {@link Mode#SUPPORTED_BY_DEFAULT}. */
    public AdServicesDeviceSupportedRule() {
        this(Mode.SUPPORTED_BY_DEFAULT);
    }

    /** Creates a rule with the given mode. */
    public AdServicesDeviceSupportedRule(Mode mode) {
        super(new AndroidLogger(AdServicesDeviceSupportedRule.class), mode);
    }

    @Override
    public boolean isFeatureSupported() {
        boolean isSupported = AdServicesSupportHelper.isDeviceSupported();
        mLog.v("isFeatureSupported(): %b", isSupported);
        return isSupported;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Will always throw the exception (if any), as AdService APIs should not throw exceptions
     * when running on unsupported devices.
     */
    @Override
    public void afterTest(boolean testShouldRunAsSupported, Throwable thrown) throws Throwable {
        if (thrown != null) {
            throw thrown;
        }
    }
}
