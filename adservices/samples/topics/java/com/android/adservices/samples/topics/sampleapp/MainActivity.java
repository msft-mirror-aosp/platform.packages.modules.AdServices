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
package com.android.adservices.samples.topics.sampleapp;

import android.adservices.topics.GetTopicsResponse;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Android application activity for testing Topics API by providing a button in UI that initiate
 * user's interaction with Topics Manager in the background. Response from Topics API will be shown
 * in the app as text as well as toast message. In case anything goes wrong in this process, error
 * message will also be shown in toast to suggest the Exception encountered.
 */
public class MainActivity extends AppCompatActivity {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SPACE = " ";
    private static String mSdkName = "SdkName";
    private Button mTopicsClientButton;
    private TextView mResultTextView;
    private AdvertisingTopicsClient mAdvertisingTopicsClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTopicsClientButton = findViewById(R.id.topics_client_button);
        mResultTextView = findViewById(R.id.textView);
        mAdvertisingTopicsClient = new AdvertisingTopicsClient.Builder()
                .setContext(this)
                .setSdkName(mSdkName)
                .setExecutor(CALLBACK_EXECUTOR)
                .build();
        registerGetTopicsButton();
    }

    private void registerGetTopicsButton() {
        mTopicsClientButton.setOnClickListener(v -> {
            try {
                // TODO(b/225902793): This will cause ANRs if the getTopics().get() take more than 5 seconds.
                GetTopicsResponse result = mAdvertisingTopicsClient.getTopics().get();
                String topics = getTopics(result.getTopics()),
                        text = "Topics are " + topics;
                mResultTextView.setText(text);
                makeToast(text);
            } catch (ExecutionException | InterruptedException e) {
                makeToast(e.getMessage());
            }
        });
    }

    private String getTopics(List<String> arr) {
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            sb.append(s).append(SPACE);
        }
        return sb.toString();
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }
}
