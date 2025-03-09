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

import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.Nullable;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Generic device functionalities that are not provided by other interfaces in this package. */
public interface DeviceGateway {

    /**
     * Runs the given Shell command, returning its output.
     *
     * @throws InvalidShellCommandResultException if the Shell command returned a non-empty string
     *     on its standard error stream.
     */
    @FormatMethod
    String runShellCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs);

    /** Runs the given Shell command, returning its full output. */
    ShellCommandOutput runShellCommandRwe(ShellCommandInput input);

    /** Gets the SDK level of the device. */
    Level getSdkLevel();
}
