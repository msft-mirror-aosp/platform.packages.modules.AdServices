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

package com.android.adservices.service.devapi;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Represents the different states of a developer session.
 *
 * <p>Note that all changes here must be reflected in {@code dev_session.proto}.
 */
public enum DevSessionState {
    /** No state is known. This could be the value when first reading dev session state. */
    UNKNOWN(0),
    /** We are in production. */
    IN_PROD(1),
    /** We are moving from production to a dev session. */
    TRANSITIONING_PROD_TO_DEV(2),
    /** We are in a dev session. */
    IN_DEV(3),
    /** We are moving from a dev session back to production. */
    TRANSITIONING_DEV_TO_PROD(4);

    private final int mOrdinal;

    DevSessionState(int ordinal) {
        this.mOrdinal = ordinal;
    }

    @VisibleForTesting
    int getOrdinal() {
        return mOrdinal;
    }
}
