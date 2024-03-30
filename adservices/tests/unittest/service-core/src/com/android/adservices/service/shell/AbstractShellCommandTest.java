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

import static com.android.adservices.service.shell.AbstractShellCommand.toShellCommandResult;
import static com.android.adservices.service.shell.AbstractShellCommand.toBoolean;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import org.junit.Test;

public final class AbstractShellCommandTest extends AdServicesMockitoTestCase {

    @SuppressWarnings("common typo")
    @Test
    public void testToBoolean_success() {
        expect.withMessage("toBoolean(arg=true)").that(toBoolean("true")).isTrue();

        expect.withMessage("toBoolean(arg=TRUE)").that(toBoolean("TRUE")).isTrue();

        expect.withMessage("toBoolean(arg= true )").that(toBoolean(" true ")).isTrue();

        expect.withMessage("toBoolean(arg=TruE)").that(toBoolean("TruE")).isTrue();

        expect.withMessage("toBoolean(arg=false)").that(toBoolean("false")).isFalse();

        expect.withMessage("toBoolean(arg=FALSE)").that(toBoolean("FALSE")).isFalse();
    }

    @Test
    public void testToBoolean_returnsNull() {
        expect.withMessage("toBoolean(arg=\"\")").that(toBoolean("")).isNull();

        expect.withMessage("toBoolean(arg=abc)").that(toBoolean("abc")).isNull();
    }

    @Test
    public void testReturnWithResult_returnsNull() {
        ShellCommandResult result =
                toShellCommandResult(
                        ShellCommandStats.COMMAND_ECHO, ShellCommandStats.RESULT_SUCCESS);
        expect.withMessage("toBoolean(arg=\"\")")
                .that(result.getResultCode())
                .isEqualTo(ShellCommandStats.RESULT_SUCCESS);
        expect.withMessage("toBoolean(arg=\"\")")
                .that(result.getCommand())
                .isEqualTo(ShellCommandStats.COMMAND_ECHO);
    }
}
