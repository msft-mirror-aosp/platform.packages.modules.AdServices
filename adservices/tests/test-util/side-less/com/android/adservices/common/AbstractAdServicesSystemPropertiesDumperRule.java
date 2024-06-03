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

import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.SystemPropertiesHelper;

// TODO(b/328064701): add unit tests
/** Rule used to dump some system properties when a test fails. */
abstract class AbstractAdServicesSystemPropertiesDumperRule
        extends AbstractSystemPropertiesDumperRule {

    // TODO(b/328064701): static import from AdServicesCommonConstants instead
    public static final String SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX = "debug.adservices.";

    protected AbstractAdServicesSystemPropertiesDumperRule(
            RealLogger logger, SystemPropertiesHelper.Interface helper) {
        super(logger, SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX, helper);
    }
}
