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
import android.widget.VideoView;

/**
 * Displays a video view in the app. This can be used to test the differences between a video ad
 * from the sandbox vs. an app's VideoView.
 *
 * <p>Open this activity using the following command: adb shell am start -n
 * com.android.sdksandboxclient/.AppVideoView --es "video-url" "[video url]"
 */
public class AppVideoView extends Activity {
    private static final String VIDEO_URL_KEY = "video-url";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_app_video);

        Bundle extras = getIntent().getExtras();
        if (extras == null) return;
        final String videoUrl = extras.getString(VIDEO_URL_KEY);

        VideoView videoView = findViewById(R.id.app_video);
        videoView.setVideoURI(Uri.parse(videoUrl));
        videoView.requestFocus();
        videoView.start();
    }
}
