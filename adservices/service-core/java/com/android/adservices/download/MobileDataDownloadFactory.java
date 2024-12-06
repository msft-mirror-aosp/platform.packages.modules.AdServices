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

package com.android.adservices.download;

import static com.android.adservices.service.topics.classifier.ModelManager.BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.classifier.CommonClassifierHelper;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.downloader.AndroidDownloaderLogger;
import com.google.android.downloader.ConnectivityHandler;
import com.google.android.downloader.DownloadConstraints;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.PlatformUrlEngine;
import com.google.android.downloader.UrlEngine;
import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
import com.google.android.libraries.mobiledatadownload.TimeSource;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.ExceptionHandler;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.Offroad2FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.backends.AndroidFileBackend;
import com.google.android.libraries.mobiledatadownload.file.backends.JavaFileBackend;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.DownloadMetadataStore;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.SharedPreferencesDownloadMetadata;
import com.google.android.libraries.mobiledatadownload.monitor.NetworkUsageMonitor;
import com.google.android.libraries.mobiledatadownload.populator.ManifestConfigFileParser;
import com.google.android.libraries.mobiledatadownload.populator.ManifestConfigOverrider;
import com.google.android.libraries.mobiledatadownload.populator.ManifestFileGroupPopulator;
import com.google.android.libraries.mobiledatadownload.populator.SharedPreferencesManifestFileMetadata;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mobiledatadownload.DownloadConfigProto;
import com.google.mobiledatadownload.DownloadConfigProto.ManifestFileFlag;
import com.google.protobuf.MessageLite;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Mobile Data Download Factory. */
@RequiresApi(Build.VERSION_CODES.S)
public class MobileDataDownloadFactory {
    private static MobileDataDownload sSingletonMdd;
    private static SynchronousFileStorage sSynchronousFileStorage;

    private static final String MDD_METADATA_SHARED_PREFERENCES = "mdd_metadata_store";
    private static final String TOPICS_MANIFEST_ID = "TopicsManifestId";
    private static final String MEASUREMENT_MANIFEST_ID = "MeasurementManifestId";
    private static final String ENCRYPTION_KEYS_MANIFEST_ID = "EncryptionKeysManifestId";
    private static final String UI_OTA_STRINGS_MANIFEST_ID = "UiOtaStringsManifestId";
    private static final String UI_OTA_RESOURCES_MANIFEST_ID = "UiOtaResourcesManifestId";
    private static final String ENROLLMENT_PROTO_MANIFEST_ID = "EnrollmentProtoManifestId";
    private static final String COBALT_REGISTRY_MANIFEST_ID = "CobaltRegistryManifestId";
    private static final String DENY_PACKAGE_MANIFEST_ID = "DenyPackageManifestId";
    private static final int MAX_ADB_LOGCAT_SIZE = 4000;

    /** Returns a singleton of MobileDataDownload for the whole PPAPI app. */
    public static MobileDataDownload getMdd(Flags flags) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSingletonMdd == null) {
                // TODO(b/236761740): This only adds the core MDD code. We still need other
                //  components:
                // Add Logger
                // Add Configurator.

                Context context = ApplicationContextSingleton.get();
                SynchronousFileStorage fileStorage = getFileStorage();
                FileDownloader fileDownloader = getFileDownloader(flags, fileStorage);
                NetworkUsageMonitor networkUsageMonitor =
                        new NetworkUsageMonitor(
                                context,
                                new TimeSource() {
                                    @Override
                                    public long currentTimeMillis() {
                                        return System.currentTimeMillis();
                                    }

                                    @Override
                                    public long elapsedRealtimeNanos() {
                                        return SystemClock.elapsedRealtimeNanos();
                                    }
                                });

                MobileDataDownloadBuilder mobileDataDownloadBuilder =
                        MobileDataDownloadBuilder.newBuilder()
                                .setContext(context)
                                .setControlExecutor(getControlExecutor())
                                .setNetworkUsageMonitor(networkUsageMonitor)
                                .setFileStorage(fileStorage)
                                .setFileDownloaderSupplier(() -> fileDownloader)
                                .addFileGroupPopulator(
                                        getTopicsManifestPopulator(
                                                flags, fileStorage, fileDownloader))
                                .addFileGroupPopulator(
                                        getMeasurementManifestPopulator(
                                                flags,
                                                fileStorage,
                                                fileDownloader,
                                                /* getProto= */ false))
                                .addFileGroupPopulator(
                                        getEncryptionKeysManifestPopulator(
                                                context, flags, fileStorage, fileDownloader))
                                .addFileGroupPopulator(
                                        getUiOtaResourcesManifestPopulator(
                                                flags, fileStorage, fileDownloader))
                                .addFileGroupPopulator(
                                        getCobaltRegistryManifestPopulator(
                                                flags, fileStorage, fileDownloader))
                                .addFileGroupPopulator(
                                        getDenyPackageManifestPopulator(
                                                flags, fileStorage, fileDownloader))
                                .setLoggerOptional(getMddLogger(flags))
                                .setFlagsOptional(Optional.of(MddFlags.getInstance()));

                if (flags.getEnrollmentProtoFileEnabled()) {
                    mobileDataDownloadBuilder.addFileGroupPopulator(
                            getMeasurementManifestPopulator(
                                    flags, fileStorage, fileDownloader, /* getProto= */ true));
                }

                sSingletonMdd = mobileDataDownloadBuilder.build();
            }

            return sSingletonMdd;
        }
    }

    // Connectivity constraints will be checked by JobScheduler/WorkManager instead.
    private static class NoOpConnectivityHandler implements ConnectivityHandler {
        @Override
        public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
            return Futures.immediateVoidFuture();
        }
    }

    /** Return a singleton of {@link SynchronousFileStorage}. */
    public static SynchronousFileStorage getFileStorage() {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSynchronousFileStorage == null) {
                Context context = ApplicationContextSingleton.get();
                sSynchronousFileStorage =
                        new SynchronousFileStorage(
                                ImmutableList.of(
                                        /*backends*/ AndroidFileBackend.builder(context).build(),
                                        new JavaFileBackend()),
                                ImmutableList.of(/*transforms*/ ),
                                ImmutableList.of(/*monitors*/ ));
            }
            return sSynchronousFileStorage;
        }
    }

    @VisibleForTesting
    static ListeningExecutorService getControlExecutor() {
        return AdServicesExecutors.getBackgroundExecutor();
    }

    private static Executor getDownloadExecutor() {
        return AdServicesExecutors.getBackgroundExecutor();
    }

    private static UrlEngine getUrlEngine(Flags flags) {
        // TODO(b/219594618): Switch to use CronetUrlEngine.
        return new PlatformUrlEngine(
                AdServicesExecutors.getBlockingExecutor(),
                /* connectTimeoutMs= */ flags.getDownloaderConnectionTimeoutMs(),
                /* readTimeoutMs= */ flags.getDownloaderReadTimeoutMs());
    }

    private static ExceptionHandler getExceptionHandler() {
        return ExceptionHandler.withDefaultHandling();
    }

    @VisibleForTesting
    static FileDownloader getFileDownloader(Flags flags, SynchronousFileStorage fileStorage) {
        DownloadMetadataStore downloadMetadataStore = getDownloadMetadataStore();

        Downloader downloader =
                new Downloader.Builder()
                        .withIOExecutor(AdServicesExecutors.getBlockingExecutor())
                        .withConnectivityHandler(new NoOpConnectivityHandler())
                        .withMaxConcurrentDownloads(flags.getDownloaderMaxDownloadThreads())
                        .withLogger(new AndroidDownloaderLogger())
                        .addUrlEngine("https", getUrlEngine(flags))
                        .build();

        return new Offroad2FileDownloader(
                downloader,
                fileStorage,
                getDownloadExecutor(),
                /* authTokenProvider */ null,
                downloadMetadataStore,
                getExceptionHandler(),
                Optional.absent());
    }

    private static DownloadMetadataStore getDownloadMetadataStore() {
        Context context = ApplicationContextSingleton.get();
        @SuppressWarnings("AvoidSharedPreferences") // Legacy usage
        SharedPreferences sharedPrefs =
                context.getSharedPreferences(MDD_METADATA_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        DownloadMetadataStore downloadMetadataStore =
                new SharedPreferencesDownloadMetadata(
                        sharedPrefs, AdServicesExecutors.getBackgroundExecutor());
        return downloadMetadataStore;
    }

    // Create the Manifest File Group Populator for Topics Classifier.
    @VisibleForTesting
    static ManifestFileGroupPopulator getTopicsManifestPopulator(
            Flags flags, SynchronousFileStorage fileStorage, FileDownloader fileDownloader) {
        Context context = ApplicationContextSingleton.get();

        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(TOPICS_MANIFEST_ID)
                        .setManifestFileUrl(flags.getMddTopicsClassifierManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        // Only download Topics classifier model when Mdd model build_id is greater than bundled
        // model.
        ManifestConfigOverrider manifestConfigOverrider =
                manifestConfig -> {
                    List<DownloadConfigProto.DataFileGroup> groups = new ArrayList<>();
                    for (DownloadConfigProto.ManifestConfig.Entry entry :
                            manifestConfig.getEntryList()) {
                        long dataFileGroupBuildId = entry.getDataFileGroup().getBuildId();
                        long bundledModelBuildId =
                                CommonClassifierHelper.getBundledModelBuildId(
                                        context, BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH);
                        if (dataFileGroupBuildId > bundledModelBuildId) {
                            groups.add(entry.getDataFileGroup());
                            LogUtil.d("Added topics classifier file group to MDD");
                        } else {
                            LogUtil.d(
                                    "Topics Classifier's Bundled BuildId = %d is bigger than or"
                                            + " equal to the BuildId = %d from Server side, "
                                            + "skipping"
                                            + " the downloading.",
                                    bundledModelBuildId, dataFileGroupBuildId);
                        }
                    }
                    return Futures.immediateFuture(groups);
                };

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                .setEnabledSupplier(
                        () -> {
                            // Topics is permanently disabled for U18 UX.
                            if (UxStatesManager.getInstance().getUx()
                                    == PrivacySandboxUxCollection.U18_UX) {
                                return false;
                            }

                            // Topics resources should not be downloaded pre-consent.
                            if (flags.getGaUxFeatureEnabled()) {
                                return ConsentManager.getInstance()
                                        .getConsent(AdServicesApiType.TOPICS)
                                        .isGiven();
                            } else {
                                return ConsentManager.getInstance().getConsent().isGiven();
                            }
                        })
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable Dedup using Etag.
                .setDedupDownloadWithEtag(false)
                .setOverriderOptional(Optional.of(manifestConfigOverrider))
                // TODO(b/243829623): use proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    @VisibleForTesting
    static ManifestFileGroupPopulator getUiOtaResourcesManifestPopulator(
            Flags flags, SynchronousFileStorage fileStorage, FileDownloader fileDownloader) {
        Context context = ApplicationContextSingleton.get();

        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(
                                flags.getUiOtaResourcesFeatureEnabled()
                                        ? UI_OTA_RESOURCES_MANIFEST_ID
                                        : UI_OTA_STRINGS_MANIFEST_ID)
                        .setManifestFileUrl(
                                flags.getUiOtaResourcesFeatureEnabled()
                                        ? flags.getUiOtaResourcesManifestFileUrl()
                                        : flags.getUiOtaStringsManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                // OTA resources can be downloaded pre-consent before notification, or with consent
                .setEnabledSupplier(
                        () ->
                                !ConsentManager.getInstance().wasGaUxNotificationDisplayed()
                                        || isAnyConsentGiven(flags))
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable dedup using etag.
                .setDedupDownloadWithEtag(false)
                // TODO(b/236761740): user proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    private static boolean isAnyConsentGiven(Flags flags) {
        ConsentManager instance = ConsentManager.getInstance();
        if (flags.getGaUxFeatureEnabled()
                && (instance.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()
                        || instance.getConsent(AdServicesApiType.TOPICS).isGiven()
                        || instance.getConsent(AdServicesApiType.FLEDGE).isGiven())) {
            return true;
        }

        return instance.getConsent().isGiven();
    }

    @VisibleForTesting
    static ManifestFileGroupPopulator getMeasurementManifestPopulator(
            Flags flags,
            SynchronousFileStorage fileStorage,
            FileDownloader fileDownloader,
            boolean getProto) {
        Context context = ApplicationContextSingleton.get();

        String manifestId = getProto ? ENROLLMENT_PROTO_MANIFEST_ID : MEASUREMENT_MANIFEST_ID;
        String fileUrl =
                getProto
                        ? flags.getMddEnrollmentManifestFileUrl()
                        : flags.getMeasurementManifestFileUrl();

        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(manifestId)
                        .setManifestFileUrl(fileUrl)
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                // measurement resources should not be downloaded pre-consent
                .setEnabledSupplier(
                        () -> {
                            if (flags.getGaUxFeatureEnabled()) {
                                return isAnyConsentGiven(flags);
                            } else {
                                return ConsentManager.getInstance().getConsent().isGiven();
                            }
                        })
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable dedup using etag.
                .setDedupDownloadWithEtag(false)
                // TODO(b/243829623): use proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    @VisibleForTesting
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    static ManifestFileGroupPopulator getEncryptionKeysManifestPopulator(
            Context context,
            Flags flags,
            SynchronousFileStorage fileStorage,
            FileDownloader fileDownloader) {
        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(ENCRYPTION_KEYS_MANIFEST_ID)
                        .setManifestFileUrl(flags.getMddEncryptionKeysManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                // encryption key resources should not be downloaded pre-consent
                .setEnabledSupplier(
                        () -> {
                            if (flags.getGaUxFeatureEnabled()) {
                                return isAnyConsentGiven(flags);
                            } else {
                                return ConsentManager.getInstance().getConsent().isGiven();
                            }
                        })
                .setEnabledSupplier(flags::getEnableMddEncryptionKeys)
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable dedup using etag.
                .setDedupDownloadWithEtag(false)
                // TODO(b/243829623): use proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    @VisibleForTesting
    static ManifestFileGroupPopulator getCobaltRegistryManifestPopulator(
            Flags flags, SynchronousFileStorage fileStorage, FileDownloader fileDownloader) {
        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(COBALT_REGISTRY_MANIFEST_ID)
                        .setManifestFileUrl(flags.getMddCobaltRegistryManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        Context context = ApplicationContextSingleton.get();

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                // cobalt registry should not be downloaded pre-consent
                .setEnabledSupplier(
                        () -> {
                            if (flags.getGaUxFeatureEnabled()) {
                                return isAnyConsentGiven(flags);
                            } else {
                                return ConsentManager.getInstance().getConsent().isGiven();
                            }
                        })
                .setEnabledSupplier(flags::getCobaltRegistryOutOfBandUpdateEnabled)
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable dedup using etag.
                .setDedupDownloadWithEtag(false)
                // TODO(b/243829623): use proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    @VisibleForTesting
    static ManifestFileGroupPopulator getDenyPackageManifestPopulator(
            Flags flags, SynchronousFileStorage fileStorage, FileDownloader fileDownloader) {
        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(DENY_PACKAGE_MANIFEST_ID)
                        .setManifestFileUrl(flags.getMddPackageDenyRegistryManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        Context context = ApplicationContextSingleton.get();

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                .setEnabledSupplier(
                        () -> {
                            if (flags.getGaUxFeatureEnabled()) {
                                return isAnyConsentGiven(flags);
                            } else {
                                return ConsentManager.getInstance().getConsent().isGiven();
                            }
                        })
                .setEnabledSupplier(flags::getEnablePackageDenyMdd)
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable dedup using etag.
                .setDedupDownloadWithEtag(false)
                // TODO(b/243829623): use proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    // Check the feature flag is on or off. True means use MddLogger.
    @VisibleForTesting
    static Optional<Logger> getMddLogger(Flags flags) {
        return flags.getMddLoggerEnabled() ? Optional.of(new MddLogger()) : Optional.absent();
    }

    /** Dump MDD Debug Info. */
    public static void dump(PrintWriter writer) {
        String debugString =
                MobileDataDownloadFactory.getMdd(FlagsFactory.getFlags()).getDebugInfoAsString();
        writer.println("***====*** MDD Lib dump: ***====***");

        for (int i = 0; i <= debugString.length() / MAX_ADB_LOGCAT_SIZE; i++) {
            int start = i * MAX_ADB_LOGCAT_SIZE;
            int end = (i + 1) * MAX_ADB_LOGCAT_SIZE;
            end = Math.min(debugString.length(), end);
            writer.println(debugString.substring(start, end));
        }
    }
}
