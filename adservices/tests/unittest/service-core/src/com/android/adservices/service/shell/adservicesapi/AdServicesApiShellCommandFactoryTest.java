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

package com.android.adservices.service.shell.adservicesapi;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class AdServicesApiShellCommandFactoryTest extends AdServicesMockitoTestCase {

    private ShellCommandFactory mFactory;
    @Mock DevSessionController mDevSessionSetter;

    @Before
    public void setup() {
        mFactory =
                new AdServicesApiShellCommandFactory(
                        mDevSessionSetter, /* developerModeFeatureEnabled= */ true);
    }

    @Test
    public void test_enableAdServicesCmd() {
        ShellCommand shellCommand =
                mFactory.getShellCommand(EnableAdServicesCommand.CMD_ENABLE_ADSERVICES);
        assertThat(shellCommand).isInstanceOf(EnableAdServicesCommand.class);
    }

    @Test
    public void test_invalidCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        assertThat(shellCommand).isNull();
    }

    @Test
    public void test_nullCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(null);
        assertThat(shellCommand).isNull();
    }

    @Test
    public void test_emptyCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("");
        assertThat(shellCommand).isNull();
    }

    @Test
    public void test_devSessionCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(DevSessionCommand.CMD);

        assertThat(shellCommand).isInstanceOf(DevSessionCommand.class);
    }

    @Test
    public void test_devSessionDisabledCmd() {
        AdServicesApiShellCommandFactory factory =
                new AdServicesApiShellCommandFactory(
                        mDevSessionSetter, /* developerModeFeatureEnabled= */ false);

        ShellCommand shellCommand = factory.getShellCommand(DevSessionCommand.CMD);

        assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }
}
