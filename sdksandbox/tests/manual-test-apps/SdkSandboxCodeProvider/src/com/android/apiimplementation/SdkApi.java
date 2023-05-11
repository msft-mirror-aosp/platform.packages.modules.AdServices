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

import android.app.Activity;
import android.app.sdksandbox.interfaces.IActivityStarter;
import android.app.sdksandbox.interfaces.ISdkApi;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.VideoView;

import com.android.modules.utils.build.SdkLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SdkApi extends ISdkApi.Stub {
    private static final String WEB_VIEW_LINK = "https://youtu.be/pQdzFbmlvOo";
    private static final String VIDEO_URL_KEY = "video-url";

    private final Context mContext;

    private WebView mWebView;

    public SdkApi(Context sdkContext) {
        mContext = sdkContext;
        preloadWebViewForActivity(sdkContext);
    }

    @Override
    public ParcelFileDescriptor getFileDescriptor(String inputValue) {
        try {
            final String fileName = "testParcelFileDescriptor";
            FileOutputStream fout = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
            // Writing inputValue String to a file
            for (int i = 0; i < inputValue.length(); i++) {
                fout.write((int) inputValue.charAt(i));
            }
            fout.close();
            File file = new File(mContext.getFilesDir(), fileName);
            ParcelFileDescriptor pFd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            return pFd;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String createFile(int sizeInMb) throws RemoteException {
        Path path;
        if (SdkLevel.isAtLeastU()) {
            // U device should be have customized sdk context that allows all storage APIs on
            // context to utlize per-sdk storage
            path = Paths.get(mContext.getFilesDir().getPath(), "file.txt");
            // Verify per-sdk storage is being used
            if (!path.startsWith(mContext.getDataDir().getPath())) {
                throw new IllegalStateException("Customized Sdk Context is not being used");
            }
        } else {
            path = Paths.get(mContext.getDataDir().getPath(), "file.txt");
        }

        try {
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
        return "Message from sdk in the sandbox process";
    }

    @Override
    public String getSyncedSharedPreferencesString(String key) {
        return getClientSharedPreferences().getString(key, "");
    }

    @Override
    public String getSandboxDump() {
        // Check if the SDK can access device volume.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        int curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return "Current music volume: " + curVolume + ", max music volume: " + maxVolume;
    }

    @Override
    public void startActivity(IActivityStarter iActivityStarter, Bundle params)
            throws RemoteException {
        if (!SdkLevel.isAtLeastU()) {
            throw new IllegalStateException("Starting activity requires Android U or above!");
        }

        final String videoUrl = params.getString(VIDEO_URL_KEY, null);
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);
        IBinder token =
                controller.registerSdkSandboxActivityHandler(
                        activity -> {
                            View mediaView;
                            if (videoUrl != null) {
                                mediaView = createVideoAd(activity, videoUrl);
                            } else {
                                mediaView = mWebView;
                            }
                            new ActivityHandler(activity, mContext, mediaView).buildLayout();
                        });
        iActivityStarter.startActivity(token);
    }

    @Override
    public boolean isCustomizedSdkContextEnabled() throws RemoteException {
        // If customized-sdk-context is enabled, then per-sdk storage should be returned for all
        // storage apis on Context object
        final String filesDir = mContext.getFilesDir().getAbsolutePath();
        final String perSdkDir = mContext.getDataDir().getAbsolutePath();
        return filesDir.startsWith(perSdkDir);
    }

    private SharedPreferences getClientSharedPreferences() {
        return mContext.getSystemService(SdkSandboxController.class).getClientSharedPreferences();
    }

    private VideoView createVideoAd(Activity activity, String videoUrl) {
        final VideoView videoView = new VideoView(activity);
        videoView.setVideoURI(Uri.parse(videoUrl));
        videoView.requestFocus();

        videoView.setOnPreparedListener(mp -> videoView.start());

        videoView.setOnCompletionListener(
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        videoView.setOnTouchListener(
                                (view, event) -> {
                                    Intent visitUrl = new Intent(Intent.ACTION_VIEW);
                                    visitUrl.setData(Uri.parse("https://www.google.com"));
                                    visitUrl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                    activity.startActivity(visitUrl);
                                    return true;
                                });
                    }
                });
        return videoView;
    }

    private void preloadWebViewForActivity(Context sdkContext) {
        mWebView = new WebView(sdkContext);
        initializeSettings(mWebView.getSettings());
        // Handle Urls locally
        mWebView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view, WebResourceRequest request) {
                        return false;
                    }
                });
        mWebView.loadUrl(WEB_VIEW_LINK);
    }

    private void initializeSettings(WebSettings settings) {
        settings.setJavaScriptEnabled(true);

        settings.setGeolocationEnabled(true);
        settings.setSupportZoom(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Default layout behavior for chrome on android.
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
    }
}
