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

import android.adservices.shell.IShellCommandCallback;
import android.adservices.shell.ShellCommandParam;
import android.adservices.shell.ShellCommandResult;
import android.os.IBinder;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.NoFailureSyncCallback;

import org.junit.Test;

public final class ShellCommandServiceImplTest extends AdServicesUnitTestCase {

    @Test
    public void testRunShellCommand() throws Exception {
        ShellCommandServiceImpl service = new ShellCommandServiceImpl();
        SyncIShellCommandCallback callback = new SyncIShellCommandCallback();

        service.runShellCommand(new ShellCommandParam("echo", "xxx"), callback);

        ShellCommandResult response = callback.assertResultReceived();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(0);
        expect.withMessage("out").that(response.getOut()).contains("xxx");
        expect.withMessage("err").that(response.getErr()).isEmpty();
    }

    @Test
    public void testRunShellCommand_invalidCommand() throws Exception {
        ShellCommandServiceImpl service = new ShellCommandServiceImpl();
        SyncIShellCommandCallback callback = new SyncIShellCommandCallback();

        service.runShellCommand(new ShellCommandParam("invalid-cmd"), callback);

        ShellCommandResult response = callback.assertResultReceived();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(-1);
        expect.withMessage("out").that(response.getOut()).isEmpty();
        expect.withMessage("err").that(response.getErr()).contains("Unknown command");
    }

    private static final class SyncIShellCommandCallback
            extends NoFailureSyncCallback<ShellCommandResult> implements IShellCommandCallback {

        @Override
        public void onResult(ShellCommandResult response) {
            injectResult(response);
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }
}
