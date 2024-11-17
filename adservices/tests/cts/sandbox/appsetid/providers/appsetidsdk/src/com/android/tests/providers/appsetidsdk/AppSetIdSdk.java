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

package com.android.tests.providers.appsetidsdk;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdManager;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.android.adservices.shared.testing.OutcomeReceiverForTests;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AppSetIdSdk extends SandboxedSdkProvider {
    private static final String TAG = "AppSetIdSdk";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {

        try {
            AppSetIdManager appSetIdManager = AppSetIdManager.get(getContext());
            OutcomeReceiverForTests<AppSetId> callback = new OutcomeReceiverForTests<>();
            appSetIdManager.getAppSetId(CALLBACK_EXECUTOR, callback);

            AppSetId resultAppSetId = callback.assertResultReceived();

            if (resultAppSetId != null && resultAppSetId.getId() != null) {
                // Successfully called the getAppSetId
                if (resultAppSetId.getId().isEmpty()) {
                    Log.e(TAG, "AppSetId get empty result");
                } else {
                    Log.d(
                            TAG,
                            "Successfully called the getAppSetId. resultAppSetIdString = "
                                    + resultAppSetId.getId());
                }
                return new SandboxedSdk(new Binder());
            } else {
                // Failed to call the getAppSetId
                Exception exception = callback.getError();
                Log.e(TAG, "Failed to call the getAppSetId with exception: " + exception);
                throw new LoadSdkException(new Exception("AppSetId failed."), new Bundle());
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            // Throw an exception to tell the Test App that some errors occurred so
            // that it will fail the test.
            throw new LoadSdkException(e, new Bundle());
        }
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        return null;
    }
}
