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
package com.android.adservices.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.AdServicesSettingsActivity;

/**
 * Android application activity for controlling settings related to PP (Privacy Preserving) APIs.
 */
public class ConsentNotificationActivity extends FragmentActivity {
    public static final String EEA_DEVICE = "com.google.android.feature.EEA_DEVICE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupActivity();
    }

    private void setupActivity() {
        boolean isEUDevice = getPackageManager().hasSystemFeature(EEA_DEVICE);
        if (isEUDevice) {
            setContentView(R.layout.consent_notification_activity_eu);
        } else {
            setContentView(R.layout.consent_notification_activity);
        }
        setupListeners();
    }

    private void setupListeners() {
        boolean isEUDevice = getPackageManager().hasSystemFeature(EEA_DEVICE);

        TextView howItWorksExpander = findViewById(R.id.how_it_works_expander);
        howItWorksExpander.setOnClickListener(
                view -> {
                    TextView text = findViewById(R.id.how_it_works_expanded_text);
                    if (text.getVisibility() == View.VISIBLE) {
                        text.setVisibility(View.GONE);
                    } else {
                        text.setVisibility(View.VISIBLE);
                    }
                });

        Button leftControlButton = findViewById(R.id.leftControlButton);
        leftControlButton.setOnClickListener(
                view -> {
                    if (isEUDevice) {
                        // opt out
                        finish();
                    } else {
                        // go to settings view
                        Intent intent = new Intent(this, AdServicesSettingsActivity.class);
                        startActivity(intent);
                    }
                });

        Button rightControlButton = findViewById(R.id.rightControlButton);
        rightControlButton.setOnClickListener(
                view -> {
                    if (isEUDevice) {
                        // TODO(b/221848850): opt-in confirmation activity
                    } else {
                        // acknowledge and dismiss
                        finish();
                    }
                });
    }
}
