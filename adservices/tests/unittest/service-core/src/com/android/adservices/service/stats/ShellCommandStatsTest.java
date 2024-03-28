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

package com.android.adservices.service.stats;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class ShellCommandStatsTest extends AdServicesUnitTestCase {

    @Test
    public void testConstructor() {
        @ShellCommandStats.Command int command = ShellCommandStats.COMMAND_ECHO;
        @ShellCommandStats.CommandResult int result = ShellCommandStats.RESULT_SUCCESS;
        int latency = 1000;
        ShellCommandStats stats = new ShellCommandStats(command, result, latency);

        expect.withMessage("command").that(stats.command).isEqualTo(command);
        expect.withMessage("result").that(stats.result).isEqualTo(result);
        expect.withMessage("latency").that(stats.latencyMillis).isEqualTo(latency);
    }
}
