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
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

public final class ShellCommandStatsTest extends AdServicesUnitTestCase {

    @Test
    public void testConstructor() {
        @ShellCommandStats.Command int command = ShellCommandStats.COMMAND_ECHO;
        @ShellCommandStats.CommandResult int result = ShellCommandStats.RESULT_SUCCESS;
        int latency = 1000;
        ShellCommandStats stats = new ShellCommandStats(command, result, latency);

        expect.withMessage("command").that(stats.getCommand()).isEqualTo(command);
        expect.withMessage("result").that(stats.getResult()).isEqualTo(result);
        expect.withMessage("latency").that(stats.getLatencyMillis()).isEqualTo(latency);
    }

    @Test
    public void testEqualsHashCode() {
        EqualsTester et = new EqualsTester(expect);
        ShellCommandStats stats1 =
                new ShellCommandStats(
                        ShellCommandStats.COMMAND_ECHO, ShellCommandStats.RESULT_SUCCESS, 1000);
        ShellCommandStats stats2 =
                new ShellCommandStats(
                        ShellCommandStats.COMMAND_ECHO, ShellCommandStats.RESULT_SUCCESS, 1000);

        et.expectObjectsAreEqual(stats1, stats2);

        ShellCommandStats stats3 =
                new ShellCommandStats(
                        ShellCommandStats.COMMAND_CUSTOM_AUDIENCE_LIST,
                        ShellCommandStats.RESULT_SUCCESS,
                        1000);

        et.expectObjectsAreNotEqual(stats1, stats3);
        et.expectObjectsAreNotEqual(stats2, stats3);
    }
}
