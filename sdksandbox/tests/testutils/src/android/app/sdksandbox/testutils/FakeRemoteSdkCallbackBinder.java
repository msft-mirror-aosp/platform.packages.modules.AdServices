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

import android.app.sdksandbox.IRemoteSdkCallback;
import android.os.Bundle;
import android.view.SurfaceControlViewHost;

public class FakeRemoteSdkCallbackBinder extends IRemoteSdkCallback.Stub {
    private final FakeRemoteSdkCallback mFakeRemoteSdkCallback;

    public FakeRemoteSdkCallbackBinder(FakeRemoteSdkCallback fakeRemoteSdkCallback) {
        mFakeRemoteSdkCallback = fakeRemoteSdkCallback;
    }

    public FakeRemoteSdkCallbackBinder() {
        this(new FakeRemoteSdkCallback());
    }

    @Override
    public void onLoadSdkSuccess(Bundle params) {
        mFakeRemoteSdkCallback.onLoadSdkSuccess(params);
    }

    @Override
    public void onLoadSdkFailure(int errorCode, String errorMsg) {
        mFakeRemoteSdkCallback.onLoadSdkFailure(errorCode, errorMsg);
    }

    @Override
    public void onSurfacePackageError(int errorCode, String errorMsg) {
        mFakeRemoteSdkCallback.onSurfacePackageError(errorCode, errorMsg);
    }

    @Override
    public void onSurfacePackageReady(SurfaceControlViewHost.SurfacePackage surfacePackage,
            int surfacePackageId, Bundle params) {
        mFakeRemoteSdkCallback.onSurfacePackageReady(surfacePackage, surfacePackageId, params);
    }

    public boolean isLoadSdkSuccessful() {
        return mFakeRemoteSdkCallback.isLoadSdkSuccessful();
    }

    public int getLoadSdkErrorCode() {
        return mFakeRemoteSdkCallback.getLoadSdkErrorCode();
    }

    public String getLoadSdkErrorMsg() {
        return mFakeRemoteSdkCallback.getLoadSdkErrorMsg();
    }

    public boolean isRequestSurfacePackageSuccessful() {
        return mFakeRemoteSdkCallback.isRequestSurfacePackageSuccessful();
    }

    public int getSurfacePackageErrorCode() {
        return mFakeRemoteSdkCallback.getSurfacePackageErrorCode();
    }

    public String getSurfacePackageErrorMsg() {
        return mFakeRemoteSdkCallback.getSurfacePackageErrorMsg();
    }
}
