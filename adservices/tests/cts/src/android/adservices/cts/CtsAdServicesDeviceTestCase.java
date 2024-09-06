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
package android.adservices.cts;

import static com.android.adservices.AdServicesCommon.BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.SetLongDebugFlag;

// TODO (b/330324133): Short-term solution to allow test to extend binder timeout to
// resolve the test flakiness.
@SetLongDebugFlag(name = BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME, value = 10_000)
@SetCompatModeFlags
abstract class CtsAdServicesDeviceTestCase extends AdServicesCtsTestCase {
}
