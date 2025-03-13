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

package com.android.adservices.service.signals.updateprocessors.remove;

import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;

/**
 * Removes the signal for a key.
 *
 * <p>The value of this is a list of base 64 strings corresponding to the keys of the signals that
 * should be deleted.
 */
public abstract class Remove implements UpdateProcessor {
    protected static final String REMOVE = "remove";

    @Override
    public String getName() {
        return REMOVE;
    }
}
