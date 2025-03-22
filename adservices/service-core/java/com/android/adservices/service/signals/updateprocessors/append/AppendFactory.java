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

package com.android.adservices.service.signals.updateprocessors.append;

import static com.android.adservices.service.signals.updateprocessors.append.Append.APPEND;

import com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorFactory;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Locale;

/** Factory class for the {@link Append} update processor. */
public class AppendFactory extends UpdateProcessorFactory {
    private static final List<Integer> SUPPORTED_VERSIONS =
            ImmutableList.of(UpdateSchemaVersion.V0);

    @Override
    public Append getUpdateProcessor(@UpdateSchemaVersion int version) {
        return switch (version) {
            case UpdateSchemaVersion.V0 -> new AppendV0();
            default ->
                    throw new IllegalArgumentException(
                            String.format(
                                    Locale.ENGLISH,
                                    UNSUPPORTED_VERSION_ERROR_MESSAGE,
                                    APPEND,
                                    version,
                                    SUPPORTED_VERSIONS));
        };
    }
}
