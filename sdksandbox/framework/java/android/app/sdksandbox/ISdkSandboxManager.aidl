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

package android.app.sdksandbox;

import android.os.Bundle;
import android.os.IBinder;

import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.IRequestSurfacePackageCallback;
import android.app.sdksandbox.ISendDataCallback;
import android.content.pm.SharedLibraryInfo;

/** @hide */
interface ISdkSandboxManager {
    void loadSdk(in String callingPackageName, in String sdkName, in Bundle params, in ILoadSdkCallback callback);
    void unloadSdk(in String callingPackageName, in String sdkName);
    void requestSurfacePackage(in String callingPackageName, in String sdkName, in IBinder hostToken, int displayId, in int width, in int height, in Bundle params, IRequestSurfacePackageCallback callback);
    void sendData(in String callingPackageName, in String sdkName, in Bundle data, in ISendDataCallback callback);
    List<SharedLibraryInfo> getLoadedSdkLibrariesInfo(in String callingPackageName);
}
