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

package com.android.server.sdksandbox;

import android.annotation.Nullable;
import android.content.ServiceConnection;

import com.android.sdksandbox.ISdkSandboxService;

import java.io.PrintWriter;

/**
 * Interface to get hold of SdkSandbox service
 *
 * @hide
 */
public interface SdkSandboxServiceProvider {
    /**
     * Bind to and establish a connection with SdkSandbox service.
     * @param callingInfo represents the calling app.
     * @param serviceConnection recieves information when service is started and stopped.
     */
    void bindService(CallingInfo callingInfo, ServiceConnection serviceConnection);

    /**
     * Unbind the SdkSandbox service associated with the app.
     *
     * @param callingInfo represents the app for which the sandbox should be unbound.
     * @param shouldForgetConnection set to false if there is a possibility of still interacting
     *     with the sandbox, and true otherwise for e.g. if the sandbox has been unbound when the
     *     app goes to the background, but sandbox APIs could still be used.
     */
    void unbindService(CallingInfo callingInfo, boolean shouldForgetConnection);

    /**
     * Return bound {@link ISdkSandboxService} connected for {@code callingInfo} or otherwise
     * {@code null}.
    */
    @Nullable
    ISdkSandboxService getBoundServiceForApp(CallingInfo callingInfo);

    /**
     * Set bound SdkSandbox service for {@code callingInfo}.
     */
    void setBoundServiceForApp(CallingInfo callingInfo, @Nullable ISdkSandboxService service);

    /** Dump debug information for adb shell dumpsys */
    default void dump(PrintWriter writer) {
    }
}
