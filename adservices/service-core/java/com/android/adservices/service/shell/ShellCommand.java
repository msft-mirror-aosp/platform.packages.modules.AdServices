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

import java.io.PrintWriter;

/** Base interface to run the shell commands. */
public interface ShellCommand {

    /** Runs the shell command and returns the result. */
    ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args);

    /**
     * @return the name of the command.
     */
    String getCommandName();

    /**
     * @return the metrics identifier of the command defined in {@link
     *     com.android.adservices.service.stats.AdServicesStatsLog}.
     */
    int getMetricsLoggerCommand();
}
