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

package com.android.senddatasdkprovider;

import android.app.sdksandbox.LoadSdkResponse;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

public class SdkProvider extends SandboxedSdkProvider {

    @Override
    public LoadSdkResponse onLoadSdk(Bundle params) {
        return new LoadSdkResponse(new Bundle());
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        return null;
    }

    @Override
    public void onDataReceived(Bundle data, DataReceivedCallback callback) {
        if (data.getChar("Success") == 'S') {
            Bundle returnData = new Bundle();
            returnData.putChar("Completed", 'C');
            callback.onDataReceivedSuccess(returnData);
        } else {
            callback.onDataReceivedError("Unable to process data.");
        }
    }
}
