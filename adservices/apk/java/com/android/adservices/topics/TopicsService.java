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
package com.android.adservices.topics;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.download.MddJob;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochJob;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsServiceImpl;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.adservices.shared.util.Clock;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Objects;

/** Topics Service */
@RequiresApi(Build.VERSION_CODES.S)
public class TopicsService extends Service {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();

    /** The binder service. This field must only be accessed on the main thread. */
    private TopicsServiceImpl mTopicsService;

    @Override
    public void onCreate() {
        super.onCreate();

        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            sLogger.e("onCreate(): Topics API is disabled");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return;
        }

        AppImportanceFilter appImportanceFilter =
                AppImportanceFilter.create(
                        this,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation());

        if (mTopicsService == null) {
            mTopicsService =
                    new TopicsServiceImpl(
                            this,
                            TopicsWorker.getInstance(),
                            ConsentManager.getInstance(),
                            AdServicesLoggerImpl.getInstance(),
                            Clock.getInstance(),
                            FlagsFactory.getFlags(),
                            Throttler.getInstance(),
                            EnrollmentDao.getInstance(),
                            appImportanceFilter);
            mTopicsService.init();
        }
        if (hasUserConsent()) {
            PackageChangedReceiver.enableReceiver(this, FlagsFactory.getFlags());
            schedulePeriodicJobs();
        }
    }

    private void schedulePeriodicJobs() {
        MaintenanceJobService.scheduleIfNeeded(this, /* forceSchedule */ false);
        EncryptionKeyJobService.scheduleIfNeeded(this, /* forceSchedule */ false);
        MddJob.scheduleAllMddJobs();
        EpochJob.schedule();
    }

    private boolean hasUserConsent() {
        return ConsentManager.getInstance().getConsent(AdServicesApiType.TOPICS).isGiven();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            sLogger.e("onBind(): Topics API is disabled, return nullBinding.");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            // Return null so that clients can not bind to the service.
            return null;
        }
        return Objects.requireNonNull(mTopicsService);
    }

    // TODO(b/246316128): Add dump() in Consent Manager.
    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
        if (Build.isDebuggable()) {
            writer.println("Build is Debuggable, dumping information for TopicsService");
            EpochManager.getInstance().dump(writer, args);
            CacheManager.getInstance().dump(writer, args);
            MobileDataDownloadFactory.dump(writer);
            writer.println("=== User Consent State For Topics Service ===");
            writer.println("User Consent is given: " + hasUserConsent());
        } else {
            writer.println("Build is not Debuggable");
        }
    }
}
