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

import static com.android.adservices.service.stats.ShellCommandStats.Command;
import static com.android.adservices.service.stats.ShellCommandStats.CommandResult;

import com.google.auto.value.AutoValue;

/** Class to provide execution result of a Shell Command. */
@AutoValue
public abstract class ShellCommandResult {

    /**
     * @return Result code of the shell command execution.
     */
    @CommandResult
    public abstract int getResultCode();

    /**
     * @return Stat logger identifier for the shell command execution.
     */
    @Command
    public abstract int getCommand();

    /**
     * @return {@link ShellCommandResult}.
     */
    public static ShellCommandResult create(@CommandResult int resultCode, @Command int command) {
        return new AutoValue_ShellCommandResult(resultCode, command);
    }
}
