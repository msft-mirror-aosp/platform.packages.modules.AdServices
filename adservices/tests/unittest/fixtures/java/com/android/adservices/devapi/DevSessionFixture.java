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

package com.android.adservices.devapi;

import com.android.adservices.service.devapi.DevSession;
import com.android.adservices.service.devapi.DevSessionState;

/**
 * A helper class to generate {@link DevSession} instances with different states and expiry times
 * for testing purposes.
 */
public final class DevSessionFixture {
    // Pre-defined DevSession instances with different states and expiry times.
    public static final DevSession IN_DEV = create(DevSessionState.IN_DEV);
    public static final DevSession IN_PROD = create(DevSessionState.IN_PROD);
    public static final DevSession TRANSITIONING_DEV_TO_PROD =
            create(DevSessionState.TRANSITIONING_DEV_TO_PROD);
    public static final DevSession TRANSITIONING_PROD_TO_DEV =
            create(DevSessionState.TRANSITIONING_PROD_TO_DEV);

    private DevSessionFixture() {}

    /**
     * Creates a {@link DevSession} instance with the specified state.
     *
     * @param state The desired state of the DevSession.
     */
    private static DevSession create(DevSessionState state) {
        return DevSession.builder().setState(state).build();
    }
}
