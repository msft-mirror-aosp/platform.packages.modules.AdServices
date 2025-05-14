/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.tests.adspersonalizationstatus;

import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.shell.adservicesapi.SetAdsPersonalizationStatusCommand.CMD_SET_ADS_PERSONALIZATION_STATUS;

import android.util.Log;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.shell.CommandResult;

import org.junit.Test;

@RequiresSdkLevelAtLeastT()
public final class AdsPersonalizationStatusTest extends AdServicesCtsTestCase
        implements AdsPersonalizationStatusTestFlags {
    private static final String ENABLE_ADS_PERSONALIZATION = "enabled";
    private final AdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new AdServicesShellCommandHelper();

    /** Test the set ads personalization api . */
    @Test
    public void testAdsPersonalization() throws Exception {
        String setModuleStateCmdFmt = "%s %s %s";
        CommandResult commandResult =
                mAdServicesShellCommandHelper.runCommandRwe(
                        setModuleStateCmdFmt,
                        COMMAND_PREFIX,
                        CMD_SET_ADS_PERSONALIZATION_STATUS,
                        ENABLE_ADS_PERSONALIZATION);
        Log.i(
                mTag,
                "Invoked set ads personalization status through cli, output from cli:"
                        + commandResult.getOut());
    }
}
