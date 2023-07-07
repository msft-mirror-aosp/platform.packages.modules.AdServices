// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package android.app.sdksandbox.interfaces;

import android.app.sdksandbox.interfaces.IActivityStarter;
import android.os.Bundle;

interface ISdkApi {
    ParcelFileDescriptor getFileDescriptor(String inputValue);
    String parseFileDescriptor(in ParcelFileDescriptor pFd);
    String createFile(int sizeInMb);
    String getSyncedSharedPreferencesString(String key);
    // Representative method for SDK-SDK communication. This can be any method called by other
    // sdks for example, to loadAd for a given adDetail
    String getMessage();
    void startActivity(IActivityStarter callback, in Bundle params);
    String getSandboxDump();
    boolean isCustomizedSdkContextEnabled();
}
