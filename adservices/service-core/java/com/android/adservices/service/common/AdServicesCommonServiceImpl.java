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

package com.android.adservices.service.common;

import android.adservices.common.IAdServicesCommonService;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesExecutors;
import com.android.adservices.service.measurement.MeasurementImpl;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link IAdServicesCommonService}.
 *
 * @hide
 */
public class AdServicesCommonServiceImpl extends
        IAdServicesCommonService.Stub {
    private final MeasurementImpl mMeasurementImpl;
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    public AdServicesCommonServiceImpl(Context context) {
        mMeasurementImpl = MeasurementImpl.getInstance(context);
    }

    @Override
    public void onPackageFullyRemoved(@NonNull Uri packageUri) {
        Objects.requireNonNull(packageUri);
        measurementOnPackageFullyRemoved(packageUri);
    }

    @Override
    public void onPackageAdded(@NonNull Uri packageUri) {
        Objects.requireNonNull(packageUri);
        measurementOnPackageAdded(packageUri);
    }

    private void measurementOnPackageFullyRemoved(@NonNull Uri packageUri) {
        LogUtil.d(
                "Deleting package measurement records for package: " + packageUri.toString());
        sBackgroundExecutor.execute(() -> {
            mMeasurementImpl.deletePackageRecords(packageUri);
        });
    }

    private void measurementOnPackageAdded(Uri packageUri) {
        LogUtil.d(
                "Adding package install attribution records for package: " + packageUri.toString());
        sBackgroundExecutor.execute(() -> {
            mMeasurementImpl.doInstallAttribution(packageUri, System.currentTimeMillis());
        });
    }
}
