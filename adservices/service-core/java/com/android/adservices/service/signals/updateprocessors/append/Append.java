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

import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;

/**
 * Appends a new signal/signals to a time series of signals, removing the oldest signals to make
 * room for the new ones if the size of the series exceeds the given maximum.
 *
 * <p>The value for this is a JSON object where the JSON keys are base 64 strings corresponding to
 * the signal key to append to and the values are objects with two fields: "values" and
 * "maxSignals". "values" is a list of base 64 strings corresponding to signal values to append to
 * the time series. "maxSignals" is the maximum number of values that are allowed in this
 * timeseries. If the current number of signals associated with the key exceeds maxSignals the
 * oldest signals will be removed. Note that you can append to a key added by put.
 */
public abstract class Append implements UpdateProcessor {
    protected static final String APPEND = "append";

    @Override
    public String getName() {
        return APPEND;
    }
}
