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

package com.android.adservices.service;

import android.util.Log;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class FlagsTest extends AdServicesUnitTestCase {

    private static final String TAG = FlagsTest.class.getSimpleName();

    private final Flags mGlobalKsEnabled =
            new Flags() {
                public boolean getGlobalKillSwitch() {
                    Log.d(TAG, this + ".getGlobalKillSwitch(): returning true");
                    return true;
                }
                ;

                @Override
                public String toString() {
                    return "mGlobalKsEnabled";
                }
            };

    private final Flags mGlobalKsDisabled =
            new Flags() {
                public boolean getGlobalKillSwitch() {
                    Log.d(TAG, this + ".getGlobalKillSwitch(): returning false");
                    return false;
                }
                ;

                @Override
                public String toString() {
                    return "mGlobalKsDisabled";
                }
            };

    @Test
    public void testGetProtectedSignalsServiceKillSwitch() {
        expect.withMessage(
                        "getProtectedSignalsServiceKillSwitch() when global kill_switch is enabled")
                .that(mGlobalKsEnabled.getProtectedSignalsEnabled())
                .isFalse();

        expect.withMessage(
                        "getProtectedSignalsServiceKillSwitch() when global kill_switch is"
                                + " disabled")
                .that(mGlobalKsDisabled.getProtectedSignalsEnabled())
                .isEqualTo(Flags.PROTECTED_SIGNALS_ENABLED);
    }
}
