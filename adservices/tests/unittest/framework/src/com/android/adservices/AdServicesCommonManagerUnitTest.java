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

package com.android.adservices;

import static android.adservices.common.AdServicesCommonManager.MODULE_ADID;
import static android.adservices.common.AdServicesCommonManager.MODULE_MEASUREMENT;
import static android.adservices.common.AdServicesCommonManager.MODULE_ON_DEVICE_PERSONALIZATION;
import static android.adservices.common.AdServicesCommonManager.MODULE_PROTECTED_APP_SIGNALS;
import static android.adservices.common.AdServicesCommonManager.MODULE_PROTECTED_AUDIENCE;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_DISABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_ENABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_UNKNOWN;
import static android.adservices.common.AdServicesCommonManager.MODULE_TOPICS;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdServicesCommonManager;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link AdServicesCommonManager}. */
public final class AdServicesCommonManagerUnitTest extends AdServicesUnitTestCase {
    // TODO(b/378953302): Add flag on/off tests
    @Test
    public void testValidateModule() {
        int[] modules =
                new int[] {
                    MODULE_ADID,
                    MODULE_MEASUREMENT,
                    MODULE_ON_DEVICE_PERSONALIZATION,
                    MODULE_PROTECTED_APP_SIGNALS,
                    MODULE_PROTECTED_AUDIENCE,
                    MODULE_TOPICS
                };
        for (int module : modules) {
            expect.withMessage("validateModule(" + module + ")")
                    .that(AdServicesCommonManager.validateModule(module))
                    .isEqualTo(module);
        }
        assertThrows(
                IllegalArgumentException.class, () -> AdServicesCommonManager.validateModule(-1));
        assertThrows(
                IllegalArgumentException.class, () -> AdServicesCommonManager.validateModule(6));
    }

    @Test
    public void testValidateModuleState() {
        int[] states =
                new int[] {MODULE_STATE_UNKNOWN, MODULE_STATE_ENABLED, MODULE_STATE_DISABLED};
        for (int state : states) {
            expect.withMessage("validateModuleState(" + state + ")")
                    .that(AdServicesCommonManager.validateModuleState(state))
                    .isEqualTo(state);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> AdServicesCommonManager.validateModuleState(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> AdServicesCommonManager.validateModuleState(3));
    }
}
