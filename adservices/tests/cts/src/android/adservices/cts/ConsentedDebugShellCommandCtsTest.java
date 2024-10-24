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

package android.adservices.cts;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED;

import android.util.Log;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED)
public final class ConsentedDebugShellCommandCtsTest extends CtsAdServicesDeviceTestCase {

    private static final String TAG = "AdServicesShellCmd";
    private static final String SHELL_COMMAND_PREFIX = "ad-selection consented-debug ";
    private static final String DISABLE_SHELL_COMMAND_TEMPLATE = SHELL_COMMAND_PREFIX + "disable";
    private static final String VIEW_SHELL_COMMAND_TEMPLATE = SHELL_COMMAND_PREFIX + "view";

    private final AdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testRun_consentedDebug_happyPath() {
        String debugToken = UUID.randomUUID().toString();
        int expiryInHours = 48;
        mAdServicesShellCommandHelper.runCommand(
                "%s enable --secret-debug-token %s --expires-in-hours %d",
                SHELL_COMMAND_PREFIX, debugToken, expiryInHours);
        String viewBeforeDisableResponse =
                mAdServicesShellCommandHelper.runCommand(VIEW_SHELL_COMMAND_TEMPLATE);
        Log.d(TAG, "viewBeforeDisableResponse: " + viewBeforeDisableResponse);

        mAdServicesShellCommandHelper.runCommand(DISABLE_SHELL_COMMAND_TEMPLATE);
        String viewAfterDisableResponse =
                mAdServicesShellCommandHelper.runCommand(VIEW_SHELL_COMMAND_TEMPLATE);
        Log.d(TAG, "viewAfterDisableResponse: " + viewAfterDisableResponse);

        expect.that(viewBeforeDisableResponse).contains(debugToken);
        expect.that(viewAfterDisableResponse).doesNotContain(debugToken);
    }
}
