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

import android.os.Bundle;
import android.view.MenuItem;

import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.common.annotations.VisibleForTesting;

/**
 * Android application activity for controlling settings related to PP (Privacy Preserving) APIs.
 */
public class AdServicesSettingsActivity extends CollapsingToolbarBaseActivity {
    private ActionDelegate mActionDelegate;
    private ViewModelProvider mViewModelProvider;

    /** @return the {@link ActionDelegate} for the activity. */
    public ActionDelegate getActionDelegate() {
        return mActionDelegate;
    }

    /**
     * Gets the {@link ViewModelProvider} for the activity. Need to use this implementation for
     * testing/mocking limitations.
     *
     * @return the {@link ViewModelProvider} for the activity.
     */
    public ViewModelProvider getViewModelProvider() {
        if (mViewModelProvider == null) {
            mViewModelProvider = new ViewModelProvider(this);
        }
        return mViewModelProvider;
    }

    public AdServicesSettingsActivity() {}

    @VisibleForTesting
    AdServicesSettingsActivity(ViewModelProvider viewModelProvider) {
        mViewModelProvider = viewModelProvider;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.adservices_settings_main_activity);
        initActionDelegate();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initActionDelegate() {
        mActionDelegate =
                new ActionDelegate(
                        this,
                        getSupportFragmentManager(),
                        getViewModelProvider().get(MainViewModel.class),
                        getViewModelProvider().get(TopicsViewModel.class),
                        getViewModelProvider().get(AppsViewModel.class));
    }
}
