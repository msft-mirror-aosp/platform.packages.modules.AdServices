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

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.IOException;

/* This Provider is used to test sockets to and from SDK sandbox processes. */
public class SocketSdkProvider extends SandboxedSdkProvider {

    private static final String SOCKET_NAME = "SocketSdkProvider";
    private static final String TAG = "SocketSdkProvider";

    @Override
    public final SandboxedSdk onLoadSdk(Bundle params) {
        new Thread(this::connect).start();

        return new SandboxedSdk(new Binder());
    }

    @Override
    public final View getView(Context windowContext, Bundle params, int width, int height) {
        return null;
    }

    private void connect() {
        Log.i(TAG, "waiting connections from sandbox");
        try (LocalServerSocket serverSocket = new LocalServerSocket(SOCKET_NAME);
                LocalSocket localSocket = serverSocket.accept()) {
            throw new IOException("This should not happen");
        } catch (IOException e) {
            throw new RuntimeException("Crashing test sandbox", e);
        }
    }
}
