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

import androidx.annotation.NonNull;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * This class allows to inject all dependencies required to create all Shell Command Factories. This
 * should be used for end-to-end test cases.
 */
public class TestShellCommandFactorySupplier extends ShellCommandFactorySupplier {

    @NonNull private final Flags mFlags;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;

    TestShellCommandFactorySupplier(
            @NonNull Flags flags, @NonNull CustomAudienceDao customAudienceDao) {
        mFlags = Objects.requireNonNull(flags, "Flags cannot be null");
        mCustomAudienceDao =
                Objects.requireNonNull(customAudienceDao, "CustomAudienceDao cannot be null");
    }

    @Override
    public ImmutableList<ShellCommandFactory> getAllShellCommandFactories() {
        return ImmutableList.of(
                new CustomAudienceShellCommandFactory(
                        mFlags.getFledgeCustomAudienceCLIEnabledStatus(), mCustomAudienceDao));
    }
}
