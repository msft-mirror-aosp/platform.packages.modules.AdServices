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

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED;

import android.util.Log;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.common.annotations.SetFlagEnabled;
import com.android.compatibility.common.util.ShellUtils;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

@SetFlagEnabled(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED)
@RequiresSdkLevelAtLeastT
public class ConsentedDebugShellCommandCtsTest extends AdServicesCtsTestCase {

    private static final String TAG = "AdServicesShellCmd";
    private static final String SHELL_COMMAND_PREFIX =
            "cmd adservices_manager ad_selection consented_debug ";
    private static final String ENABLE_SHELL_COMMAND_TEMPLATE =
            SHELL_COMMAND_PREFIX + "enable --secret_debug_token %s --expires_in_hours %d";
    private static final String DISABLE_SHELL_COMMAND_TEMPLATE = SHELL_COMMAND_PREFIX + "disable";
    private static final String VIEW_SHELL_COMMAND_TEMPLATE = SHELL_COMMAND_PREFIX + "view";

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.forAllApisEnabledTests()
                .setFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED, true)
                .setFlag(KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED, true)
                .setCompatModeFlags();
    }

    @Test
    public void testRun_consentedDebug_happyPath() {
        String debugToken = UUID.randomUUID().toString();
        int expiryInHours = 48;
        Log.d(
                TAG,
                "running cmd: "
                        + String.format(ENABLE_SHELL_COMMAND_TEMPLATE, debugToken, expiryInHours));
        ShellUtils.runShellCommand(ENABLE_SHELL_COMMAND_TEMPLATE, debugToken, expiryInHours);
        Log.d(TAG, "running cmd: " + String.format(VIEW_SHELL_COMMAND_TEMPLATE));
        String viewBeforeDisableResponse = ShellUtils.runShellCommand(VIEW_SHELL_COMMAND_TEMPLATE);
        Log.d(TAG, "viewBeforeDisableResponse: " + viewBeforeDisableResponse);
        Log.d(TAG, "running cmd: " + String.format(DISABLE_SHELL_COMMAND_TEMPLATE));
        ShellUtils.runShellCommand(DISABLE_SHELL_COMMAND_TEMPLATE);
        Log.d(TAG, "running cmd: " + String.format(VIEW_SHELL_COMMAND_TEMPLATE));
        String viewAfterDisableResponse = ShellUtils.runShellCommand(VIEW_SHELL_COMMAND_TEMPLATE);
        Log.d(TAG, "viewAfterDisableResponse: " + viewAfterDisableResponse);

        Truth.assertThat(viewBeforeDisableResponse).contains(debugToken);
        Truth.assertThat(viewAfterDisableResponse).doesNotContain(debugToken);
    }
}
