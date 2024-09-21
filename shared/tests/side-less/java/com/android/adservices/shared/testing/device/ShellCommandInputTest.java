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
package com.android.adservices.shared.testing.device;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.SidelessTestCase;

import org.junit.Test;

public final class ShellCommandInputTest extends SidelessTestCase {

    @Test
    @SuppressWarnings("FormatStringAnnotation")
    public void testConstructor_null() {
        assertThrows(NullPointerException.class, () -> new ShellCommandInput(null));
    }

    @Test
    public void testWithVarArgs() {
        var input = new ShellCommandInput("I am %s!", "Groot");

        expect.withMessage("getCommand()").that(input.getCommand()).isEqualTo("I am Groot!");
        expect.withMessage("toString()").that(input.toString()).isEqualTo("I am Groot!");
    }

    @Test
    public void testNoVarArgs() {
        var input = new ShellCommandInput("I am Groot!");

        expect.withMessage("getCommand()").that(input.getCommand()).isEqualTo("I am Groot!");
        expect.withMessage("toString()").that(input.toString()).isEqualTo("I am Groot!");
    }

    @Test
    public void testEqualsHashcode() {
        var equals1 = new ShellCommandInput("I am Groot!");
        var equals2 = new ShellCommandInput("I am %s!", "Groot");
        var different = new ShellCommandInput("I am IronMan!");
        var et = new EqualsTester(expect);

        et.expectObjectsAreEqual(equals1, equals1);
        et.expectObjectsAreEqual(equals1, equals2);
        et.expectObjectsAreEqual(equals2, equals2);
        et.expectObjectsAreNotEqual(equals1, different);
    }
}
