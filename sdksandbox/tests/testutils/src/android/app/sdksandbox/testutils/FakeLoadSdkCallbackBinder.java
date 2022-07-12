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

package android.app.sdksandbox.testutils;

import android.app.sdksandbox.ILoadSdkCallback;
import android.os.Bundle;

public class FakeLoadSdkCallbackBinder extends ILoadSdkCallback.Stub {
    private final FakeLoadSdkCallback mFakeLoadSdkCallback;

    public FakeLoadSdkCallbackBinder(FakeLoadSdkCallback fakeLoadSdkCallback) {
        mFakeLoadSdkCallback = fakeLoadSdkCallback;
    }

    public FakeLoadSdkCallbackBinder() {
        this(new FakeLoadSdkCallback());
    }

    @Override
    public void onLoadSdkSuccess(Bundle params) {
        mFakeLoadSdkCallback.onLoadSdkSuccess(params);
    }

    @Override
    public void onLoadSdkFailure(int errorCode, String errorMsg) {
        mFakeLoadSdkCallback.onLoadSdkFailure(errorCode, errorMsg);
    }

    public boolean isLoadSdkSuccessful() {
        return mFakeLoadSdkCallback.isLoadSdkSuccessful();
    }

    public int getLoadSdkErrorCode() {
        return mFakeLoadSdkCallback.getLoadSdkErrorCode();
    }

    public String getLoadSdkErrorMsg() {
        return mFakeLoadSdkCallback.getLoadSdkErrorMsg();
    }
}
