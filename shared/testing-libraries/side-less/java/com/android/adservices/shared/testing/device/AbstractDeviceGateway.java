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

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/** Base class for {@link DeviceGateway} implementations. */
public abstract class AbstractDeviceGateway implements DeviceGateway {

    protected final Logger mLog;

    protected AbstractDeviceGateway() {
        this(DynamicLogger.getInstance());
    }

    protected AbstractDeviceGateway(RealLogger realLogger) {
        mLog = new Logger(realLogger, getClass());
    }

    @Override
    @FormatMethod
    public final String runShellCommand(@FormatString String cmdFmt, Object... cmdArgs) {
        Objects.requireNonNull(cmdFmt, "cmdFmt cannot be null");

        ShellCommandInput input = new ShellCommandInput(cmdFmt, cmdArgs);
        ShellCommandOutput output = runShellCommandRwe(input);

        if (output == null) {
            throw new UnsupportedOperationException(
                    "INTERNAL ERROR: "
                            + getClass().getSimpleName()
                            + ".runShellCommandRwe("
                            + input
                            + ") returned null");
        }

        if (!output.getErr().isEmpty()) {
            throw new InvalidShellCommandResultException(input, output);
        }

        return output.getOut();
    }
}
