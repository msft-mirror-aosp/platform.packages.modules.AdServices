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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.TestNamer.DEFAULT_TEST_NAME;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

// NOTE: not extending any superclass because its testing the uber superclass itself!
public final class SidelessTestCaseTest {

    @Rule public final Expect expect = Expect.create();

    private final SidelessTestCase mTestCase = new SidelessTestCase() {};

    @Test
    public void testExpect() {
        assertWithMessage("expect reference").that(mTestCase.expect).isNotNull();
    }

    @Test
    public void testGetTestName() {
        assertWithMessage("getTestName()")
                .that(mTestCase.getTestName())
                .isEqualTo(DEFAULT_TEST_NAME);
    }

    @Test
    public void testGetTestInvocationId() {
        int id1 = mTestCase.getTestInvocationId();
        expect.withMessage("id of test fixture").that(id1).isAtLeast(1);

        SidelessTestCase newTestCase = new SidelessTestCase() {};
        int id2 = newTestCase.getTestInvocationId();
        expect.withMessage("id of new test").that(id2).isEqualTo(id1 + 1);
    }
}
