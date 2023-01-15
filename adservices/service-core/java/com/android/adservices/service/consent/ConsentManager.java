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
import android.app.adservices.consent.ConsentParcel;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;

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
 *
 * <p>Currently there are three types of source of truth to store consent data,
 *
 * <ul>
 *   <li>SYSTEM_SERVER_ONLY: Write and read consent from system server only.
 *   <li>PPAPI_ONLY: Write and read consent from PPAPI only.
 *   <li>PPAPI_AND_SYSTEM_SERVER: Write consent to both PPAPI and system server. Read consent from
 *       system server only.
 * </ul>
 */
// TODO(b/259791134): Add a CTS/UI test to test the Consent Migration
public class ConsentManager {
    private static final String ERROR_MESSAGE_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";

    @VisibleForTesting
    static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";

    @VisibleForTesting
    static final String GA_UX_NOTIFICATION_DISPLAYED_ONCE = "GA-UX-NOTIFICATION-DISPLAYED-ONCE";

    @VisibleForTesting
    static final String TOPICS_CONSENT_PAGE_DISPLAYED = "TOPICS-CONSENT-PAGE-DISPLAYED";

    @VisibleForTesting
    static final String FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED =
            "FLEDGE-AND-MSMT-CONSENT-PAGE-DISPLAYED";

    @VisibleForTesting static final String CONSENT_KEY = "CONSENT";
    private static final String ERROR_MESSAGE_WHILE_SET_CONTENT = "setConsent method failed.";
    private static final String ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH =
            "Invalid type of consent source of truth.";
    // Internal datastore version
    @VisibleForTesting static final int STORAGE_VERSION = 1;
    // Internal datastore filename. The name should be unique to avoid multiple threads or processes
    // to update the same file.
    @VisibleForTesting
    static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    // The name of shared preferences file to store status of one-time migrations.
    // Once a migration has happened, it marks corresponding shared preferences to prevent it
    // happens again.
    @VisibleForTesting static final String SHARED_PREFS_CONSENT = "PPAPI_Consent";

    // Shared preferences to mark whether PPAPI consent has been migrated to system server
    @VisibleForTesting
    static final String SHARED_PREFS_KEY_HAS_MIGRATED = "CONSENT_HAS_MIGRATED_TO_SYSTEM_SERVER";

    // Shared preferences to mark whether PPAPI consent has been cleared.
    @VisibleForTesting
    static final String SHARED_PREFS_KEY_PPAPI_HAS_CLEARED = "CONSENT_HAS_CLEARED_IN_PPAPI";

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
    private final int mConsentSourceOfTruth;

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
            @NonNull Flags flags,
            @ConsentParcel.ConsentApiType int consentSourceOfTruth) {
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
        mConsentSourceOfTruth = consentSourceOfTruth;
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
                // Execute one-time consent migration if needed.
                int consentSourceOfTruth = FlagsFactory.getFlags().getConsentSourceOfTruth();
                BooleanFileDatastore datastore = createAndInitializeDataStore(context);
                AdServicesManager adServicesManager = AdServicesManager.getInstance(context);
                handleConsentMigrationIfNeeded(
                        context, datastore, adServicesManager, consentSourceOfTruth);

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
                                    adServicesManager,
                                    datastore,
                                    // TODO(b/260601944): Remove Flag Instance.
                                    FlagsFactory.getFlags(),
                                    consentSourceOfTruth);
                }
            }
        }
        return sConsentManager;
    }

    /**
     * Enables all PP API services. It gives consent to Topics, Fledge and Measurements services.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
     */
    public void enable(@NonNull Context context) {
        Objects.requireNonNull(context);

        mAdServicesLoggerImpl.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(mDeviceLoggingRegion)
                        .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED)
                        .build());

        BackgroundJobsManager.scheduleAllBackgroundJobs(context);

        try {
            // reset all state data which should be removed
            resetTopicsAndBlockedTopics();
            resetAppsAndBlockedApps();
            resetMeasurement();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setConsentToSourceOfTruth(/* isGiven */ true);
    }

    /**
     * Disables all PP API services. It revokes consent to Topics, Fledge and Measurements services.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
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
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setConsentToSourceOfTruth(/* isGiven */ false);
    }

    /**
     * Enables the {@code apiType} PP API service. It gives consent to an API which is provided in
     * the parameter.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
     *
     * @param context Context of the application.
     * @param apiType Type of the API (Topics, Fledge, Measurement) which should be enabled.
     */
    public void enable(@NonNull Context context, AdServicesApiType apiType) {
        Objects.requireNonNull(context);

        // TODO(b/258185102): add missing logging once they are added

        BackgroundJobsManager.scheduleJobsPerApi(context, apiType);

        try {
            // reset all state data which should be removed
            resetByApi(apiType);
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setPerApiConsentToSourceOfTruth(/* isGiven */ true, apiType);
    }

    /**
     * Disables {@code apiType} PP API service. It revokes consent to an API which is provided in
     * the parameter.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
     */
    public void disable(@NonNull Context context, AdServicesApiType apiType) {
        Objects.requireNonNull(context);

        // TODO(b/258185102): add missing logging once they are added

        try {
            resetByApi(apiType);
            BackgroundJobsManager.unscheduleJobsPerApi(
                    context.getSystemService(JobScheduler.class), apiType);
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setPerApiConsentToSourceOfTruth(/* isGiven */ false, apiType);

        if (areAllApisDisabled()) {
            BackgroundJobsManager.unscheduleAllBackgroundJobs(
                    context.getSystemService(JobScheduler.class));
        }
    }

    private boolean areAllApisDisabled() {
        if (getConsent(AdServicesApiType.TOPICS).isGiven()
                || getConsent(AdServicesApiType.MEASUREMENTS).isGiven()
                || getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            return false;
        }
        return true;
    }

    /**
     * Retrieves the consent for all PP API services.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return AdServicesApiConsent the consent
     */
    public AdServicesApiConsent getConsent() {
        if (mFlags.getConsentManagerDebugMode()) {
            return AdServicesApiConsent.GIVEN;
        }

        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return AdServicesApiConsent.getConsent(mDatastore.get(CONSENT_KEY));
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        ConsentParcel consentParcel =
                                mAdServicesManager.getConsent(ConsentParcel.ALL_API);
                        return AdServicesApiConsent.getConsent(consentParcel.isIsGiven());
                    default:
                        LogUtil.e(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return AdServicesApiConsent.REVOKED;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, ERROR_MESSAGE_WHILE_GET_CONTENT);
            }

            return AdServicesApiConsent.REVOKED;
        }
    }

    /**
     * Retrieves the consent per API.
     *
     * @param apiType apiType for which the consent should be provided
     * @return {@link AdServicesApiConsent} providing information whether the consent was given or
     *     revoked.
     */
    public AdServicesApiConsent getConsent(AdServicesApiType apiType) {
        if (!mFlags.getGaUxFeatureEnabled()) {
            throw new IllegalStateException("GA UX feature is disabled.");
        }

        if (mFlags.getConsentManagerDebugMode()) {
            return AdServicesApiConsent.GIVEN;
        }

        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return AdServicesApiConsent.getConsent(
                                mDatastore.get(apiType.toPpApiDatastoreKey()));
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        ConsentParcel consentParcel =
                                mAdServicesManager.getConsent(apiType.toConsentApiType());
                        return AdServicesApiConsent.getConsent(consentParcel.isIsGiven());
                    default:
                        LogUtil.e(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return AdServicesApiConsent.REVOKED;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, ERROR_MESSAGE_WHILE_GET_CONTENT);
            }

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
    @VisibleForTesting
    void resetEnrollment() {
        mEnrollmentDao.deleteAll();
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordNotificationDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordNotificationDisplayed();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
                        mAdServicesManager.recordNotificationDisplayed();
                        break;
                    default:
                        throw new RuntimeException(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record Notification Displayed failed", e);
            }
        }
    }

    /**
     * Retrieves if notification has been displayed.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public Boolean wasNotificationDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(NOTIFICATION_DISPLAYED_ONCE);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.wasNotificationDisplayed();
                    default:
                        LogUtil.e(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get notification failed.");
            }

            return false;
        }
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordGaUxNotificationDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(GA_UX_NOTIFICATION_DISPLAYED_ONCE, true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordGaUxNotificationDisplayed();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(GA_UX_NOTIFICATION_DISPLAYED_ONCE, true);
                        mAdServicesManager.recordGaUxNotificationDisplayed();
                        break;
                    default:
                        throw new RuntimeException(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record GA UX Notification Displayed failed", e);
            }
        }
    }

    /**
     * Retrieves if GA UX notification has been displayed.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    public Boolean wasGaUxNotificationDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.wasGaUxNotificationDisplayed();
                    default:
                        LogUtil.e(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get GA UX notification failed.");
            }

            return false;
        }
    }

    /**
     * Saves information to the storage that topics consent page was displayed for the first time to
     * the user.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordTopicsConsentPageDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(TOPICS_CONSENT_PAGE_DISPLAYED, true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordTopicsConsentPageDisplayed();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(TOPICS_CONSENT_PAGE_DISPLAYED, true);
                        mAdServicesManager.recordTopicsConsentPageDisplayed();
                        break;
                    default:
                        throw new RuntimeException(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record Topics Consent Page Displayed failed", e);
            }
        }
    }

    /**
     * Retrieves if topics consent page has been displayed.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if topics consent page was displayed, otherwise false.
     */
    public Boolean wasTopicsConsentPageDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(TOPICS_CONSENT_PAGE_DISPLAYED);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.wasTopicsConsentPageDisplayed();
                    default:
                        LogUtil.e(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get Topics Consent Page Displayed failed.");
            }

            return false;
        }
    }

    /**
     * Saves information to the storage that fledge and msmt consent page was displayed for the
     * first time to the user.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordFledgeAndMsmtConsentPageDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED, true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordFledgeAndMsmtConsentPageDisplayed();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED, true);
                        mAdServicesManager.recordFledgeAndMsmtConsentPageDisplayed();
                        break;
                    default:
                        throw new RuntimeException(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException(
                        "Record FLEDGE and MSMT Consent Page Displayed failed", e);
            }
        }
    }

    /**
     * Retrieves if fledge and msmt consent page has been displayed.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if fledge and msmt consent page was displayed, otherwise false.
     */
    public Boolean wasFledgeAndMsmtConsentPageDisplayed() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.wasFledgeAndMsmtConsentPageDisplayed();
                    default:
                        LogUtil.e(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get Fledge Consent Page Displayed failed.");
            }

            return false;
        }
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
            if (booleanFileDatastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE) == null) {
                booleanFileDatastore.put(GA_UX_NOTIFICATION_DISPLAYED_ONCE, false);
            }
            if (booleanFileDatastore.get(TOPICS_CONSENT_PAGE_DISPLAYED) == null) {
                booleanFileDatastore.put(TOPICS_CONSENT_PAGE_DISPLAYED, false);
            }
            if (booleanFileDatastore.get(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED) == null) {
                booleanFileDatastore.put(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED, false);
            }
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Failed to initialize the File Datastore!", e);
        }

        return booleanFileDatastore;
    }

    // Handle different migration requests based on current consent source of Truth
    // PPAPI_ONLY: reset the shared preference to reset status of migrating consent from PPAPI to
    //             system server.
    // PPAPI_AND_SYSTEM_SERVER: migrate consent from PPAPI to system server.
    // SYSTEM_SERVER_ONLY: migrate consent from PPAPI to system server and clear PPAPI consent
    @VisibleForTesting
    static void handleConsentMigrationIfNeeded(
            @NonNull Context context,
            @NonNull BooleanFileDatastore datastore,
            @NonNull AdServicesManager adServicesManager,
            @ConsentParcel.ConsentApiType int consentSourceOfTruth) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(datastore);
        Objects.requireNonNull(adServicesManager);

        switch (consentSourceOfTruth) {
            case Flags.PPAPI_ONLY:
                resetSharedPreference(context, SHARED_PREFS_KEY_HAS_MIGRATED);
                break;
            case Flags.PPAPI_AND_SYSTEM_SERVER:
                migratePpApiConsentToSystemService(context, datastore, adServicesManager);
                break;
            case Flags.SYSTEM_SERVER_ONLY:
                migratePpApiConsentToSystemService(context, datastore, adServicesManager);
                clearPpApiConsent(context, datastore);
                break;
            default:
                break;
        }
    }

    @VisibleForTesting
    void setConsentToPpApi(boolean isGiven) throws IOException {
        mDatastore.put(CONSENT_KEY, isGiven);
    }

    @VisibleForTesting
    void setConsentPerApiToPpApi(AdServicesApiType apiType, boolean isGiven) throws IOException {
        mDatastore.put(apiType.toPpApiDatastoreKey(), isGiven);
    }

    // Set the aggregated consent so that after the rollback of the module
    // and the flag which controls the consent flow everything works as expected.
    // The problematic edge case which is covered:
    // T1: AdServices is installed in pre-GA UX version and the consent is given
    // T2: AdServices got upgraded to GA UX binary and GA UX feature flag is enabled
    // T3: Consent for the Topics API got revoked
    // T4: AdServices got rolledback and the feature flags which controls consent flow
    // (SYSTEM_SERVER_ONLY and DUAL_WRITE) also got rolledback
    // T5: Restored consent should be revoked
    @VisibleForTesting
    void setAggregatedConsentToPpApi() throws IOException {
        if (getConsent(AdServicesApiType.TOPICS).isGiven()
                && getConsent(AdServicesApiType.MEASUREMENTS).isGiven()
                && getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            setConsentToPpApi(true);
        } else {
            setConsentToPpApi(false);
        }
    }

    // Reset data for the specific AdServicesApiType
    @VisibleForTesting
    void resetByApi(AdServicesApiType apiType) throws IOException {
        switch (apiType) {
            case TOPICS:
                resetTopicsAndBlockedTopics();
                break;
            case FLEDGE:
                resetAppsAndBlockedApps();
                break;
            case MEASUREMENTS:
                resetMeasurement();
                break;
        }
    }

    @VisibleForTesting
    static void setConsentToSystemServer(
            @NonNull AdServicesManager adServicesManager, boolean isGiven) {
        Objects.requireNonNull(adServicesManager);

        ConsentParcel consentParcel =
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.ALL_API)
                        .setIsGiven(isGiven)
                        .build();
        adServicesManager.setConsent(consentParcel);
    }

    @VisibleForTesting
    static void setPerApiConsentToSystemServer(
            @NonNull AdServicesManager adServicesManager,
            @ConsentParcel.ConsentApiType int consentApiType,
            boolean isGiven) {
        Objects.requireNonNull(adServicesManager);

        if (isGiven) {
            adServicesManager.setConsent(ConsentParcel.createGivenConsent(consentApiType));
        } else {
            adServicesManager.setConsent(ConsentParcel.createRevokedConsent(consentApiType));
        }
    }

    // Perform a one-time migration to migrate existing PPAPI Consent
    @VisibleForTesting
    static void migratePpApiConsentToSystemService(
            @NonNull Context context,
            @NonNull BooleanFileDatastore datastore,
            @NonNull AdServicesManager adServicesManager) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(datastore);
        Objects.requireNonNull(adServicesManager);

        // Exit if migration has happened.
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false)) {
            LogUtil.v(
                    "Consent migration has happened to user %d, skip...",
                    context.getUser().getIdentifier());
            return;
        }
        LogUtil.d("Start migrating Consent from PPAPI to System Service");

        // Migrate Consent and Notification Displayed to System Service.
        // Set consent enabled only when value is TRUE. FALSE and null are regarded as disabled.
        setConsentToSystemServer(
                adServicesManager, Boolean.TRUE.equals(datastore.get(CONSENT_KEY)));

        // Set notification displayed only when value is TRUE. FALSE and null are regarded as
        // not displayed.
        if (Boolean.TRUE.equals(datastore.get(NOTIFICATION_DISPLAYED_ONCE))) {
            adServicesManager.recordNotificationDisplayed();
        }

        // Save migration has happened into shared preferences.
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, true);

        if (editor.commit()) {
            LogUtil.d("Finish migrating Consent from PPAPI to System Service");
        } else {
            LogUtil.e(
                    "Finish migrating Consent from PPAPI to System Service but shared preference is"
                            + " not updated.");
        }
    }

    // Clear PPAPI Consent if fully migrated to use system server consent. This is because system
    // consent cannot be migrated back to PPAPI. This data clearing should only happen once.
    @VisibleForTesting
    static void clearPpApiConsent(
            @NonNull Context context, @NonNull BooleanFileDatastore datastore) {
        // Exit if PPAPI consent has cleared.
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean(
                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false)) {
            return;
        }

        LogUtil.d("Start clearing Consent in PPAPI.");

        try {
            datastore.clear();
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear PPAPI Consent", e);
        }

        // Save that PPAPI consent has cleared into shared preferences.
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);

        if (editor.commit()) {
            LogUtil.d("Finish clearing Consent in PPAPI.");
        } else {
            LogUtil.e("Finish clearing Consent in PPAPI but shared preference is not updated.");
        }
    }

    // Set the shared preference to false for given key.
    @VisibleForTesting
    static void resetSharedPreference(
            @NonNull Context context, @NonNull String sharedPreferenceKey) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(sharedPreferenceKey);

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(sharedPreferenceKey, false);

        if (editor.commit()) {
            LogUtil.d("Finish resetting shared preference for " + sharedPreferenceKey);
        } else {
            LogUtil.e("Failed to reset shared preference for " + sharedPreferenceKey);
        }
    }

    // To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources.
    // To write to system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
    private void setConsentToSourceOfTruth(boolean isGiven) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        setConsentToPpApi(isGiven);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        setConsentToSystemServer(mAdServicesManager, isGiven);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        // Ensure data is consistent in PPAPI and system server.
                        synchronized (ConsentManager.class) {
                            setConsentToPpApi(isGiven);
                            setConsentToSystemServer(mAdServicesManager, isGiven);
                        }
                        break;
                    default:
                        throw new RuntimeException(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
            }
        }
    }

    private void setPerApiConsentToSourceOfTruth(boolean isGiven, AdServicesApiType apiType) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        setConsentPerApiToPpApi(apiType, isGiven);
                        setAggregatedConsentToPpApi();
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        setPerApiConsentToSystemServer(
                                mAdServicesManager, apiType.toConsentApiType(), isGiven);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        // Ensure data is consistent in PPAPI and system server.
                        setConsentPerApiToPpApi(apiType, isGiven);
                        setPerApiConsentToSystemServer(
                                mAdServicesManager, apiType.toConsentApiType(), isGiven);
                        setAggregatedConsentToPpApi();
                        break;
                    default:
                        throw new RuntimeException(ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException(ERROR_MESSAGE_WHILE_SET_CONTENT, e);
            }
        }
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
