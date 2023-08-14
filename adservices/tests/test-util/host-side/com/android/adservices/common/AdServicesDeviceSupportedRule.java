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

import com.android.tradefed.device.ITestDevice;

/** See {@link AbstractAdServicesDeviceSupportedRule}. */
public final class AdServicesDeviceSupportedRule extends AbstractAdServicesDeviceSupportedRule {

    private ITestDevice mDevice;

    /** Creates a rule using {@link Mode#SUPPORTED_BY_DEFAULT}. */
    public AdServicesDeviceSupportedRule() {
        this(Mode.SUPPORTED_BY_DEFAULT);
    }

    /** Creates a rule with the given mode. */
    public AdServicesDeviceSupportedRule(Mode mode) {
        super(new ConsoleLogger(AdServicesDeviceSupportedRule.class), mode);
    }

    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public boolean isFeatureSupported() throws Exception {
        boolean isSupported = AdServicesSupportHelper.isDeviceSupported(mDevice);
        mLog.v("isFeatureSupported(): %b", isSupported);
        return isSupported;
    }
}
