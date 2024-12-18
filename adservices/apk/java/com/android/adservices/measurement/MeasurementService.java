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
package com.android.adservices.measurement;


import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.CachedFlags;
import com.android.adservices.service.measurement.MeasurementServiceImpl;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

/** Measurement Service */
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementService extends Service {

    /** The binder service. This field must only be accessed on the main thread. */
    private MeasurementServiceImpl mMeasurementService;

    /**
     * Constructor for MeasurementService. Although not explicitly referenced, still necessary
     * because the other constructor that's meant only for testing becomes the default constructor
     * without this one, making the class uninstantiable. See: <a
     * href="https://docs.oracle.com/javase/specs/jls/se22/html/jls-8.html#jls-8.8.10">...</a>
     */
    public MeasurementService() {}

    @VisibleForTesting
    MeasurementService(MeasurementServiceImpl measurementService) {
        mMeasurementService = measurementService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Flags flags = FlagsFactory.getFlags();
        DebugFlags debugFlags = DebugFlags.getInstance();
        if (!flags.getMeasurementEnabled()) {
            LogUtil.e("Measurement API is disabled");
            return;
        }

        if (mMeasurementService == null) {
            final AppImportanceFilter appImportanceFilter =
                    AppImportanceFilter.create(
                            this,
                            () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation());

            mMeasurementService =
                    new MeasurementServiceImpl(
                            this,
                            Clock.getInstance(),
                            ConsentManager.getInstance(),
                            new CachedFlags(flags),
                            debugFlags,
                            appImportanceFilter);
        }

        if (hasUserConsent()) {
            PackageChangedReceiver.enableReceiver(this, flags);
            mMeasurementService.schedulePeriodicJobs(/* callback= */ null);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!FlagsFactory.getFlags().getMeasurementEnabled()) {
            LogUtil.e("Measurement API is disabled");
            // Return null so that clients can not bind to the service.
            return null;
        }
        return mMeasurementService;
    }

    private boolean hasUserConsent() {
        return ConsentManager.getInstance().getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
    }
}
