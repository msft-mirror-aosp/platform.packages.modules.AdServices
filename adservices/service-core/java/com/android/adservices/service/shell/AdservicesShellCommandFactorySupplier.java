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


import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.shell.adselection.AdSelectionShellCommandFactory;
import com.android.adservices.service.shell.customaudience.CustomAudienceShellCommandFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.collect.ImmutableList;

/** Default implementation for {@link ShellCommandFactorySupplier} */
public final class AdservicesShellCommandFactorySupplier extends ShellCommandFactorySupplier {
    private static final ImmutableList<ShellCommandFactory> sDefaultFactories =
            ImmutableList.of(
                    CustomAudienceShellCommandFactory.getInstance(
                            FlagsFactory.getFlags(), ApplicationContextSingleton.get()),
                    AdSelectionShellCommandFactory.getInstance(
                            FlagsFactory.getFlags(), ApplicationContextSingleton.get()));

    @Override
    public ImmutableList<ShellCommandFactory> getAllShellCommandFactories() {
        return sDefaultFactories;
    }
}
