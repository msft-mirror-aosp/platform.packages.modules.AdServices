/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.adservices.exceptions;

import android.adservices.measurement.MeasurementManager.ResultCode;
import android.annotation.NonNull;

/** Exception thrown by Measurement API. */
public class MeasurementException extends AdServicesException {

    @ResultCode private final int mResultCode;

    /**
     * Initializes an {@link MeasurementException} with no message.
     *
     * @param resultCode The resultCode.
     */
    public MeasurementException(@ResultCode int resultCode) {
        super(null, null);
        mResultCode = resultCode;
    }

    /**
     * Initializes an {@link MeasurementException} with a result code and message.
     *
     * @param resultCode The resultCode.
     * @param message The detail message (which is saved for later retrieval by the {@link
     *     #getMessage()} method).
     */
    public MeasurementException(@ResultCode int resultCode, @NonNull String message) {
        super(message, null);
        mResultCode = resultCode;
    }

    /**
     * Initializes an {@link MeasurementException} with a result code, message and cause.
     *
     * @param resultCode The resultCode.
     * @param message The detail message (which is saved for later retrieval by the {@link
     *     #getMessage()} method).
     * @param cause The cause (which is saved for later retrieval by the {@link #getCause()}
     *     method). (A null value is permitted, and indicates that the cause is nonexistent or
     *     unknown.)
     */
    public MeasurementException(
            @ResultCode int resultCode, @NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
        mResultCode = resultCode;
    }

    /**
     * Returns the result code this exception was constructed with.
     *
     * @return The resultCode.
     */
    @ResultCode
    public int getResultCode() {
        return mResultCode;
    }
}
