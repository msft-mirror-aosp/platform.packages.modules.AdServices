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

package com.android.adservices.service.signals.updateprocessors.updateencoder;

import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;

/**
 * Updates the encoder for a buyer based on updateSignals call The value for this is a JSON object
 * with key "update_encoder"
 *
 * <p>Inside the JSON object the buyer need to provide a valid action from supported choices. The
 * action for update is provided with the key "action" and so far we support "DELETE" & "REGISTER"
 * actions. In case of "REGISTER" the Uri for update is provided in the key "endpoint"
 */
public abstract class UpdateEncoder implements UpdateProcessor {
    protected static final String UPDATE_ENCODER = "update_encoder";

    /**
     * @return name for this {@link UpdateProcessor}
     */
    @Override
    public String getName() {
        return UPDATE_ENCODER;
    }
}
