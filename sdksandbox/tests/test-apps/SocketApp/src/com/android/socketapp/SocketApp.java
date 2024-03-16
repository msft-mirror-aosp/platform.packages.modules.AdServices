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

import android.app.Application;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.io.IOException;

public class SocketApp extends Application {

    private static final String SOCKET_NAME = "SocketApp";
    private static final String TAG = "SocketApp";

    @Override
    public void onCreate() {
        super.onCreate();

        new Thread(this::connect).start();
    }

    private void connect() {
        Log.i(TAG, "waiting connections from app");
        try (LocalServerSocket serverSocket = new LocalServerSocket(SOCKET_NAME);
                LocalSocket localSocket = serverSocket.accept()) {
            // Read and send back.
            localSocket.getOutputStream().write(localSocket.getInputStream().read());
        } catch (IOException e) {
            Log.e(TAG, "Socket error", e);
        }
    }
}
