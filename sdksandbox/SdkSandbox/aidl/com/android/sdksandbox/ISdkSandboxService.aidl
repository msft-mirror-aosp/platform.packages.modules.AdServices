/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.app.sdksandbox.ISharedPreferencesSyncCallback;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.IBinder;
import com.android.sdksandbox.SandboxLatencyInfo;

import com.android.sdksandbox.ILoadSdkInSandboxCallback;

/** @hide */
oneway interface ISdkSandboxService {
    // TODO(b/228045863): Wrap parameters in a parcelable
    void loadSdk(in String callingPackageName, IBinder sdkToken, in ApplicationInfo info,
                  in String sdkName, in String sdkProviderClassName,
                  in String sdkCeDataDir, in String sdkDeDataDir,
                  in Bundle params, in ILoadSdkInSandboxCallback callback,
                  in SandboxLatencyInfo sandboxLatencyInfo);
    void unloadSdk(IBinder sdkToken);
    void syncDataFromClient(in SharedPreferencesUpdate update, in ISharedPreferencesSyncCallback callback);
}
