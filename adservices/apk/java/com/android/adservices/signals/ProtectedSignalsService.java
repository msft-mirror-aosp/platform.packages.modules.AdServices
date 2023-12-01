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

package com.android.adservices.signals;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.signals.ProtectedSignalsServiceImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** Protected Signals Service */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ProtectedSignalsService extends Service {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    /** The binder service. This field will only be accessed on the main thread. */
    private ProtectedSignalsServiceImpl mProtectedSignalsService;

    private final Flags mFlags;

    public ProtectedSignalsService() {
        this(FlagsFactory.getFlags());
    }

    @VisibleForTesting
    ProtectedSignalsService(Flags flags) {
        this.mFlags = flags;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (mFlags.getProtectedSignalsServiceKillSwitch()) {
            sLogger.e("Protected Signals API is disabled");
            return;
        }

        if (mProtectedSignalsService == null) {
            mProtectedSignalsService = ProtectedSignalsServiceImpl.create(this);
        }

        if (hasUserConsent()) {
            PackageChangedReceiver.enableReceiver(this, mFlags);
            MddJobService.scheduleIfNeeded(this, /* forceSchedule */ false);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (mFlags.getProtectedSignalsServiceKillSwitch()) {
            sLogger.e("Protected signals API is disabled");
            // Return null so that clients can not bind to the service.
            return null;
        }
        return Objects.requireNonNull(mProtectedSignalsService);
    }

    /**
     * @return {@code true} if the Privacy Sandbox has user consent
     */
    private boolean hasUserConsent() {
        return ConsentManager.getInstance(this).getConsent(AdServicesApiType.FLEDGE).isGiven();
    }
}