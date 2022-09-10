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

import static android.app.sdksandbox.SdkSandboxController.SDK_SANDBOX_CONTROLLER_SERVICE;

import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;

/**
 * Controller that is used by SDK loaded in the sandbox to access information provided by the sdk
 * sandbox.
 *
 * <p>It enables the SDK to communicate with other SDKS in the SDK sandbox and know about the state
 * of the sdks that are currently loaded in it.
 *
 * <p>An instance of {@link SdkSandboxController} can be obtained using {@link
 * Context#getSystemService} and {@link SdkSandboxController class}. The {@link Context} can in turn
 * be obtained using {@link android.app.sdksandbox.SandboxedSdkProvider#getContext()}.
 *
 * @hide
 */
@SystemService(SDK_SANDBOX_CONTROLLER_SERVICE)
public class SdkSandboxController {
    public static final String SDK_SANDBOX_CONTROLLER_SERVICE = "sdk_sandbox_controller_service";

    private Context mContext;

    /** Create SdkSandboxController.* */
    public SdkSandboxController(@NonNull Context context) {

        // When SdkSandboxController is initiated from inside the sdk sandbox process, its private
        // members will be immediately rewritten by the initialize method.
        initialize(context);
    }

    /**
     * Initializes {@link SdkSandboxController} with the given {@code context}.
     *
     * <p>This method is called by the {@link SandboxedSdkContext} to propagate the correct context.
     * For more information check the javadoc on the {@link
     * android.app.sdksandbox.SdkSandboxSystemServiceRegistry}.
     *
     * @hide
     * @see android.app.sdksandbox.SdkSandboxSystemServiceRegistry
     */
    public SdkSandboxController initialize(@NonNull Context context) {
        mContext = context;
        return this;
    }
}
