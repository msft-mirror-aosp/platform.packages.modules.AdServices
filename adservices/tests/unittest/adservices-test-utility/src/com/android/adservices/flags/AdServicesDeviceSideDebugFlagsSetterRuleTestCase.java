/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.flags;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;

import static org.junit.Assert.assertThrows;

import com.android.adservices.service.DebugFlags;

import org.junit.Test;

abstract class AdServicesDeviceSideDebugFlagsSetterRuleTestCase<
                R extends AbstractAdServicesDebugFlagsSetterRule<R, DF>, DF extends DebugFlags>
        extends AbstractAdServicesDebugFlagsSetterRuleTestCase<R, DF> {

    protected AdServicesDeviceSideDebugFlagsSetterRuleTestCase() {
        super(/* supportsDebugFlags= */ true);
    }

    @Test
    public final void testSetDebugFlag_nullName() {
        R rule = newRule();

        assertThrows(NullPointerException.class, () -> rule.setDebugFlag(null, false));
    }

    /**
     * Tests that the value of flag is reflect after setting it.
     *
     * <p>Note: getAdServicesShellCommandEnabled() was picked arbitrarily
     */
    @Test
    public final void testSetDebugFlag() {
        R rule = newRule();
        DF debugFlags = rule.getDebugFlags();
        expect.withMessage("getAdServicesShellCommandEnabled() before set()")
                .that(debugFlags.getAdServicesShellCommandEnabled())
                .isFalse();

        rule.setDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED, true);
        expect.withMessage("getAdServicesShellCommandEnabled() after set()")
                .that(debugFlags.getAdServicesShellCommandEnabled())
                .isTrue();
    }
}
