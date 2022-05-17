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

package com.android.adservices.customaudience;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.android.adservices.service.customaudience.CustomAudienceServiceImpl;

import java.util.Objects;

/** Custom Audience Service */
public class CustomAudienceService extends Service {

    /** The binder service. This field will only be accessed on the main thread. */
    private CustomAudienceServiceImpl mCustomAudienceService;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mCustomAudienceService == null) {
            mCustomAudienceService =
                    new CustomAudienceServiceImpl(this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return Objects.requireNonNull(mCustomAudienceService);
    }
}
