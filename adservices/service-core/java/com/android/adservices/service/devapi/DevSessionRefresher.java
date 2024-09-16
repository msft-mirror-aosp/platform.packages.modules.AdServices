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

import java.util.concurrent.Future;

/** Provides functionality to reset the AdServices DB. */
public interface DevSessionRefresher {
    /**
     * Reset the current state of the AdServices database to a fresh state.
     *
     * @param setDevSessionEnabled Specifies if development session state should be set to true or
     *     false. In both cases, this method is called to do the actual reset operation.
     * @return A future containing the success or failure of this operation.
     * @throws IllegalStateException If already in developer mode when trying to enable it.
     */
    Future<Boolean> reset(boolean setDevSessionEnabled) throws IllegalStateException;
}
