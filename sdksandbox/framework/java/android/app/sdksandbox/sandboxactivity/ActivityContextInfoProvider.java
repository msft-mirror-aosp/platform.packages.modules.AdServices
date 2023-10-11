/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.sdksandbox.sandboxactivity;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityRegistry;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;

/**
 * A singleton class to provide instances of {@link ActivityContextInfo} to the callers.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public abstract class ActivityContextInfoProvider {
    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static ActivityContextInfoProvider sInstance;

    private ActivityContextInfoProvider() {}

    /** Returns a Single instance of this class. */
    @NonNull
    public static ActivityContextInfoProvider getInstance() {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new ActivityContextInfoProviderImpl();
            }
            return sInstance;
        }
    }

    /**
     * Returns {@link ActivityContextInfo} instance containing the information which is needed to
     * build the sandbox activity {@link android.content.Context} for the passed {@link Intent}.
     *
     * @param intent an {@link Intent} for a sandbox {@link android.app.Activity} containing
     *     information to identify the SDK which requested the activity.
     * @return {@link ActivityContextInfoProvider} instance.
     * @throws IllegalArgumentException if the intent doesn't refer to a registered {@link
     *     android.app.sdksandbox.sdkprovider.SdkSandboxActivityHandler}
     * @throws IllegalStateException if Customized SDK Context flag is not enabled
     */
    @NonNull
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public ActivityContextInfo getActivityContextInfo(@NonNull Intent intent) {
        SdkSandboxActivityRegistry registry = SdkSandboxActivityRegistry.getInstance();
        ActivityContextInfo contextInfo = registry.getContextInfo(intent);
        if (contextInfo == null) {
            throw new IllegalArgumentException(
                    "There is no registered SdkSandboxActivityHandler "
                            + "for the passed intent, "
                            + intent);
        }
        return contextInfo;
    }

    private static class ActivityContextInfoProviderImpl extends ActivityContextInfoProvider {}
}