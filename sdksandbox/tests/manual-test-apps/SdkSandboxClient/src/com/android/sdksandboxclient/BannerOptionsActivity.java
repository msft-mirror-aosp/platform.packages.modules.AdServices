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

import android.os.Bundle;
import android.os.Looper;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BannerOptionsActivity extends AppCompatActivity {

    public BannerOptionsActivity() {
        super(R.layout.activity_options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.options_container, BannerOptionsFragment.class, null)
                    .commit();
        }
    }

    public static class BannerOptionsFragment extends PreferenceFragmentCompat {

        private final Executor mExecutor = Executors.newSingleThreadExecutor();

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            mExecutor.execute(
                    () -> {
                        Looper.prepare();
                        setPreferencesFromResource(R.xml.banner_preferences, rootKey);
                    });
        }
    }
}
