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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.appsearch.AppSearchConsentService;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentManager {
    private static volatile ConsentManager sConsentManager;

    @IntDef(value = {NO_MANUAL_INTERACTIONS_RECORDED, UNKNOWN, MANUAL_INTERACTIONS_RECORDED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserManualInteraction {}

    public static final int NO_MANUAL_INTERACTIONS_RECORDED = -1;
    public static final int UNKNOWN = 0;
    public static final int MANUAL_INTERACTIONS_RECORDED = 1;

    private final Context mContext;
    private final Flags mFlags;
    private final TopicsWorker mTopicsWorker;
    private final BooleanFileDatastore mDatastore;
    private final AppConsentDao mAppConsentDao;
    private final EnrollmentDao mEnrollmentDao;
    private final MeasurementImpl mMeasurementImpl;
    private final CustomAudienceDao mCustomAudienceDao;
    private final AppInstallDao mAppInstallDao;
    private final AdServicesManager mAdServicesManager;
    private final int mConsentSourceOfTruth;
    private final AppSearchConsentService mAppSearchConsentService;

    private static final Object LOCK = new Object();

    ConsentManager(
            @NonNull Context context,
            @NonNull TopicsWorker topicsWorker,
            @NonNull AppConsentDao appConsentDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull MeasurementImpl measurementImpl,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AppInstallDao appInstallDao,
            @NonNull AdServicesManager adServicesManager,
            @NonNull BooleanFileDatastore booleanFileDatastore,
            @NonNull AppSearchConsentService appSearchConsentService,
            @NonNull Flags flags,
            @Flags.ConsentSourceOfTruth int consentSourceOfTruth) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(topicsWorker);
        Objects.requireNonNull(appConsentDao);
        Objects.requireNonNull(measurementImpl);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(booleanFileDatastore);

        if (consentSourceOfTruth != Flags.PPAPI_ONLY
                && consentSourceOfTruth != Flags.APPSEARCH_ONLY) {
            Objects.requireNonNull(adServicesManager);
        }

        if (flags.getEnableAppsearchConsentData()) {
            Objects.requireNonNull(appSearchConsentService);
        }

        mContext = context;
        mAdServicesManager = adServicesManager;
        mTopicsWorker = topicsWorker;
        mDatastore = booleanFileDatastore;
        mAppConsentDao = appConsentDao;
        mEnrollmentDao = enrollmentDao;
        mMeasurementImpl = measurementImpl;
        mCustomAudienceDao = customAudienceDao;
        mAppInstallDao = appInstallDao;

        mAppSearchConsentService = appSearchConsentService;
        mFlags = flags;
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
            synchronized (LOCK) {
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
                                    CustomAudienceDatabase.getInstance(context).customAudienceDao(),
                                    SharedStorageDatabase.getInstance(context).appInstallDao(),
                                    adServicesManager,
                                    datastore,
                                    AppSearchConsentService.getInstance(context),
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

        UiStatsLogger.logOptInSelected(context);

        BackgroundJobsManager.scheduleAllBackgroundJobs(context);

        try {
            // reset all state data which should be removed
            resetTopicsAndBlockedTopics();
            resetAppsAndBlockedApps();
            resetMeasurement();
        } catch (IOException e) {
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
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

        UiStatsLogger.logOptOutSelected(context);
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
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
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

        UiStatsLogger.logOptInSelected(context, apiType);

        BackgroundJobsManager.scheduleJobsPerApi(context, apiType);

        try {
            // reset all state data which should be removed
            resetByApi(apiType);
        } catch (IOException e) {
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
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

        UiStatsLogger.logOptOutSelected(context, apiType);

        try {
            resetByApi(apiType);
            BackgroundJobsManager.unscheduleJobsPerApi(
                    context.getSystemService(JobScheduler.class), apiType);
        } catch (IOException e) {
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
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

        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return AdServicesApiConsent.getConsent(
                                mDatastore.get(ConsentConstants.CONSENT_KEY));
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        ConsentParcel consentParcel =
                                mAdServicesManager.getConsent(ConsentParcel.ALL_API);
                        return AdServicesApiConsent.getConsent(consentParcel.isIsGiven());
                        // This is the default for back compat. All consent data is written to and
                        // read from AppSearch on S- devices.
                    case Flags.APPSEARCH_ONLY:
                        return AdServicesApiConsent.getConsent(
                                mAppSearchConsentService.getConsent(ConsentConstants.CONSENT_KEY));
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return AdServicesApiConsent.REVOKED;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, ConsentConstants.ERROR_MESSAGE_WHILE_GET_CONTENT);
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

        synchronized (LOCK) {
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
                    case Flags.APPSEARCH_ONLY:
                        return AdServicesApiConsent.getConsent(
                                mAppSearchConsentService.getConsent(apiType.toPpApiDatastoreKey()));
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return AdServicesApiConsent.REVOKED;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, ConsentConstants.ERROR_MESSAGE_WHILE_GET_CONTENT);
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
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        try {
                            return ImmutableList.copyOf(
                                    mAppConsentDao.getKnownAppsWithConsent().stream()
                                            .map(App::create)
                                            .collect(Collectors.toList()));
                        } catch (IOException e) {
                            LogUtil.e(e, "getKnownAppsWithConsent failed due to IOException.");
                        }
                        return ImmutableList.of();
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return ImmutableList.copyOf(
                                mAdServicesManager
                                        .getKnownAppsWithConsent(
                                                new ArrayList<>(
                                                        mAppConsentDao.getInstalledPackages()))
                                        .stream()
                                        .map(App::create)
                                        .collect(Collectors.toList()));
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return ImmutableList.of();
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Error get known apps with consent.");
            }
            return ImmutableList.of();
        }
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked
     */
    public ImmutableList<App> getAppsWithRevokedConsent() {
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        try {
                            return ImmutableList.copyOf(
                                    mAppConsentDao.getAppsWithRevokedConsent().stream()
                                            .map(App::create)
                                            .collect(Collectors.toList()));
                        } catch (IOException e) {
                            LogUtil.e(e, "getAppsWithRevokedConsent() failed due to IOException.");
                        }
                        return ImmutableList.of();
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return ImmutableList.copyOf(
                                mAdServicesManager
                                        .getAppsWithRevokedConsent(
                                                new ArrayList<>(
                                                        mAppConsentDao.getInstalledPackages()))
                                        .stream()
                                        .map(App::create)
                                        .collect(Collectors.toList()));
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return ImmutableList.of();
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Error get apps with revoked consent.");
            }
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
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mAppConsentDao.setConsentForApp(app.getPackageName(), true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.setConsentForApp(
                                app.getPackageName(),
                                mAppConsentDao.getUidForInstalledPackageName(app.getPackageName()),
                                true);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mAppConsentDao.setConsentForApp(app.getPackageName(), true);
                        mAdServicesManager.setConsentForApp(
                                app.getPackageName(),
                                mAppConsentDao.getUidForInstalledPackageName(app.getPackageName()),
                                true);
                        break;
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Error revoke consent for app %s", app.getPackageName());
            }
        }
        asyncExecute(
                () -> mCustomAudienceDao.deleteCustomAudienceDataByOwner(app.getPackageName()));
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(() -> mAppInstallDao.deleteByPackageName(app.getPackageName()));
        }
    }

    /**
     * Proxy call to {@link AppConsentDao} to restore consent for provided {@link App}.
     *
     * @param app {@link App} to restore consent for.
     * @throws IOException if the operation fails
     */
    public void restoreConsentForApp(@NonNull App app) throws IOException {
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mAppConsentDao.setConsentForApp(app.getPackageName(), false);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.setConsentForApp(
                                app.getPackageName(),
                                mAppConsentDao.getUidForInstalledPackageName(app.getPackageName()),
                                false);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mAppConsentDao.setConsentForApp(app.getPackageName(), false);
                        mAdServicesManager.setConsentForApp(
                                app.getPackageName(),
                                mAppConsentDao.getUidForInstalledPackageName(app.getPackageName()),
                                false);
                        break;
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Error restore consent for app %s", app.getPackageName());
            }
        }
    }

    /**
     * Deletes all app consent data and all app data gathered or generated by the Privacy Sandbox.
     *
     * <p>This should be called when the Privacy Sandbox has been disabled.
     *
     * @throws IOException if the operation fails
     */
    public void resetAppsAndBlockedApps() throws IOException {
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mAppConsentDao.clearAllConsentData();
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.clearAllAppConsentData();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mAppConsentDao.clearAllConsentData();
                        mAdServicesManager.clearAllAppConsentData();
                        break;
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Error reset apps and blocked apps.");
            }
        }
        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(mAppInstallDao::deleteAllAppInstallData);
        }
    }

    /**
     * Deletes the list of known allowed apps as well as all app data from the Privacy Sandbox.
     *
     * <p>The list of blocked apps is not reset.
     *
     * @throws IOException if the operation fails
     */
    public void resetApps() throws IOException {
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mAppConsentDao.clearKnownAppsWithConsent();
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.clearKnownAppsWithConsent();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mAppConsentDao.clearKnownAppsWithConsent();
                        mAdServicesManager.clearKnownAppsWithConsent();
                        break;
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Error reset apps.");
            }
        }
        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(mAppInstallDao::deleteAllAppInstallData);
        }
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
        AdServicesApiConsent consent;
        if (!mFlags.getGaUxFeatureEnabled()) {
            consent = getConsent();
        } else {
            consent = getConsent(AdServicesApiType.FLEDGE);
        }

        if (!consent.isGiven()) {
            return true;
        }

        synchronized (LOCK) {
            switch (mConsentSourceOfTruth) {
                case Flags.PPAPI_ONLY:
                    try {
                        return mAppConsentDao.isConsentRevokedForApp(packageName);
                    } catch (IOException exception) {
                        LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
                    }
                    return true;
                case Flags.SYSTEM_SERVER_ONLY:
                    // Intentional fallthrough
                case Flags.PPAPI_AND_SYSTEM_SERVER:
                    return mAdServicesManager.isConsentRevokedForApp(
                            packageName, mAppConsentDao.getUidForInstalledPackageName(packageName));
                case Flags.APPSEARCH_ONLY:
                default:
                    LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                    return true;
            }
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
        AdServicesApiConsent consent;
        if (!mFlags.getGaUxFeatureEnabled()) {
            consent = getConsent();
        } else {
            consent = getConsent(AdServicesApiType.FLEDGE);
        }

        if (!consent.isGiven()) {
            return true;
        }

        synchronized (LOCK) {
            switch (mConsentSourceOfTruth) {
                case Flags.PPAPI_ONLY:
                    try {
                        return mAppConsentDao.setConsentForAppIfNew(packageName, false);
                    } catch (IOException exception) {
                        LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
                        return true;
                    }
                case Flags.SYSTEM_SERVER_ONLY:
                    return mAdServicesManager.setConsentForAppIfNew(
                            packageName,
                            mAppConsentDao.getUidForInstalledPackageName(packageName),
                            false);
                case Flags.PPAPI_AND_SYSTEM_SERVER:
                    try {
                        mAppConsentDao.setConsentForAppIfNew(packageName, false);
                    } catch (IOException exception) {
                        LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
                        return true;
                    }
                    return mAdServicesManager.setConsentForAppIfNew(
                            packageName,
                            mAppConsentDao.getUidForInstalledPackageName(packageName),
                            false);
                case Flags.APPSEARCH_ONLY:
                default:
                    LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                    return true;
            }
        }
    }

    /**
     * Clear consent data after an app was uninstalled.
     *
     * @param packageName the package name that had been uninstalled.
     * @param packageUid the package uid that had been uninstalled.
     */
    public void clearConsentForUninstalledApp(String packageName, int packageUid) {
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        try {
                            mAppConsentDao.clearConsentForUninstalledApp(packageName, packageUid);
                        } catch (IOException exception) {
                            LogUtil.e(
                                    exception,
                                    "Clear consent for uninstalled app %s and uid %d failed due to"
                                            + " IOException",
                                    packageName,
                                    packageUid);
                        }
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.clearConsentForUninstalledApp(packageName, packageUid);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        try {
                            mAppConsentDao.clearConsentForUninstalledApp(packageName, packageUid);
                        } catch (IOException exception) {
                            LogUtil.e(
                                    exception,
                                    "Clear consent for uninstalled app %s and uid %d failed due to"
                                            + " IOException",
                                    packageName,
                                    packageUid);
                        }
                        mAdServicesManager.clearConsentForUninstalledApp(packageName, packageUid);
                        break;
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                LogUtil.e(
                        e,
                        "Error clear consent for uninstalled app %s and uid %d.",
                        packageName,
                        packageUid);
            }
        }
    }

    /**
     * Clear consent data after an app was uninstalled, but the package Uid is unavailable. This
     * could happen because the INTERACT_ACROSS_USERS_FULL permission is not available on Android
     * versions prior to T.
     *
     * <p><strong>This method should only be used for R/S back-compat scenarios.</strong>
     *
     * @param packageName the package name that had been uninstalled.
     */
    public void clearConsentForUninstalledApp(@NonNull String packageName) {
        Objects.requireNonNull(packageName);
        Preconditions.checkStringNotEmpty(packageName, "Package name should not be empty");

        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        try {
                            mAppConsentDao.clearConsentForUninstalledApp(packageName);
                        } catch (IOException exception) {
                            LogUtil.e(
                                    exception,
                                    "Clear consent for uninstalled app %s failed due to"
                                            + " IOException",
                                    packageName);
                        }
                        break;
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Error clear consent for uninstalled app %s.", packageName);
            }
        }
    }

    /** Wipes out all the data gathered by Measurement API. */
    public void resetMeasurement() {
        UiStatsLogger.logResetMeasurement(mContext);
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
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE, true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordNotificationDisplayed();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE, true);
                        mAdServicesManager.recordNotificationDisplayed();
                        break;
                    case Flags.APPSEARCH_ONLY:
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
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
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.wasNotificationDisplayed();
                    case Flags.APPSEARCH_ONLY:
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
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
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE, true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordGaUxNotificationDisplayed();
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE, true);
                        mAdServicesManager.recordGaUxNotificationDisplayed();
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
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
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.wasGaUxNotificationDisplayed();
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get GA UX notification failed.");
            }
            return false;
        }
    }

    /**
     * Retrieves the PP API default consent.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if the topics default consent is true, false otherwise.
     */
    public Boolean getDefaultConsent() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(ConsentConstants.DEFAULT_CONSENT);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.getDefaultConsent();
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get PP API default consent failed.");
            }
            return false;
        }
    }

    /**
     * Retrieves the topics default consent.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if the topics default consent is true, false otherwise.
     */
    public Boolean getTopicsDefaultConsent() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(ConsentConstants.TOPICS_DEFAULT_CONSENT);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.getTopicsDefaultConsent();
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get topics default consent failed.");
            }
            return false;
        }
    }

    /**
     * Retrieves the FLEDGE default consent.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if the FLEDGE default consent is true, false otherwise.
     */
    public Boolean getFledgeDefaultConsent() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(ConsentConstants.FLEDGE_DEFAULT_CONSENT);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.getFledgeDefaultConsent();
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get FLEDGE default consent failed.");
            }
            return false;
        }
    }

    /**
     * Retrieves the measurement default consent.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if the measurement default consent is true, false otherwise.
     */
    public Boolean getMeasurementDefaultConsent() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.getMeasurementDefaultConsent();
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get measurement default consent failed.");
            }
            return false;
        }
    }

    /**
     * Retrieves the default AdId state.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return true if the AdId is enabled by default, false otherwise.
     */
    public Boolean getDefaultAdIdState() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mDatastore.get(ConsentConstants.DEFAULT_AD_ID_STATE);
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.getDefaultAdIdState();
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return false;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get default AdId state failed.");
            }
            return false;
        }
    }

    /**
     * Saves the PP API default consent bit to storage.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordDefaultConsent(boolean defaultConsent) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(ConsentConstants.DEFAULT_CONSENT, defaultConsent);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordDefaultConsent(defaultConsent);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(ConsentConstants.DEFAULT_CONSENT, defaultConsent);
                        mAdServicesManager.recordDefaultConsent(defaultConsent);
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record default consent failed", e);
            }
        }
    }

    /**
     * Saves the topics default consent bit to storage.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordTopicsDefaultConsent(boolean defaultConsent) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(ConsentConstants.TOPICS_DEFAULT_CONSENT, defaultConsent);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordTopicsDefaultConsent(defaultConsent);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(ConsentConstants.TOPICS_DEFAULT_CONSENT, defaultConsent);
                        mAdServicesManager.recordTopicsDefaultConsent(defaultConsent);
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record topics default consent failed", e);
            }
        }
    }

    /**
     * Saves the FLEDGE default consent bit to storage.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordFledgeDefaultConsent(boolean defaultConsent) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(ConsentConstants.FLEDGE_DEFAULT_CONSENT, defaultConsent);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordFledgeDefaultConsent(defaultConsent);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(ConsentConstants.FLEDGE_DEFAULT_CONSENT, defaultConsent);
                        mAdServicesManager.recordFledgeDefaultConsent(defaultConsent);
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record FLEDGE default consent failed", e);
            }
        }
    }

    /**
     * Saves the measurement default consent bit to storage.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordMeasurementDefaultConsent(boolean defaultConsent) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(
                                ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, defaultConsent);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordMeasurementDefaultConsent(defaultConsent);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(
                                ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, defaultConsent);
                        mAdServicesManager.recordMeasurementDefaultConsent(defaultConsent);
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record measurement default consent failed", e);
            }
        }
    }

    /**
     * Saves the default AdId state.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void recordDefaultAdIdState(boolean defaultAdIdState) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(ConsentConstants.DEFAULT_AD_ID_STATE, defaultAdIdState);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordDefaultAdIdState(defaultAdIdState);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(ConsentConstants.DEFAULT_AD_ID_STATE, defaultAdIdState);
                        mAdServicesManager.recordDefaultAdIdState(defaultAdIdState);
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record default AdId state failed", e);
            }
        }
    }

    /**
     * Set the current privacy sandbox feature.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public void setCurrentPrivacySandboxFeature(PrivacySandboxFeatureType currentFeatureType) {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mDatastore.put(currentFeatureType.name(), true);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.setCurrentPrivacySandboxFeature(
                                currentFeatureType.name());
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mDatastore.put(currentFeatureType.name(), true);
                        mAdServicesManager.setCurrentPrivacySandboxFeature(
                                currentFeatureType.name());
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Set current privacy sandbox feature failed.", e);
            }
        }
    }

    /** Saves information to the storage that user interacted with consent manually. */
    public void recordUserManualInteractionWithConsent(@UserManualInteraction int interaction) {
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        storeUserManualInteractionToPpApi(interaction);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordUserManualInteractionWithConsent(interaction);
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        storeUserManualInteractionToPpApi(interaction);
                        mAdServicesManager.recordUserManualInteractionWithConsent(interaction);
                        break;
                    case Flags.APPSEARCH_ONLY:
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Record manual interaction with consent failed", e);
            }
        }
    }

    /**
     * Get the current privacy sandbox feature.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public PrivacySandboxFeatureType getCurrentPrivacySandboxFeature() {
        synchronized (ConsentManager.class) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        for (PrivacySandboxFeatureType featureType :
                                PrivacySandboxFeatureType.values()) {
                            if (mDatastore.get(featureType.name())) {
                                return featureType;
                            }
                        }
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        for (PrivacySandboxFeatureType featureType :
                                PrivacySandboxFeatureType.values()) {
                            if (mAdServicesManager
                                    .getCurrentPrivacySandboxFeature()
                                    .equals(featureType.name())) {
                                return featureType;
                            }
                        }
                        break;
                    case Flags.APPSEARCH_ONLY:
                        break;
                    default:
                        LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                        return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Get privacy sandbox feature failed.");
            }
            return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
        }
    }

    private void storeUserManualInteractionToPpApi(@UserManualInteraction int interaction)
            throws IOException {
        switch (interaction) {
            case NO_MANUAL_INTERACTIONS_RECORDED:
                mDatastore.put(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED, false);
                break;
            case UNKNOWN:
                mDatastore.remove(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
                break;
            case MANUAL_INTERACTIONS_RECORDED:
                mDatastore.put(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED, true);
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("InteractionId < %d > can not be handled.", interaction));
        }
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return true if the user interacted with the consent manually, otherwise false.
     */
    public @UserManualInteraction int getUserManualInteractionWithConsent() {
        synchronized (LOCK) {
            try {
                switch (mConsentSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        Boolean manualInteractionWithConsent =
                                mDatastore.get(
                                        ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
                        if (manualInteractionWithConsent == null) {
                            return UNKNOWN;
                        } else if (Boolean.TRUE.equals(manualInteractionWithConsent)) {
                            return MANUAL_INTERACTIONS_RECORDED;
                        } else {
                            return NO_MANUAL_INTERACTIONS_RECORDED;
                        }
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.getUserManualInteractionWithConsent();
                    case Flags.APPSEARCH_ONLY:
                        return UNKNOWN;
                    default:
                        LogUtil.e(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
                        return UNKNOWN;
                }
            } catch (RuntimeException e) {
                LogUtil.e(e, "Record manual interaction with consent failed.");
            }

            return UNKNOWN;
        }
    }

    @VisibleForTesting
    static BooleanFileDatastore createAndInitializeDataStore(@NonNull Context context) {
        BooleanFileDatastore booleanFileDatastore =
                new BooleanFileDatastore(
                        context,
                        ConsentConstants.STORAGE_XML_IDENTIFIER,
                        ConsentConstants.STORAGE_VERSION);

        try {
            booleanFileDatastore.initialize();
            // TODO(b/259607624): implement a method in the datastore which would support
            // this exact scenario - if the value is null, return default value provided
            // in the parameter (similar to SP apply etc.)
            if (booleanFileDatastore.get(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE) == null) {
                booleanFileDatastore.put(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE, false);
            }
            if (booleanFileDatastore.get(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE)
                    == null) {
                booleanFileDatastore.put(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE, false);
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
            AdServicesManager adServicesManager,
            @Flags.ConsentSourceOfTruth int consentSourceOfTruth) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(datastore);
        if (consentSourceOfTruth != Flags.PPAPI_ONLY
                && consentSourceOfTruth != Flags.APPSEARCH_ONLY) {
            Objects.requireNonNull(adServicesManager);
        }

        switch (consentSourceOfTruth) {
            case Flags.PPAPI_ONLY:
                resetSharedPreference(context, ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED);
                break;
            case Flags.PPAPI_AND_SYSTEM_SERVER:
                migratePpApiConsentToSystemService(context, datastore, adServicesManager);
                break;
            case Flags.SYSTEM_SERVER_ONLY:
                migratePpApiConsentToSystemService(context, datastore, adServicesManager);
                clearPpApiConsent(context, datastore);
                break;
            case Flags.APPSEARCH_ONLY:
            default:
                break;
        }
    }

    @VisibleForTesting
    void setConsentToPpApi(boolean isGiven) throws IOException {
        mDatastore.put(ConsentConstants.CONSENT_KEY, isGiven);
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
                context.getSharedPreferences(
                        ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean(
                ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false)) {
            LogUtil.v(
                    "Consent migration has happened to user %d, skip...",
                    context.getUser().getIdentifier());
            return;
        }
        LogUtil.d("Start migrating Consent from PPAPI to System Service");

        // Migrate Consent and Notification Displayed to System Service.
        // Set consent enabled only when value is TRUE. FALSE and null are regarded as disabled.
        setConsentToSystemServer(
                adServicesManager,
                Boolean.TRUE.equals(datastore.get(ConsentConstants.CONSENT_KEY)));

        // Set notification displayed only when value is TRUE. FALSE and null are regarded as
        // not displayed.
        if (Boolean.TRUE.equals(datastore.get(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE))) {
            adServicesManager.recordNotificationDisplayed();
        }

        Boolean manualInteractionRecorded =
                datastore.get(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
        if (manualInteractionRecorded != null) {
            adServicesManager.recordUserManualInteractionWithConsent(
                    manualInteractionRecorded ? 1 : -1);
        }

        // Save migration has happened into shared preferences.
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED, true);

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
                context.getSharedPreferences(
                        ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean(
                ConsentConstants.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false)) {
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
        editor.putBoolean(ConsentConstants.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);

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
                context.getSharedPreferences(
                        ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
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
    @VisibleForTesting
    void setConsentToSourceOfTruth(boolean isGiven) {
        synchronized (LOCK) {
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
                        setConsentToPpApi(isGiven);
                        setConsentToSystemServer(mAdServicesManager, isGiven);
                        break;
                    case Flags.APPSEARCH_ONLY:
                        mAppSearchConsentService.setConsent(ConsentConstants.CONSENT_KEY, isGiven);
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
            }
        }
    }

    @VisibleForTesting
    void setPerApiConsentToSourceOfTruth(boolean isGiven, AdServicesApiType apiType) {
        synchronized (LOCK) {
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
                    case Flags.APPSEARCH_ONLY:
                        mAppSearchConsentService.setConsent(apiType.toPpApiDatastoreKey(), isGiven);
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                }
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
            }
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
        AdServicesExecutors.getBackgroundExecutor().execute(runnable);
    }
}
