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

import com.android.adservices.shared.testing.HostSideSdkLevelSupportRule;
import com.android.adservices.shared.testing.HostSideTestCase;
import com.android.adservices.shared.testing.annotations.SetSyncDisabledModeForTest;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.ClassRule;
import org.junit.Rule;

/**
 * Base class for host-side tests, it contains just the bare minimum setup needed by all tests (like
 * implementing {@link IDeviceTest}).
 *
 * <p><b>Note: </b> when using it, you must also add the {@link
 * com.android.adservices.common.AdServicesHostTestsTargetPreparer} in the {@code AndroidText.xml}.
 */
@SetSyncDisabledModeForTest
public abstract class AdServicesHostSideTestCase extends HostSideTestCase {

    // Need to define these constants here so they can be used on subclasses annotations
    public static final String CTS_TEST_PACKAGE = "com.android.adservices.cts";
    public static final String APPSEARCH_WRITER_ACTIVITY_CLASS = "AppSearchWriterActivity";

    @ClassRule
    public static final AdServicesHostSideFlagsPreparerClassRule sFlagsPreparer =
            new AdServicesHostSideFlagsPreparerClassRule();

    @Rule(order = 0)
    public final HostSideSdkLevelSupportRule sdkLevel = HostSideSdkLevelSupportRule.forAnyLevel();

    @Rule(order = 1)
    public final AdServicesHostSideDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesHostSideDeviceSupportedRule();

    @Rule(order = 2)
    public final AdServicesHostSideFlagsSetterRule flags = getAdServicesHostSideFlagsSetterRule();

    protected AdServicesHostSideFlagsSetterRule getAdServicesHostSideFlagsSetterRule() {
        return AdServicesHostSideFlagsSetterRule.forCompatModeEnabledTests();
    }
}
