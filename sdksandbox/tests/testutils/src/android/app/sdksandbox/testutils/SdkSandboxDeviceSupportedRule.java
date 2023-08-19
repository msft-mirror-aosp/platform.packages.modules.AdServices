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

import com.android.adservices.common.AdServicesSupportHelper;
import com.android.adservices.common.AndroidLogger;

/** See {@link AbstractSdkSandboxDeviceSupportedRule}. */
public final class SdkSandboxDeviceSupportedRule extends AbstractSdkSandboxDeviceSupportedRule {

    private static final AndroidLogger sLogger =
            new AndroidLogger(SdkSandboxDeviceSupportedRule.class);

    /** Default constructor */
    public SdkSandboxDeviceSupportedRule() {
        super(sLogger);
    }

    @Override
    public boolean isSdkSandboxSupportedOnDevice() {
        boolean isSupported = AdServicesSupportHelper.isDeviceSupported();
        mLog.v("isSdkSandboxSupportedOnDevice(): %b", isSupported);
        return isSupported;
    }
}
