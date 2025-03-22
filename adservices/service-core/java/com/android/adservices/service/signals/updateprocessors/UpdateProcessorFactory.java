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

package com.android.adservices.service.signals.updateprocessors;

import static com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;

/** Base class for {@link UpdateProcessor} factories. */
public abstract class UpdateProcessorFactory {
    protected static final String UNSUPPORTED_VERSION_ERROR_MESSAGE =
            "Unsupported %s update schema version %d, valid versions are: %s";

    /**
     * Gets an {@link UpdateProcessor} compatible with the given update schema version.
     *
     * @param version The version.
     * @return A version-compatible {@link UpdateProcessor}.
     */
    public abstract UpdateProcessor getUpdateProcessor(@UpdateSchemaVersion int version);
}
