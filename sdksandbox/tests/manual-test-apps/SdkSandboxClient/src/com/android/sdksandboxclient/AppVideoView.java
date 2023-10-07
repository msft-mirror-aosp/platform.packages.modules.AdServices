/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.sdksandboxclient;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.VideoView;

/**
 * Displays a video view in the app. This can be used to test the differences between a video ad
 * from the sandbox vs. an app's VideoView.
 *
 * <p>Open this activity using the following command: adb shell am start -n
 * com.android.sdksandboxclient/.AppVideoView --es "video-url" "[video url]"
 */
public class AppVideoView extends Activity {
    static final String VIDEO_URL_KEY = "video-url";

    private VideoView mVideoView;
    private EditText mVideoUrlEdit;
    private Button mStartAppVideoButton;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_app_video);

        mVideoView = findViewById(R.id.app_video);
        mVideoUrlEdit = findViewById(R.id.app_video_url_edit);
        mStartAppVideoButton = findViewById(R.id.start_app_video_button);

        registerStartAppVideoButton();

        final Bundle extras = getIntent().getExtras();
        final String videoUrl = extras == null ? null : extras.getString(VIDEO_URL_KEY);
        if (videoUrl != null) {
            mVideoUrlEdit.setText(videoUrl);
            startVideo(videoUrl);
        }
    }

    private void registerStartAppVideoButton() {
        mStartAppVideoButton.setOnClickListener(
                v -> {
                    final String videoUrl = mVideoUrlEdit.getText().toString();
                    startVideo(videoUrl);
                });
    }

    private void startVideo(String videoUrl) {
        mVideoView.setVideoURI(Uri.parse(videoUrl));
        mVideoView.requestFocus();
        mVideoView.start();
    }
}
