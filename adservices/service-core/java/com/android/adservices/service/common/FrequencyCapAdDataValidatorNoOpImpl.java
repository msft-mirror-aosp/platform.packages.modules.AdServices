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

package com.android.adservices.service.common;

import android.adservices.common.AdData;
import android.annotation.NonNull;

import com.google.common.collect.ImmutableCollection;

import java.util.Objects;

/**
 * No-op implementation of the {@link FrequencyCapAdDataValidator}, used when ad filtering is
 * disabled.
 */
public class FrequencyCapAdDataValidatorNoOpImpl implements FrequencyCapAdDataValidator {
    public FrequencyCapAdDataValidatorNoOpImpl() {}

    @Override
    public void addValidation(
            @NonNull AdData adData, @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(adData);
        Objects.requireNonNull(violations);
        // No-op
    }
}
