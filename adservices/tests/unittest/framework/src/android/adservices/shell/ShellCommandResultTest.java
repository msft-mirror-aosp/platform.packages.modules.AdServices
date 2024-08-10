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

package android.adservices.shell;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class ShellCommandResultTest extends AdServicesUnitTestCase {

    @Test
    public void testToString_noFields() {
        ShellCommandResult result = new ShellCommandResult.Builder().build();

        String toString = result.toString();

        expect.withMessage("toString").that(toString).isEqualTo("ShellCommandResult[code=0]");
    }

    @Test
    public void testToString_allFields_emptyStreams() {
        ShellCommandResult result =
                new ShellCommandResult.Builder().setResultCode(42).setOut("").setErr("").build();

        String toString = result.toString();

        expect.withMessage("toString")
                .that(toString)
                .isEqualTo("ShellCommandResult[code=42, out_size=0, err_size=0]");
    }

    @Test
    public void testToString_allFields() {
        ShellCommandResult result =
                new ShellCommandResult.Builder()
                        .setResultCode(42)
                        .setOut("out!")
                        .setErr("D'OH!")
                        .build();

        String toString = result.toString();

        expect.withMessage("toString")
                .that(toString)
                .isEqualTo("ShellCommandResult[code=42, out_size=4, err_size=5]");
    }
}
