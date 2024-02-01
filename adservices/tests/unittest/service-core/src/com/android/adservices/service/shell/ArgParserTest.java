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

package com.android.adservices.service.shell;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class ArgParserTest extends AdServicesUnitTestCase {

    private static final String CMD = "not-relevant-command";

    @Test
    public void testParse_happyPath_success() {
        ArgParser argParser = new ArgParser();

        argParser.parse(CMD, "--hello", "world");

        expect.withMessage("`--hello world` argument parses")
                .that(argParser.getValue("hello"))
                .isEqualTo("world");
    }

    @Test
    public void testParse_multipleArguments_success() {
        ArgParser argParser = new ArgParser();

        argParser.parse(CMD, "--hello", "world", "--another", "argument");

        expect.withMessage("`--hello world` argument parses")
                .that(argParser.getValue("hello"))
                .isEqualTo("world");
        expect.withMessage("`--another argument` argument parses")
                .that(argParser.getValue("another"))
                .isEqualTo("argument");
    }

    @Test
    public void testParse_multipleArgumentsOutOfOrder_success() {
        ArgParser argParser = new ArgParser();

        // Same as above test, but with order swapped.
        argParser.parse(CMD, "--another", "argument", "--hello", "world");

        expect.withMessage("`--hello world` parses")
                .that(argParser.getValue("hello"))
                .isEqualTo("world");
        expect.withMessage("`--another argument` parses")
                .that(argParser.getValue("another"))
                .isEqualTo("argument");
    }

    @Test
    public void testParse_withRequired_success() {
        ArgParser argParser = new ArgParser("hello");

        argParser.parse(CMD, "--hello", "world");

        expect.withMessage("`--hello world` parses")
                .that(argParser.getValue("hello"))
                .isEqualTo("world");
    }

    @Test
    public void testParse_withUriArgument_success() {
        String uri = "http://google.com/about#abc123";

        ArgParser argParser = new ArgParser("uri");

        argParser.parse(CMD, "--uri", uri);
        expect.withMessage("`--uri` parses").that(argParser.getValue("uri")).isEqualTo(uri);
    }

    @Test
    public void testParse_noExtraArguments_success() {
        ArgParser argParser = new ArgParser();

        argParser.parse(CMD);
    }

    @Test
    public void testParse_duplicateArguments_throwsException() {
        ArgParser argParser = new ArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () -> argParser.parse(new String[] {CMD, "--hello", "world", "--hello", "world"}));
    }

    @Test
    public void testParse_noArguments_throwsException() {
        ArgParser argParser = new ArgParser();

        assertThrows(IllegalArgumentException.class, () -> argParser.parse(new String[] {}));
    }

    @Test
    public void testParse_emptyArgument_throwsException() {
        ArgParser argParser = new ArgParser();

        assertThrows(IllegalArgumentException.class, () -> argParser.parse(new String[] {CMD, ""}));
    }

    @Test
    public void testParse_emptyKey_throwsException() {
        ArgParser argParser = new ArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () -> argParser.parse(new String[] {CMD, "--world"}));
    }

    @Test
    public void testParse_emptyValue_throwsException() {
        ArgParser argParser = new ArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () -> argParser.parse(new String[] {CMD, "--hello"}));
    }

    @Test
    public void testParse_missingRequired_throwsException() {
        ArgParser argParser = new ArgParser("hello");

        assertThrows(
                IllegalArgumentException.class,
                () -> argParser.parse(new String[] {CMD, "--something", "else"}));
    }
}
