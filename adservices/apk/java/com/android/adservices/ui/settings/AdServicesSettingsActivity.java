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
package com.android.adservices.ui.settings;

import android.app.ActionBar;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;

/**
 * Android application activity for controlling settings related to PP (Privacy Preserving) APIs.
 */
public class AdServicesSettingsActivity extends FragmentActivity {
    private ActionDelegate mActionDelegate;

    public ActionDelegate getActionDelegate() {
        return mActionDelegate;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initActionDelegate();
        setContentView(R.layout.adservices_settings_main_activity);
        initActionBar();
    }

    // TODO(b/230372790): update to another action bar.
    private void initActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.settingsUI_privacy_sandbox_beta_title);
    }

    private void initActionDelegate() {
        mActionDelegate =
                new ActionDelegate(
                        this,
                        getSupportFragmentManager(),
                        new ViewModelProvider(this).get(MainViewModel.class),
                        new ViewModelProvider(this).get(TopicsViewModel.class));
    }
}
