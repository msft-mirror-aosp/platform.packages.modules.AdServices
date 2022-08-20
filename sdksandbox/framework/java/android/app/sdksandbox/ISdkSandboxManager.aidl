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
import android.app.sdksandbox.ISdkSandboxLifecycleCallback;
import android.app.sdksandbox.ISendDataCallback;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.content.pm.SharedLibraryInfo;

/** @hide */
interface ISdkSandboxManager {
    /**
    * List of methods for which latencies are logged with logLatencyFromSystemServerToApp
    */
    const String REQUEST_SURFACE_PACKAGE = "REQUEST_SURFACE_PACKAGE";

    void addSdkSandboxLifecycleCallback(in String callingPackageName, in ISdkSandboxLifecycleCallback callback);
    void removeSdkSandboxLifecycleCallback(in String callingPackageName, in ISdkSandboxLifecycleCallback callback);
    void loadSdk(in String callingPackageName, in String sdkName, long timeAppCalledSystemServer, in Bundle params, in ILoadSdkCallback callback);
    void unloadSdk(in String callingPackageName, in String sdkName, long timeAppCalledSystemServer);
    // TODO(b/242031240): wrap the many input params in one parcelable object
    void requestSurfacePackage(in String callingPackageName, in String sdkName, in IBinder hostToken, int displayId, int width, int height, long timeAppCalledSystemServer, in Bundle params, IRequestSurfacePackageCallback callback);
    void sendData(in String callingPackageName, in String sdkName, in Bundle data, in ISendDataCallback callback);
    List<SharedLibraryInfo> getLoadedSdkLibrariesInfo(in String callingPackageName, long timeAppCalledSystemServer);
    void syncDataFromClient(in String callingPackageName, long timeAppCalledSystemServer, in SharedPreferencesUpdate update);
    void stopSdkSandbox(in String callingPackageName);
    void logLatencyFromSystemServerToApp(in String method, int latency);
}
