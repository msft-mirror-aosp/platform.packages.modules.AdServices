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

    /** Default constructor. */
    public AdServicesDeviceSupportedRule() {
        super(new ConsoleLogger(AdServicesDeviceSupportedRule.class));
    }

    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public boolean isAdServicesSupportedOnDevice() throws Exception {
        boolean isSupported = AdServicesSupportHelper.isDeviceSupported(mDevice);
        mLog.v("isAdServicesSupportedOnDevice(): %b", isSupported);
        return isSupported;
    }

    @Override
    public boolean isLowRamDevice() throws Exception {
        boolean isLowRamDevice = AdServicesSupportHelper.isLowRamDevice(mDevice);
        mLog.v("isLowRamDevice(): %b", isLowRamDevice);
        return isLowRamDevice;
    }
}
