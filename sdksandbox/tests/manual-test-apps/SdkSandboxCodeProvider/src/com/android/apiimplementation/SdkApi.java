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

package com.android.apiimplementation;

import android.app.sdksandbox.SdkSandboxController;
import android.app.sdksandbox.interfaces.ISdkApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SdkApi extends ISdkApi.Stub {
    private final Context mContext;

    public SdkApi(Context sdkContext) {
        mContext = sdkContext;
    }

    @Override
    public String createFile(int sizeInMb) throws RemoteException {
        try {
            final Path path = Paths.get(mContext.getDataDir().getPath(), "file.txt");
            Files.deleteIfExists(path);
            Files.createFile(path);
            final byte[] buffer = new byte[sizeInMb * 1024 * 1024];
            Files.write(path, buffer);

            final File file = new File(path.toString());
            final long actualFilzeSize = file.length() / (1024 * 1024);
            return "Created " + actualFilzeSize + " MB file successfully";
        } catch (IOException e) {
            throw new RemoteException(e);
        }
    }

    @Override
    public String getMessage() {
        return "Message Received from a sandboxedSDK";
    }

    @Override
    public String getSyncedSharedPreferencesString(String key) {
        return getClientSharedPreferences().getString(key, "");
    }

    private SharedPreferences getClientSharedPreferences() {
        return mContext.getSystemService(SdkSandboxController.class).getClientSharedPreferences();
    }
}
