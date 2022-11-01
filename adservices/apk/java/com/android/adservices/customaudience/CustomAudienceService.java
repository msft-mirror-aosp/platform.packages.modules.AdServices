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

import com.android.adservices.LogUtil;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.customaudience.CustomAudienceServiceImpl;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/** Custom Audience Service */
public class CustomAudienceService extends Service {

    /** The binder service. This field will only be accessed on the main thread. */
    private CustomAudienceServiceImpl mCustomAudienceService;

    private Flags mFlags;

    public CustomAudienceService() {
        this(FlagsFactory.getFlags());
    }

    @VisibleForTesting
    CustomAudienceService(Flags flags) {
        this.mFlags = flags;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (mFlags.getFledgeCustomAudienceServiceKillSwitch()) {
            LogUtil.e("Custom Audience API is disabled");
            return;
        }

        if (mCustomAudienceService == null) {
            mCustomAudienceService = CustomAudienceServiceImpl.create(this);
        }

        if (hasUserConsent()) {
            PackageChangedReceiver.enableReceiver(this);
            MddJobService.scheduleIfNeeded(this, /* forceSchedule */ false);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (mFlags.getFledgeCustomAudienceServiceKillSwitch()) {
            LogUtil.e("Custom Audience API is disabled");
            // Return null so that clients can not bind to the service.
            return null;
        }
        return Objects.requireNonNull(mCustomAudienceService);
    }

    /** @return {@code true} if the Privacy Sandbox has user consent */
    private boolean hasUserConsent() {
        return ConsentManager.getInstance(this).getConsent().isGiven();
    }
}
