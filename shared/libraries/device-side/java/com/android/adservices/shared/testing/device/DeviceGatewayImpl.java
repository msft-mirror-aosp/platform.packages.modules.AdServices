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

import android.app.UiAutomation;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.AndroidSdk.Level;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class DeviceGatewayImpl extends AbstractDeviceGateway {

    private static final int OUT_DESCRIPTOR_INDEX = 0;
    private static final int IN_DESCRIPTOR_INDEX = 1;
    private static final int ERR_DESCRIPTOR_INDEX = 2;

    public DeviceGatewayImpl() {
        super(AndroidLogger.getInstance());
    }

    @Override
    public Level getSdkLevel() {
        return Level.forLevel(Build.VERSION.SDK_INT);
    }

    @Override
    public ShellCommandOutput runShellCommandRwe(ShellCommandInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        String cmd = input.getCommand();

        // UiAutomation.executeShellCommandRwe() was added on T
        if (!getSdkLevel().isAtLeast(Level.T)) {
            return runShellCommandRweSMinus(cmd);
        }
        return runShellCommandRweTPlus(cmd);
    }

    // NOTE: logic below copied from AdServicesShellCommandHelper (plus extra logging), which most
    // likely copied it from SystemUtil
    private ShellCommandOutput runShellCommandRweTPlus(String cmd) {
        mLog.d("Calling UiAutomation.executeShellCommandRwe('%s') on runShellCommandRwe()", cmd);
        String out = "";
        String err = "";
        try {
            UiAutomation uiAutomation =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation();
            ParcelFileDescriptor[] fds = uiAutomation.executeShellCommandRwe(cmd);
            ParcelFileDescriptor fdOut = fds[OUT_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdIn = fds[IN_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdErr = fds[ERR_DESCRIPTOR_INDEX];

            fdOut.checkError();
            fdErr.checkError();

            if (fdIn != null) {
                mLog.v("Closing 'in' (fd %d)", IN_DESCRIPTOR_INDEX);
                try {
                    // not using stdin
                    fdIn.close();
                } catch (Exception e) {
                    mLog.w(e, "Exception closing in (fd %d)", IN_DESCRIPTOR_INDEX);
                    // Ignore
                }
            }
            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdOut)) {
                mLog.v("Reading 'out' (fd %d)", OUT_DESCRIPTOR_INDEX);
                out = new String(readInputStreamFully(fis)).trim();
                mLog.v("out: %d chars", out.length());
            }
            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdErr)) {
                mLog.v("Reading 'err' (fd %d)", ERR_DESCRIPTOR_INDEX);
                err = new String(readInputStreamFully(fis)).trim();
                mLog.v("err: %d chars", err.length());
            }
            return new ShellCommandOutput.Builder().setOut(out).setErr(err).build();
        } catch (IOException e) {
            // TODO(b/368704903): need proper unit test for this
            mLog.e(e, "Failure running %s", cmd);
            return new ShellCommandOutput.Builder().setOut(out).setErr(e.getMessage()).build();
        }
    }

    private ShellCommandOutput runShellCommandRweSMinus(String cmd) {
        mLog.w(
                "NOTE: calling UiAutomation.executeShellCommand('%s') on runShellCommandRwe() -"
                    + " that method does NOT support standand error so if the specific command"
                    + " failed by writing on standerd error, this call might pass - unfortunately,"
                    + " there's nothing we can do about that...",
                cmd);
        String out = "";
        try {
            UiAutomation uiAutomation =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation();
            ParcelFileDescriptor fdOut = uiAutomation.executeShellCommand(cmd);

            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdOut)) {
                mLog.v("Reading 'out' (fd %d)", OUT_DESCRIPTOR_INDEX);
                out = new String(readInputStreamFully(fis)).trim();
                mLog.v("out: %d chars", out.length());
            }
            return new ShellCommandOutput(out);
        } catch (IOException e) {
            // TODO(b/368704903): need proper unit test for this
            mLog.e(e, "Failure running %s", cmd);
            return new ShellCommandOutput.Builder().setOut(out).setErr(e.getMessage()).build();
        }
    }

    // TODO(b/324491698): copied from com.android.compatibility.common.util.FileUtils, as we cannot
    // import that project currently as it causes build failures (most likely because this class is
    // provided by a java-library project, not an android-library)
    private static byte[] readInputStreamFully(InputStream is) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[32768];
        int count;
        try {
            while ((count = is.read(buffer)) != -1) {
                os.write(buffer, 0, count);
            }
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return os.toByteArray();
    }
}
