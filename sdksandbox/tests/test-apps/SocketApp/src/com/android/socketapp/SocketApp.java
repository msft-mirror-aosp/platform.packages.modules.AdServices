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

package com.android.socketapp;

import android.app.Activity;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

public class SocketApp extends Activity {

    private static final String SOCKET_NAME = "SocketApp";
    private static final String TAG = "SocketApp";
    private static final String SDK_NAME = "com.android.socketsdkprovider";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        new Thread(this::connect).start();

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        getApplicationContext()
                .getSystemService(SdkSandboxManager.class)
                .loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
    }

    private void connect() {
        Log.i(TAG, "waiting connections from app");
        try (LocalServerSocket serverSocket = new LocalServerSocket(SOCKET_NAME);
                LocalSocket localSocket = serverSocket.accept()) {
            throw new IOException("This should not happen");
        } catch (IOException e) {
            throw new RuntimeException("Crashing test app", e);
        }
    }
}
