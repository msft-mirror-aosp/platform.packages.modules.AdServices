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

package com.android.adservices.service.consent;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;

import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.app.job.JobScheduler;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Manager to handle user's consent.
 *
 * <p>For Beta the consent is given for all {@link AdServicesApiType} or for none.
 */
public class ConsentManager {
    private static final String ERROR_MESSAGE_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";
    private static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";
    private static final String CONSENT_KEY = "CONSENT";
    private static final String ERROR_MESSAGE_WHILE_SET_CONTENT = "setConsent method failed.";
    // Internal datastore version
    @VisibleForTesting static final int STORAGE_VERSION = 1;
    // Internal datastore filename. The name should be unique to avoid multiple threads or processes
    // to update the same file.
    @VisibleForTesting
    static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    private static volatile ConsentManager sConsentManager;

    private final Flags mFlags;
    private final TopicsWorker mTopicsWorker;
    private final BooleanFileDatastore mDatastore;
    private final AppConsentDao mAppConsentDao;
    private final EnrollmentDao mEnrollmentDao;
    private final MeasurementImpl mMeasurementImpl;
    private final AdServicesLoggerImpl mAdServicesLoggerImpl;
    private final int mDeviceLoggingRegion;
    private final CustomAudienceDao mCustomAudienceDao;
    private final ExecutorService mExecutor;
    private final AdServicesManager mAdServicesManager;

    ConsentManager(
            @NonNull Context context,
            @NonNull TopicsWorker topicsWorker,
            @NonNull AppConsentDao appConsentDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull MeasurementImpl measurementImpl,
            @NonNull AdServicesLoggerImpl adServicesLoggerImpl,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AdServicesManager adServicesManager,
            @NonNull BooleanFileDatastore booleanFileDatastore,
            @NonNull Flags flags) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(topicsWorker);
        Objects.requireNonNull(appConsentDao);
        Objects.requireNonNull(measurementImpl);
        Objects.requireNonNull(adServicesLoggerImpl);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adServicesManager);
        Objects.requireNonNull(booleanFileDatastore);

        mAdServicesManager = adServicesManager;
        mTopicsWorker = topicsWorker;
        mDatastore = booleanFileDatastore;
        mAppConsentDao = appConsentDao;
        mEnrollmentDao = enrollmentDao;
        mMeasurementImpl = measurementImpl;
        mAdServicesLoggerImpl = adServicesLoggerImpl;
        mCustomAudienceDao = customAudienceDao;
        mExecutor = Executors.newSingleThreadExecutor();
        mFlags = flags;
        mDeviceLoggingRegion = initializeLoggingValues(context);
    }

    /**
     * Gets an instance of {@link ConsentManager} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static ConsentManager getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        if (sConsentManager == null) {
            synchronized (ConsentManager.class) {
                if (sConsentManager == null) {
                    sConsentManager =
                            new ConsentManager(
                                    context,
                                    TopicsWorker.getInstance(context),
                                    AppConsentDao.getInstance(context),
                                    EnrollmentDao.getInstance(context),
                                    MeasurementImpl.getInstance(context),
                                    AdServicesLoggerImpl.getInstance(),
                                    CustomAudienceDatabase.getInstance(context).customAudienceDao(),
                                    context.getSystemService(AdServicesManager.class),
                                    createAndInitializeDataStore(context),
                                    FlagsFactory.getFlags());
                }
            }
        }
        return sConsentManager;
    }

    /**
     * Enables all PP API services. It gives consent to Topics, Fledge and Measurements services.
     */
    public void enable(@NonNull Context context) {
        Objects.requireNonNull(context);

        mAdServicesLoggerImpl.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(mDeviceLoggingRegion)
                        .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED)
                        .build());

        // Enable all the APIs
        try {
            BackgroundJobsManager.scheduleAllBackgroundJobs(context);

            setConsent(AdServicesApiConsent.GIVEN);
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }
    }

    /**
     * Disables all PP API services. It revokes consent to Topics, Fledge and Measurements services.
     */
    public void disable(@NonNull Context context) {
        Objects.requireNonNull(context);

        mAdServicesLoggerImpl.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(mDeviceLoggingRegion)
                        .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED)
                        .build());

        // Disable all the APIs
        try {
            // reset all data
            resetTopicsAndBlockedTopics();
            resetAppsAndBlockedApps();
            resetMeasurement();
            resetEnrollment();

            BackgroundJobsManager.unscheduleAllBackgroundJobs(
                    context.getSystemService(JobScheduler.class));

            setConsent(AdServicesApiConsent.REVOKED);
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }
    }

    /** Retrieves the consent for all PP API services. */
    public AdServicesApiConsent getConsent() {
        if (mFlags.getConsentManagerDebugMode()) {
            return AdServicesApiConsent.GIVEN;
        }
        try {
            // TODO(b/258679209): switch to use the Consent from the System Service.
            if (mFlags.getConsentSourceOfTruth() != Flags.PPAPI_ONLY) {
                mAdServicesManager.getConsent();
            }

            return AdServicesApiConsent.getConsent(mDatastore.get(CONSENT_KEY));
        } catch (NullPointerException | IllegalArgumentException | SecurityException e) {
            LogUtil.e(e, ERROR_MESSAGE_WHILE_GET_CONTENT);
            return AdServicesApiConsent.REVOKED;
        }
    }

    /**
     * Proxy call to {@link TopicsWorker} to get {@link ImmutableList} of {@link Topic}s which could
     * be returned to the {@link TopicsWorker} clients.
     *
     * @return {@link ImmutableList} of {@link Topic}s.
     */
    @NonNull
    public ImmutableList<Topic> getKnownTopicsWithConsent() {
        return mTopicsWorker.getKnownTopicsWithConsent();
    }

    /**
     * Proxy call to {@link TopicsWorker} to get {@link ImmutableList} of {@link Topic}s which were
     * blocked by the user.
     *
     * @return {@link ImmutableList} of blocked {@link Topic}s.
     */
    @NonNull
    public ImmutableList<Topic> getTopicsWithRevokedConsent() {
        return mTopicsWorker.getTopicsWithRevokedConsent();
    }

    /**
     * Proxy call to {@link TopicsWorker} to revoke consent for provided {@link Topic} (block
     * topic).
     *
     * @param topic {@link Topic} to block.
     */
    @NonNull
    public void revokeConsentForTopic(@NonNull Topic topic) {
        mTopicsWorker.revokeConsentForTopic(topic);
    }

    /**
     * Proxy call to {@link TopicsWorker} to restore consent for provided {@link Topic} (unblock the
     * topic).
     *
     * @param topic {@link Topic} to restore consent for.
     */
    @NonNull
    public void restoreConsentForTopic(@NonNull Topic topic) {
        mTopicsWorker.restoreConsentForTopic(topic);
    }

    /** Wipes out all the data gathered by Topics API but blocked topics. */
    public void resetTopics() {
        ArrayList<String> tablesToBlock = new ArrayList<>();
        tablesToBlock.add(TopicsTables.BlockedTopicsContract.TABLE);
        mTopicsWorker.clearAllTopicsData(tablesToBlock);
    }

    /** Wipes out all the data gathered by Topics API. */
    public void resetTopicsAndBlockedTopics() {
        mTopicsWorker.clearAllTopicsData(new ArrayList<>());
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have not had user
     *     consent revoked
     */
    public ImmutableList<App> getKnownAppsWithConsent() {
        try {
            return ImmutableList.copyOf(
                    mAppConsentDao.getKnownAppsWithConsent().stream()
                            .map(App::create)
                            .collect(Collectors.toList()));
        } catch (IOException e) {
            LogUtil.e(e, "getKnownAppsWithConsent failed due to IOException.");
            return ImmutableList.of();
        }
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked
     */
    public ImmutableList<App> getAppsWithRevokedConsent() {
        try {
            return ImmutableList.copyOf(
                    mAppConsentDao.getAppsWithRevokedConsent().stream()
                            .map(App::create)
                            .collect(Collectors.toList()));
        } catch (IOException e) {
            LogUtil.e(e, "getAppsWithRevokedConsent failed due to IOException.");
            return ImmutableList.of();
        }
    }

    /**
     * Proxy call to {@link AppConsentDao} to revoke consent for provided {@link App}.
     *
     * <p>Also clears all app data related to the provided {@link App}.
     *
     * @param app {@link App} to block.
     * @throws IOException if the operation fails
     */
    public void revokeConsentForApp(@NonNull App app) throws IOException {
        mAppConsentDao.setConsentForApp(app.getPackageName(), true);
        asyncExecute(
                () -> mCustomAudienceDao.deleteCustomAudienceDataByOwner(app.getPackageName()));
    }

    /**
     * Proxy call to {@link AppConsentDao} to restore consent for provided {@link App}.
     *
     * @param app {@link App} to restore consent for.
     * @throws IOException if the operation fails
     */
    public void restoreConsentForApp(@NonNull App app) throws IOException {
        mAppConsentDao.setConsentForApp(app.getPackageName(), false);
    }

    /**
     * Deletes all app consent data and all app data gathered or generated by the Privacy Sandbox.
     *
     * <p>This should be called when the Privacy Sandbox has been disabled.
     *
     * @throws IOException if the operation fails
     */
    public void resetAppsAndBlockedApps() throws IOException {
        mAppConsentDao.clearAllConsentData();
        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
    }

    /**
     * Deletes the list of known allowed apps as well as all app data from the Privacy Sandbox.
     *
     * <p>The list of blocked apps is not reset.
     *
     * @throws IOException if the operation fails
     */
    public void resetApps() throws IOException {
        mAppConsentDao.clearKnownAppsWithConsent();
        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
    }

    /**
     * Checks whether a single given installed application (identified by its package name) has had
     * user consent to use the FLEDGE APIs revoked.
     *
     * <p>This method also checks whether a user has opted out of the FLEDGE Privacy Sandbox
     * initiative.
     *
     * @param packageName String package name that uniquely identifies an installed application to
     *     check
     * @return {@code true} if either the FLEDGE Privacy Sandbox initiative has been opted out or if
     *     the user has revoked consent for the given application to use the FLEDGE APIs
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    public boolean isFledgeConsentRevokedForApp(@NonNull String packageName)
            throws IllegalArgumentException {
        // TODO(b/238464639): Implement API-specific consent for FLEDGE
        if (!getConsent().isGiven()) {
            return true;
        }

        try {
            return mAppConsentDao.isConsentRevokedForApp(packageName);
        } catch (IOException exception) {
            LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
            return true;
        }
    }

    /**
     * Persists the use of a FLEDGE API by a single given installed application (identified by its
     * package name) if the app has not already had its consent revoked.
     *
     * <p>This method also checks whether a user has opted out of the FLEDGE Privacy Sandbox
     * initiative.
     *
     * <p>This is only meant to be called by the FLEDGE APIs.
     *
     * @param packageName String package name that uniquely identifies an installed application that
     *     has used a FLEDGE API
     * @return {@code true} if user consent has been revoked for the application or API, {@code
     *     false} otherwise
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    public boolean isFledgeConsentRevokedForAppAfterSettingFledgeUse(@NonNull String packageName)
            throws IllegalArgumentException {
        // TODO(b/238464639): Implement API-specific consent for FLEDGE
        if (!getConsent().isGiven()) {
            return true;
        }

        try {
            return mAppConsentDao.setConsentForAppIfNew(packageName, false);
        } catch (IOException exception) {
            LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
            return true;
        }
    }

    /** Wipes out all the data gathered by Measurement API. */
    public void resetMeasurement() {
        mMeasurementImpl.deleteAllMeasurementData(List.of());
    }

    /** Wipes out all the Enrollment data */
    private void resetEnrollment() {
        mEnrollmentDao.deleteAll();
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed() {
        try {
            // TODO(b/229725886): add metrics / logging
            mDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        } catch (IOException e) {
            LogUtil.e(e, "Record notification failed due to IOException thrown by Datastore.");
        }
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public Boolean wasNotificationDisplayed() {
        return mDatastore.get(NOTIFICATION_DISPLAYED_ONCE);
    }

    private void setConsent(AdServicesApiConsent state) throws IOException {
        mDatastore.put(CONSENT_KEY, state.isGiven());
    }

    @VisibleForTesting
    static BooleanFileDatastore createAndInitializeDataStore(@NonNull Context context) {
        BooleanFileDatastore booleanFileDatastore =
                new BooleanFileDatastore(context, STORAGE_XML_IDENTIFIER, STORAGE_VERSION);

        try {
            booleanFileDatastore.initialize();
            // TODO(b/259607624): implement a method in the datastore which would support
            // this exact scenario - if the value is null, return default value provided
            // in the parameter (similar to SP apply etc.)
            if (booleanFileDatastore.get(NOTIFICATION_DISPLAYED_ONCE) == null) {
                booleanFileDatastore.put(NOTIFICATION_DISPLAYED_ONCE, false);
            }
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Failed to initialize the File Datastore!", e);
        }

        return booleanFileDatastore;
    }

    private int initializeLoggingValues(Context context) {
        if (DeviceRegionProvider.isEuDevice(context)) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
        } else {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
        }
    }

    /**
     * Represents revoked consent as internally determined by the PP APIs.
     *
     * <p>This is an internal-only exception and is not meant to be returned to external callers.
     */
    public static class RevokedConsentException extends IllegalStateException {
        public static final String REVOKED_CONSENT_ERROR_MESSAGE =
                "Error caused by revoked user consent";

        /** Creates an instance of a {@link RevokedConsentException}. */
        public RevokedConsentException() {
            super(REVOKED_CONSENT_ERROR_MESSAGE);
        }
    }

    private void asyncExecute(Runnable runnable) {
        mExecutor.execute(runnable);
    }
}
