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
package com.android.adservices.common;

import com.android.adservices.shared.testing.TestDeviceHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

// TODO(b/347083260): add unit test (there's no AdServicesSharedLibrariesHostTests currently)
/*
 * TODO(b/281868718): currently this is the only custom TargetPreparer we're providing, so it's
 * quite simple. But ideally we should split it in a few classes:
 *
 * AbstractTargetPreparer - (side-less?) class hosted on shared directory, it would automatically
 * take care of plumbing tasks such as:
 *  - extracting ITestDevice from TestInformation (and checking it's not null)
 *  - throw UnsupportedOperationException on deprecated methods from ITargetPreparer
 *  - throw IllegalStateException on wrong calls (like setUp() / tearDown() more than once or
 *    tearDown() before setUp()
 *  - adding constructor that would make sure only one instance is running at any time
 *  - provide a protected / @VisibleForTesting isRunning() method
 *
 *  AbstractTestDeviceHelperSetterTargetPreparer - abstract class that extends
 *  AbstractTargetPreparer and has the logic to set TestDeviceHelperSetter, as currently doing here
 *  (so this class would initially just extend it)
 *
 *  TestDeviceHelperSetterTargetPreparer - final class that extends
 *  AbstractTestDeviceHelperSetterTargetPreparer, could be used in projects that don't want / need
 *  to extend AbstractTestDeviceHelperSetterTargetPreparer.
 */
/** Target preparer that should be used by all host-side tests. */
public final class AdServicesHostTestsTargetPreparer implements ITargetPreparer {

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        CLog.v("setup(%s)", testInformation);
        ITestDevice device = testInformation.getDevice();
        TestDeviceHelper.setTestDevice(device);
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        CLog.v("tearDown(%s, %s)", testInformation, e);
    }
}
