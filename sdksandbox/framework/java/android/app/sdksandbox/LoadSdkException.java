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

package android.app.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;

/** Exception thrown by {@link SdkSandboxManager#loadSdk} */
public final class LoadSdkException extends Exception {

    private final @SdkSandboxManager.LoadSdkErrorCode int mLoadSdkErrorCode;
    private final Bundle mExtraInformation;

    /**
     * Initializes a {@link LoadSdkException} with a result code and a message
     *
     * @param loadSdkErrorCode The result code.
     * @param message The detailed message which is saved for later retrieval by the {@link
     *     #getMessage()} method.
     */
    public LoadSdkException(
            @SdkSandboxManager.LoadSdkErrorCode int loadSdkErrorCode, @Nullable String message) {
        this(loadSdkErrorCode, message, /*cause=*/ null);
    }

    /**
     * Initializes a {@link LoadSdkException} with a result code, a message and a cause.
     *
     * @param loadSdkErrorCode The result code.
     * @param message The detailed message which is saved for later retrieval by the {@link
     *     #getMessage()} method.
     * @param cause The cause of the exception, which is saved for later retrieval by the {@link
     *     #getCause()} method. A null value is permitted, and indicates that the cause is
     *     nonexistent or unknown.
     */
    public LoadSdkException(
            @SdkSandboxManager.LoadSdkErrorCode int loadSdkErrorCode,
            @Nullable String message,
            @Nullable Throwable cause) {
        this(loadSdkErrorCode, message, cause, new Bundle());
    }

    /**
     * Initializes a {@link LoadSdkException} with a result code, a message, a cause and extra
     * information.
     *
     * @param loadSdkErrorCode The result code.
     * @param message The detailed message which is saved for later retrieval by the {@link
     *     #getMessage()} method.
     * @param cause The cause of the exception, which is saved for later retrieval by the {@link
     *     #getCause()} method. A null value is permitted, and indicates that the cause is
     *     nonexistent or unknown.
     * @param extraInfo Extra error information. This is empty if there is no such information.
     */
    public LoadSdkException(
            @SdkSandboxManager.LoadSdkErrorCode int loadSdkErrorCode,
            @Nullable String message,
            @Nullable Throwable cause,
            @NonNull Bundle extraInfo) {
        super(message, cause);
        mLoadSdkErrorCode = loadSdkErrorCode;
        mExtraInformation = extraInfo;
    }
    /**
     * Returns the result code this exception was constructed with.
     *
     * @return The loadSdk result code.
     */
    public @SdkSandboxManager.LoadSdkErrorCode int getLoadSdkErrorCode() {
        return mLoadSdkErrorCode;
    }

    /**
     * Returns the extra error information this exception was constructed with.
     *
     * @return The extra error information Bundle.
     */
    @NonNull
    public Bundle getExtraErrorInformation() {
        return mExtraInformation;
    }
}
