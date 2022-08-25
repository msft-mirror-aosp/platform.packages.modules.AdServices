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

package com.android.sdksandbox;

import android.annotation.NonNull;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.SdkSandboxController;

final class SdkSandboxControllerImpl implements SdkSandboxController {

    private final String mClientPackageName;
    private final ISdkToServiceCallback mManagerService;

    // TODO(b/240671642): Add a CTS test setup for sdk-sandbox link
    SdkSandboxControllerImpl(
            @NonNull String clientPackageName, @NonNull ISdkToServiceCallback callback) {
        mClientPackageName = clientPackageName;
        mManagerService = callback;
    }
}
