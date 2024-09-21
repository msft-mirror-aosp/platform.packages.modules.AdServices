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

import com.android.adservices.shared.testing.SidelessTestCase;

import org.junit.Test;

public final class InvalidShellCommandResultExceptionTest extends SidelessTestCase {

    private final ShellCommandInput mInput = new ShellCommandInput("rm -rf /");
    private final ShellCommandOutput mOutput = new ShellCommandOutput("No regrets!");

    @Test
    public void testConstructor_null() {
        assertThrows(
                NullPointerException.class,
                () -> new InvalidShellCommandResultException(/* input= */ null, mOutput));
        assertThrows(
                NullPointerException.class,
                () -> new InvalidShellCommandResultException(mInput, /* output= */ null));
    }

    @Test
    public void testValid() {
        InvalidShellCommandResultException e =
                new InvalidShellCommandResultException(mInput, mOutput);

        expect.withMessage("getInput()").that(e.getInput()).isSameInstanceAs(mInput);
        expect.withMessage("getOutput()").that(e.getOutput()).isSameInstanceAs(mOutput);
        expect.withMessage("getMessage()")
                .that(e.getMessage())
                .isEqualTo("Input: 'rm -rf /' Output: 'No regrets!'");
    }
}
