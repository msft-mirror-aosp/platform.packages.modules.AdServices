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

package com.android.adservices.shell;

import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.shell.AdServicesShellCommandService.TAG;

import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.LogEntry.Level;
import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;

@SpyStatic(FlagsFactory.class)
public final class AdServicesShellCommandServiceTest extends AdServicesExtendedMockitoTestCase {

    private final AdServicesShellCommandService mShellService = new AdServicesShellCommandService();

    @Mock private Flags mMockFlags;

    // TODO(b/308009734): Move this to separate constants class as this will also be used by the
    //  AdServicesShellCommandHelper
    private static final String ACTION_SHELL_COMMAND_SERVICE =
            "android.adservices.SHELL_COMMAND_SERVICE";

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(mMockFlags);
        mockGetAdServicesShellCommandEnabled(/* enabled= */ true);
    }

    @Test
    public void testOnBindShellCommandService_flagEnabled() {
        mShellService.onCreate();
        IBinder binder = mShellService.onBind(getIntentForShellCommandService());

        expect.withMessage("onBind()").that(binder).isNotNull();
    }

    @Test
    public void testOnBindShellCommandService_flagDisabled() {
        mockGetAdServicesShellCommandEnabled(/* enabled= */ false);
        mShellService.onCreate();
        IBinder binder = mShellService.onBind(getIntentForShellCommandService());

        expect.withMessage("onBind()").that(binder).isNull();
    }

    @Test
    public void testDump_shellCommandThroughDumpsys_flagEnabled() throws Exception {
        String[] args = new String[] {"cmd", "echo", "hello"};

        expect.withMessage("cmd: echo")
                .that(dump(pw -> mShellService.dump(/* fd= */ null, pw, args)))
                .isEqualTo("hello\n");
    }

    @Test
    @SpyStatic(Log.class)
    public void testDump_shellCommandThroughDumpsys_flagDisabled() throws Exception {
        mockGetAdServicesShellCommandEnabled(/* enabled= */ false);
        String[] args = new String[] {"cmd", "echo", "hello"};
        LogInterceptor logInterceptor = extendedMockito.interceptLogE(TAG);

        expect.withMessage("cmd: echo")
                .that(dump(pw -> mShellService.dump(/* fd= */ null, pw, args)))
                .isEmpty();
        expect.withMessage("Log.e() calls to tag %s", TAG)
                .that(logInterceptor.getPlainMessages(TAG, Level.ERROR))
                .contains(
                        String.format(
                                "dump(%s) called on AdServicesShellCommandService when shell"
                                        + " command flag was disabled",
                                Arrays.toString(args)));
    }

    private Intent getIntentForShellCommandService() {
        return new Intent(ACTION_SHELL_COMMAND_SERVICE);
    }

    private void mockGetAdServicesShellCommandEnabled(boolean enabled) {
        when(mMockFlags.getAdServicesShellCommandEnabled()).thenReturn(enabled);
    }
}
