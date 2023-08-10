/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.common;

import org.junit.runners.model.InitializationError;

/**
 * Same as {@link GlobalKillSwitchFlipper}, but runs test with the kill switch enabled first.
 *
 * <p>Typically used for debugging purposes only.
 */
public final class GlobalKillSwitchEnabledFirstFlipper extends AbstractGlobalKillSwitchFlipper {

    public GlobalKillSwitchEnabledFirstFlipper(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected int sortMethodsByFlags(boolean flag1, boolean flag2) {
        return flag1 ? -1 : 1;
    }
}
