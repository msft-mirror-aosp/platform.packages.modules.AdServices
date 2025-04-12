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

package com.android.adservices.service.signals.updateprocessors.updateproperties;

import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;

/**
 * Updates properties for one or more existing signals.
 *
 * <p>The value for this is a JSON object where the JSON keys are base 64 strings corresponding to
 * the signal key to update, and the values are objects with one or more fields corresponding to
 * signal properties.
 *
 * <p>Each signal property maps the name of the property (e.g., <code> "eviction_priority"</code> to
 * its new value. Signal properties are maintained per-key, and updated values will be set for all
 * signals under the given key.
 *
 * <p>If an update is provided for a key not already present on the device, it will be ignored and
 * skipped.
 */
public abstract class UpdateProperties implements UpdateProcessor {
    public static final String UPDATE_PROPERTIES = "update_properties";
}
