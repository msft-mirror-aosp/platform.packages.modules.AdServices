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

package com.android.adservices.mockito;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * Helper interface providing common expectations for "regular" (non static or final) methods on
 * Android SDK.
 */
public interface AndroidMocker {

    /**
     * Mocks the result to calls to {@link
     * PackageManager#queryIntentServices(android.content.Intent, int)} or {@link
     * PackageManager#queryIntentServices(android.content.Intent,
     * android.content.pm.PackageManager.ResolveInfoFlags)}, passing any argument.
     */
    void mockQueryIntentService(PackageManager mockPm, ResolveInfo... resolveInfos);

    /** Mocks a call to {@link Context#getApplicationContext()}. */
    void mockGetApplicationContext(Context mockContext, @Nullable Context appContext);
}
