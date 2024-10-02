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

import com.google.common.util.concurrent.ListenableFuture;

/** Provides functionality to reset the AdServices DB. */
public interface DevSessionController {
    /**
     * Begins a developer session and returns to {@link DevSessionState#IN_PROD}. Clears the
     * adservices database during the transition.
     *
     * @return A {@link DevSessionControllerResult} containing the status of the operation. See that
     *     enum for more info on each result code's meaning.
     * @throws IllegalStateException If the current {@link DevSession} is already in {@link
     *     DevSessionState#IN_DEV} when trying to move to that state.
     */
    // TODO(b/370948289): Return NO_OP instead of throwing ISE.
    ListenableFuture<DevSessionControllerResult> startDevSession();

    /**
     * Ends a developer session and returns to {@link DevSessionState#IN_PROD}. Clears the
     * adservices database during the transition.
     *
     * @return A {@link DevSessionControllerResult} containing the status of the operation. See that
     *     enum for more info on each result code's meaning.
     * @throws IllegalStateException If the current {@link DevSession} is already in {@link
     *     DevSessionState#IN_PROD} when trying to move to that state.
     */
    // TODO(b/370948289): Return NO_OP instead of throwing ISE.
    ListenableFuture<DevSessionControllerResult> endDevSession();
}
