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
package com.android.adservices.flags;

import static com.android.adservices.flags.AdServicesFlagsSetterRuleForUnitTestsTestCase.assertFakeFlagsFactoryFlags;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;

import org.junit.Test;

public final class FakeFlagsFactoryTest extends AdServicesUnitTestCase {

    @Test
    public void testGetFlagsForTest() {
        var flags = getSingleton();

        assertFakeFlagsFactoryFlags(expect, flags);
    }

    @Test
    public void testGetFlagsForTest_isSingleton() {
        var flags1 = getSingleton("1st getFlagsForTest() call");
        assertFakeFlagsFactoryFlags(expect, flags1);

        var flags2 = getSingleton("2nd getFlagsForTest() call");
        expect.withMessage("flag from 2nd getFlagsForTest() call")
                .that(flags2)
                .isSameInstanceAs(flags1);
    }

    @Test
    public void testGetFlagsForTest_isImmutable() {
        FakeFlags flags = (FakeFlags) getSingleton();
        boolean before = flags.getGlobalKillSwitch();

        assertThrows(
                UnsupportedOperationException.class,
                () -> flags.setFlag(KEY_GLOBAL_KILL_SWITCH, Boolean.toString(!before)));

        expect.withMessage("getGlobalKillSwitch() after setFlags() call that threw")
                .that(flags.getGlobalKillSwitch())
                .isEqualTo(before);
    }

    private static Flags getSingleton() {
        return getSingleton("getFlagsForTest()");
    }

    private static Flags getSingleton(String message) {
        var flags = FakeFlagsFactory.getFlagsForTest();
        assertWithMessage(message).that(flags).isNotNull();
        return flags;
    }
}
