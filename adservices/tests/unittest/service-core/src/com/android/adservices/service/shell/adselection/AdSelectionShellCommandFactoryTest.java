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

package com.android.adservices.service.shell.adselection;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;

import com.google.common.collect.Sets;
import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AdSelectionShellCommandFactoryTest extends AdServicesMockitoTestCase {
    private static final boolean CONSENTED_DEBUGGING_CLI_ENABLED = true;
    @Mock private ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;
    @Mock private BuyerInputGenerator mBuyerInputGenerator;

    private ShellCommandFactory mFactory;

    @Before
    public void setup() {
        mFactory =
                new AdSelectionShellCommandFactory(
                        CONSENTED_DEBUGGING_CLI_ENABLED,
                        true,
                        mConsentedDebugConfigurationDao,
                        mBuyerInputGenerator);
    }

    @Test
    public void test_consentedDebugCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(ConsentedDebugShellCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(ConsentedDebugShellCommand.class);
    }

    @Test
    public void test_GetAdSelectionDataCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(GetAdSelectionDataCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(GetAdSelectionDataCommand.class);
    }

    @Test
    public void test_consentedDebugCmdDisabled() {
        mFactory =
                new AdSelectionShellCommandFactory(
                        false, true, mConsentedDebugConfigurationDao, mBuyerInputGenerator);
        ShellCommand shellCommand = mFactory.getShellCommand(ConsentedDebugShellCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void test_GetAdSelectionDataCmdDisabled() {
        mFactory =
                new AdSelectionShellCommandFactory(
                        false, false, mConsentedDebugConfigurationDao, mBuyerInputGenerator);
        ShellCommand shellCommand = mFactory.getShellCommand(GetAdSelectionDataCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void test_invalidCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_nullCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(null);
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_emptyCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_invalidCmd_consentedDebugCliDisabled() {
        mFactory =
                new AdSelectionShellCommandFactory(
                        false, true, mConsentedDebugConfigurationDao, mBuyerInputGenerator);
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_getAllCommandsHelp() {
        mFactory =
                new AdSelectionShellCommandFactory(
                        CONSENTED_DEBUGGING_CLI_ENABLED,
                        true,
                        mConsentedDebugConfigurationDao,
                        mBuyerInputGenerator);

        Truth.assertThat(Sets.newHashSet(mFactory.getAllCommandsHelp()))
                .containsExactlyElementsIn(
                        Sets.newHashSet(
                                ConsentedDebugShellCommand.HELP, GetAdSelectionDataCommand.HELP));
    }
}
