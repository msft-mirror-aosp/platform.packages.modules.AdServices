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

import com.android.adservices.service.PhFlags;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;


/**
 * Wrapper class for {@link AdServicesSettingsActivity} used for testing purposes only. Instantiates
 * a {@link AdServicesSettingsActivity} with the custom constructor that can be passed a a mocked
 * view model provider. This is needed because the some view models (such as the {@link
 * TopicsViewModel}) will ultimately need to call {@link PhFlags} to read system settings, which
 * requires the READ_DEVICE_CONFIG permission that is only granted to the real PP API process and
 * not the process used for the test application.
 */
public class AdServicesSettingsActivityWrapper extends AdServicesSettingsActivity {
    public AdServicesSettingsActivityWrapper() {
        super(SettingsActivityTest.generateMockedViewModelProvider());
    }
}
