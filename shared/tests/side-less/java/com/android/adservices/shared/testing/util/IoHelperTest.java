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
package com.android.adservices.shared.testing.util;

import static com.android.adservices.shared.testing.util.IoHelper.printStreamToString;
import static com.android.adservices.shared.testing.util.IoHelper.printWriterToString;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import org.junit.Test;

public final class IoHelperTest extends SharedSidelessTestCase {

    @Test
    public void testPrintWriterToString_null() throws Exception {
        assertThrows(NullPointerException.class, () -> printWriterToString(null));
    }

    @Test
    public void testPrintWriterToString() throws Exception {
        String string = printWriterToString(pw -> pw.write("I write(), therefore I am()"));

        assertWithMessage("dump()").that(string).isEqualTo("I write(), therefore I am()");
    }

    @Test
    public void testPrintStreamToString_null() throws Exception {
        assertThrows(NullPointerException.class, () -> printStreamToString(null));
    }

    @Test
    public void testPrintStreamToString() throws Exception {
        String string = printStreamToString(ps -> ps.print("I stream(), therefore I am()"));

        assertWithMessage("dump()").that(string).isEqualTo("I stream(), therefore I am()");
    }
}
