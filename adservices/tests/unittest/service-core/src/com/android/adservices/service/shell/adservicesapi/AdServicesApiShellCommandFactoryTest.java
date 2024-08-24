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

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;

public class AdServicesApiShellCommandFactoryTest extends AdServicesMockitoTestCase {

    private ShellCommandFactory mFactory;

    @Before
    public void setup() {
        mFactory = new AdServicesApiShellCommandFactory();
    }

    @Test
    public void test_enableAdServicesCmd() {
        ShellCommand shellCommand =
                mFactory.getShellCommand(EnableAdServicesCommand.CMD_ENABLE_ADSERVICES);
        Truth.assertThat(shellCommand).isInstanceOf(EnableAdServicesCommand.class);
    }
}
