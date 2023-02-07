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

import android.os.Bundle;

import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.ui.OTAResourcesManager;

/**
 * Android application activity for controlling settings related to PP (Privacy Preserving) APIs.
 */
public class ConsentNotificationActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (FlagsFactory.getFlags().getUiOtaStringsFeatureEnabled()) {
            OTAResourcesManager.applyOTAResources(getApplicationContext(), true);
        }
        setContentView(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? R.layout.consent_notification_ga_activity
                        : R.layout.consent_notification_activity);
    }
}
