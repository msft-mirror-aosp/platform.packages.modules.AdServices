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

package com.android.sdksandboxcode_1;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.app.sdksandbox.interfaces.IAppOwnedSdkApi;
import android.app.sdksandbox.interfaces.ISdkApi;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.apiimplementation.SdkApi;

import java.util.List;
import java.util.Random;

public class SampleSandboxedSdkProvider extends SandboxedSdkProvider {

    private static final String TAG = "SampleSandboxedSdkProvider";

    private static final String VIEW_TYPE_KEY = "view-type";
    private static final String VIDEO_VIEW_VALUE = "video-view";
    private static final String VIDEO_URL_KEY = "video-url";
    private static final String EXTRA_SDK_SDK_ENABLED_KEY = "sdkSdkCommEnabled";
    private static final String APP_OWNED_SDK_NAME = "app-sdk-1";
    private String mSdkSdkCommEnabled = null;

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) {
        return new SandboxedSdk(new SdkApi(getContext()));
    }

    @Override
    public void beforeUnloadSdk() {
        Log.i(TAG, "SDK unloaded");
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        String type = params.getString(VIEW_TYPE_KEY, "");
        if (VIDEO_VIEW_VALUE.equals(type)) {
            String videoUrl = params.getString(VIDEO_URL_KEY, "");
            return new TestVideoView(windowContext, videoUrl);
        }
        mSdkSdkCommEnabled = params.getString(EXTRA_SDK_SDK_ENABLED_KEY, null);
        return new TestView(windowContext, getContext(), mSdkSdkCommEnabled);
    }

    private static class TestView extends View {

        private static final CharSequence MEDIATEE_SDK = "com.android.sdksandboxcode_mediatee";
        private static final String DROPDOWN_KEY_SDK_SANDBOX = "SDK_IN_SANDBOX";
        private static final String DROPDOWN_KEY_SDK_APP = "SDK_IN_APP";
        private Context mSdkContext;
        private String mSdkToSdkCommEnabled;

        TestView(Context windowContext, Context sdkContext, String sdkSdkCommEnabled) {
            super(windowContext);
            mSdkContext = sdkContext;
            mSdkToSdkCommEnabled = sdkSdkCommEnabled;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            Paint paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTextSize(50);
            Random random = new Random();
            String message = null;

            if (!TextUtils.isEmpty(mSdkToSdkCommEnabled)) {
                if (mSdkToSdkCommEnabled.equals(DROPDOWN_KEY_SDK_SANDBOX)) {
                    SandboxedSdk mediateeSdk;
                    try {
                        // get message from another sandboxed SDK
                        List<SandboxedSdk> sandboxedSdks =
                                mSdkContext
                                        .getSystemService(SdkSandboxController.class)
                                        .getSandboxedSdks();
                        mediateeSdk =
                                sandboxedSdks.stream()
                                        .filter(
                                                s ->
                                                        s.getSharedLibraryInfo()
                                                                .getName()
                                                                .contains(MEDIATEE_SDK))
                                        .findAny()
                                        .get();
                    } catch (Exception e) {
                        throw new IllegalStateException("Error in sdk-sdk communication ", e);
                    }
                    try {
                        IBinder binder = mediateeSdk.getInterface();
                        ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                        message = sdkApi.getMessage();
                    } catch (RemoteException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (mSdkToSdkCommEnabled.equals(DROPDOWN_KEY_SDK_APP)) {
                    try {
                        // get message from an app owned SDK
                        List<AppOwnedSdkSandboxInterface> appOwnedSdks =
                                mSdkContext
                                        .getSystemService(SdkSandboxController.class)
                                        .getAppOwnedSdkSandboxInterfaces();

                        AppOwnedSdkSandboxInterface appOwnedSdk =
                                appOwnedSdks.stream()
                                        .filter(s -> s.getName().contains(APP_OWNED_SDK_NAME))
                                        .findAny()
                                        .get();
                        IAppOwnedSdkApi appOwnedSdkApi =
                                IAppOwnedSdkApi.Stub.asInterface(appOwnedSdk.getInterface());
                        message = appOwnedSdkApi.getMessage();
                    } catch (RemoteException e) {
                        throw new IllegalStateException(e);
                    }
                }
            } else {
                message = mSdkContext.getResources().getString(R.string.view_message);
            }
            int c = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            canvas.drawColor(c);
            canvas.drawText(message, 75, 75, paint);
            setOnClickListener(this::onClickListener);
        }

        private void onClickListener(View view) {
            Context context = view.getContext();
            Toast.makeText(context, "Opening url", Toast.LENGTH_LONG).show();

            String url = "http://www.google.com";
            Intent visitUrl = new Intent(Intent.ACTION_VIEW);
            visitUrl.setData(Uri.parse(url));
            visitUrl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            mSdkContext.startActivity(visitUrl);
        }

    }

    private static class TestVideoView extends VideoView {

        TestVideoView(Context windowContext, String url) {
            super(windowContext);
            new Handler(Looper.getMainLooper())
                    .post(
                            () -> {
                                setVideoURI(Uri.parse(url));
                                requestFocus();
                                start();
                            });
        }
    }
}
