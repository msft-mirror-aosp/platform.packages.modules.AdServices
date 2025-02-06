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

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.AbstractDebugFlagsSetterRuleTestCase;

import org.junit.Test;

abstract class AbstractAdServicesDebugFlagsSetterRuleTestCase<
                R extends AbstractAdServicesDebugFlagsSetterRule<R, DF>, DF>
        extends AbstractDebugFlagsSetterRuleTestCase<R> {

    private final boolean mSupportsDebugFlags;

    protected AbstractAdServicesDebugFlagsSetterRuleTestCase(boolean supportsDebugFlags) {
        mSupportsDebugFlags = supportsDebugFlags;
    }

    @Test
    public final void testGetDebugFlags() {
        R rule = newRule();
        if (mSupportsDebugFlags) {
            expect.withMessage("getDebugFlags()").that(rule.getDebugFlags()).isNotNull();
            return;
        }
        assertThrows(UnsupportedOperationException.class, () -> rule.getDebugFlags());
    }
}
