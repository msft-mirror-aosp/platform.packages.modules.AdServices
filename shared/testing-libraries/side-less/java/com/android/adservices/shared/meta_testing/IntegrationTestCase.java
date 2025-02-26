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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.AbstractSdkLevelSupportedRule;

import org.junit.Rule;

/** Base class for integration tests, it provides the bare minimum support. */
public abstract class IntegrationTestCase extends SharedSidelessTestCase {

    // TODO(b/342639109): make sure it's the right order
    @Rule(order = 0)
    public final AbstractSdkLevelSupportedRule sdkLevel = getSdkLevelSupportRule();

    /** Gets the side-specific {@code SdkLevelSupportedRule}. */
    protected abstract AbstractSdkLevelSupportedRule getSdkLevelSupportRule();
}
