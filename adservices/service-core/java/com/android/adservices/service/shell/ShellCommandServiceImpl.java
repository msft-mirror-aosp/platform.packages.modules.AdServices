/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.adservices.shell.IShellCommand;
import android.adservices.shell.IShellCommandCallback;
import android.adservices.shell.ShellCommandParam;
import android.adservices.shell.ShellCommandResult;
import android.os.RemoteException;

import com.android.adservices.LogUtil;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Implements a service which runs shell command in the AdServices process.
 *
 * <p>This internally calls {@link AdServicesShellCommandHandler} which has main logic to execute
 * the shell command.
 *
 * @hide
 */
public final class ShellCommandServiceImpl extends IShellCommand.Stub {
    @Override
    public void runShellCommand(ShellCommandParam param, IShellCommandCallback callback) {
        StringWriter outStringWriter = new StringWriter();
        StringWriter ErrStringWriter = new StringWriter();

        try (PrintWriter outPw = new PrintWriter(outStringWriter);
                PrintWriter errPw = new PrintWriter(ErrStringWriter); ) {
            AdServicesShellCommandHandler handler = new AdServicesShellCommandHandler(outPw, errPw);
            int resultCode = handler.run(param.getCommandArgs());
            ShellCommandResult response =
                    new ShellCommandResult.Builder()
                            .setResultCode(resultCode)
                            .setOut(outStringWriter.toString())
                            .setErr(ErrStringWriter.toString())
                            .build();
            callback.onResult(response);
        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send result to the callback for request: %s", param);
        }
    }
}
