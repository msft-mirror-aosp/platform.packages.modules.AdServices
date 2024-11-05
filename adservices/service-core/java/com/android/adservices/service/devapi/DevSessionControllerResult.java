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

/**
 * Result codes for {@link com.android.adservices.service.devapi.DevSessionController} operations.
 */
public enum DevSessionControllerResult {
    /** The operation result is unknown. */
    UNKNOWN,
    /** The operation (either moving to IN_DEV or IN_PROD) was a success. */
    SUCCESS,
    /** The operation (either moving to IN_DEV or IN_PROD) was a failure. */
    FAILURE,
    /** The operation was no-op (e.g. requested IN_DEV while already _DEV), so nothing happened. */
    NO_OP,
}
