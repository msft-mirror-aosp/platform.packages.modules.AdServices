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

package com.android.adservices.service.shell;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

public class CustomAudienceShellCommandFactory implements ShellCommandFactory {

    static ShellCommandFactory getInstance() {
        return new CustomAudienceShellCommandFactory();
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        switch (cmd) {
            case CustomAudienceListCommand.CMD:
                return new CustomAudienceListCommand();
            case CustomAudienceViewCommand.CMD:
                return new CustomAudienceViewCommand();
            default:
                return null;
        }
    }

    @Override
    public ImmutableSet<String> getAllCommands() {
        return ImmutableSet.of(CustomAudienceListCommand.CMD, CustomAudienceViewCommand.CMD);
    }
}