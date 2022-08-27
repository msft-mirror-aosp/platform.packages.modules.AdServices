/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.sdksandbox.ISdkSandboxService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.FileDescriptor;

public class SdkSandboxShellCommandUnitTest {

    private static final String DEBUGGABLE_PACKAGE = "android.app.debuggable";
    private static final String NON_DEBUGGABLE_PACKAGE = "android.app.nondebuggable";
    private static final int UID = 10214;
    private static final String INVALID_PACKAGE = "android.app.invalid";
    private Context mContext;
    private FakeSdkSandboxManagerService mService;

    private final FileDescriptor mIn = FileDescriptor.in;
    private final FileDescriptor mOut = FileDescriptor.out;
    private final FileDescriptor mErr = FileDescriptor.err;

    private PackageManager mPackageManager;

    @Before
    public void setup() throws Exception {
        mContext = Mockito.spy(InstrumentationRegistry.getInstrumentation().getContext());

        mService =
                Mockito.spy(
                        new FakeSdkSandboxManagerService(
                                mContext, Mockito.mock(SdkSandboxServiceProvider.class)));

        mPackageManager = Mockito.mock(PackageManager.class);

        Mockito.when(mContext.getPackageManager()).thenReturn(mPackageManager);

        final ApplicationInfo debuggableInfo = Mockito.mock(ApplicationInfo.class);
        debuggableInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
        debuggableInfo.uid = UID;

        Mockito.doReturn(debuggableInfo)
                .when(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        final ApplicationInfo nonDebuggableInfo = Mockito.mock(ApplicationInfo.class);
        nonDebuggableInfo.uid = UID;

        Mockito.doReturn(nonDebuggableInfo)
                .when(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(NON_DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.doThrow(new PackageManager.NameNotFoundException())
                .when(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(INVALID_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));
    }

    @Test
    public void testCommandFailsIfCallerNotShellOrRoot() {
        final SdkSandboxShellCommand.Injector injector =
                new SdkSandboxShellCommand.Injector() {
                    @Override
                    int getCallingUid() {
                        return UserHandle.USER_ALL;
                    }
                };
        final SdkSandboxShellCommand cmd = new SdkSandboxShellCommand(mService, mContext, injector);

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);
    }

    @Test
    public void testStartFailsForInvalidPackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", INVALID_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(INVALID_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));
    }

    @Test
    public void testStartFailsForNonDebuggablePackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(
                        cmd.exec(
                                mService,
                                mIn,
                                mOut,
                                mErr,
                                new String[] {"start", NON_DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(NON_DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService, Mockito.never())
                .startSdkSandbox(
                        Mockito.any(CallingInfo.class),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class));
    }

    @Test
    public void testStartFailsWhenSandboxAlreadyRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(true).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService, Mockito.never())
                .startSdkSandbox(
                        Mockito.any(CallingInfo.class),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class));
    }

    @Test
    public void testStartSucceedsForDebuggablePackageWhenNotAlreadyRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(false).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(0);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService)
                .startSdkSandbox(
                        Mockito.eq(callingInfo),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class));
    }

    @Test
    public void testStartFailsWhenBindingSandboxFails() throws Exception {
        mService.setBindingSuccessful(false);

        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(false).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService)
                .startSdkSandbox(
                        Mockito.eq(callingInfo),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class));
    }

    @Test
    public void testStopFailsForInvalidPackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"stop", INVALID_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(INVALID_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService, Mockito.never())
                .stopSdkSandboxService(Mockito.any(CallingInfo.class), Mockito.anyString());
    }

    @Test
    public void testStopFailsForNonDebuggablePackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(
                        cmd.exec(
                                mService,
                                mIn,
                                mOut,
                                mErr,
                                new String[] {"stop", NON_DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(NON_DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService, Mockito.never())
                .stopSdkSandboxService(Mockito.any(CallingInfo.class), Mockito.anyString());
    }

    @Test
    public void testStopFailsWhenSandboxIsNotRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(false).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"stop", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService, Mockito.never())
                .stopSdkSandboxService(Mockito.any(CallingInfo.class), Mockito.anyString());
    }

    @Test
    public void testStopSucceedsForDebuggablePackageWhenAlreadyRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(true).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"stop", DEBUGGABLE_PACKAGE}))
                .isEqualTo(0);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService)
                .stopSdkSandboxService(callingInfo, "Shell command 'sdk_sandbox stop' issued");
    }

    private static class ShellInjector extends SdkSandboxShellCommand.Injector {

        @Override
        int getCallingUid() {
            return Process.SHELL_UID;
        }
    }

    private static class FakeSdkSandboxManagerService extends SdkSandboxManagerService {

        private boolean mBindingSuccessful = true;

        FakeSdkSandboxManagerService(Context context, SdkSandboxServiceProvider provider) {
            super(context, provider);
        }

        @Override
        void startSdkSandbox(CallingInfo callingInfo, SandboxBindingCallback callback) {
            if (mBindingSuccessful) {
                callback.onBindingSuccessful(Mockito.mock(ISdkSandboxService.class));
            } else {
                callback.onBindingFailed();
            }
        }

        @Override
        void stopSdkSandboxService(CallingInfo callingInfo, String reason) {}

        @Override
        boolean isSdkSandboxServiceRunning(CallingInfo callingInfo) {
            // Must be mocked in the tests accordingly
            throw new RuntimeException();
        }

        private void setBindingSuccessful(boolean successful) {
            mBindingSuccessful = successful;
        }
    }
}
