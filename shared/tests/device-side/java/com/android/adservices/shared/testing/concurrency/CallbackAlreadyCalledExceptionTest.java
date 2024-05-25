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
package com.android.adservices.shared.testing.concurrency;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedUnitTestCase;

import org.junit.Test;

public final class CallbackAlreadyCalledExceptionTest extends SharedUnitTestCase {

    private final String mName = "Bond, James Bond()";
    private final Value mPreviousValue = new Value("Previously, on 24:");
    private final Value mNewValue = new Value("Nexus Pixelus");

    @Test
    public void testNullName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CallbackAlreadyCalledException(
                                /* name= */ null, mPreviousValue, mNewValue));
    }

    @Test
    public void testGetters_nonNull() {
        CallbackAlreadyCalledException e =
                new CallbackAlreadyCalledException(mName, mPreviousValue, mNewValue);

        expect.withMessage("name").that(e.getName()).isEqualTo(mName);
        expect.withMessage("previous value")
                .that(e.getPreviousValue())
                .isSameInstanceAs(mPreviousValue);
        expect.withMessage("new value").that(e.getNewValue()).isSameInstanceAs(mNewValue);
        expect.withMessage("message")
                .that(e)
                .hasMessageThat()
                .isEqualTo(
                        "Bond, James Bond() already called with Previously, on 24: (and now called"
                                + " with Nexus Pixelus)");
    }

    @Test
    public void testGetters_nullPreviousValue() {
        CallbackAlreadyCalledException e =
                new CallbackAlreadyCalledException(mName, /* previousValue= */ null, mNewValue);

        expect.withMessage("name").that(e.getName()).isEqualTo(mName);
        expect.withMessage("previous value").that(e.getPreviousValue()).isNull();
        expect.withMessage("new value").that(e.getNewValue()).isSameInstanceAs(mNewValue);
        expect.withMessage("message")
                .that(e)
                .hasMessageThat()
                .isEqualTo(
                        "Bond, James Bond() already called with null (and now called with Nexus"
                                + " Pixelus)");
    }

    @Test
    public void testGetters_nullNewValue() {
        CallbackAlreadyCalledException e =
                new CallbackAlreadyCalledException(mName, mPreviousValue, /* newValue= */ null);

        expect.withMessage("name").that(e.getName()).isEqualTo(mName);
        expect.withMessage("previous value")
                .that(e.getPreviousValue())
                .isSameInstanceAs(mPreviousValue);
        expect.withMessage("new value").that(e.getNewValue()).isNull();
        expect.withMessage("message")
                .that(e)
                .hasMessageThat()
                .isEqualTo(
                        "Bond, James Bond() already called with Previously, on 24: (and now called"
                                + " with null)");
    }

    private static final class Value {
        private final String mValue;

        private Value(String value) {
            mValue = value;
        }

        @Override
        public String toString() {
            return mValue;
        }
    }
}
