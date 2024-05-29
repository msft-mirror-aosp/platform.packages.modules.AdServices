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
package android.sdksandbox.test.scenario.cujclient;

import android.app.Activity;
import android.app.sdksandbox.SdkSandboxManager;
import android.os.Bundle;
import android.widget.RelativeLayout;

public class LoadEmptySdkActivity extends Activity {

    private static final String EMPTY_SDK_NAME = "com.android.emptysdkprovider";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new RelativeLayout(this));

        SdkSandboxManager sdkSandboxManager = getSystemService(SdkSandboxManager.class);
        assert sdkSandboxManager != null;
        sdkSandboxManager.loadSdk(EMPTY_SDK_NAME, Bundle.EMPTY, Runnable::run, result -> {});
    }
}
