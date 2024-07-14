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
package android.app.sdksandbox.testutils;

import android.app.sdksandbox.common.AbstractSdkSandboxDeviceSupportedRule;

import com.android.adservices.common.AdServicesSupportHelper;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.modules.utils.build.SdkLevel;

/** See {@link AbstractSdkSandboxDeviceSupportedRule}. */
public final class SdkSandboxDeviceSupportedRule extends AbstractSdkSandboxDeviceSupportedRule {

    public SdkSandboxDeviceSupportedRule() {
        super(AndroidLogger.getInstance());
    }

    @Override
    public boolean isSdkSandboxSupportedOnDevice() {
        boolean isDeviceSupported = AdServicesSupportHelper.getInstance().isDeviceSupported();
        boolean isSdkLevelSupported = isDeviceSupported && SdkLevel.isAtLeastU();
        mLog.v("isSdkSandboxSupportedOnDevice(): %b", isSdkLevelSupported);
        return isSdkLevelSupported;
    }
}