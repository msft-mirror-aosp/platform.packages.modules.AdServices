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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;

import com.google.android.downloader.AndroidDownloaderLogger;
import com.google.android.downloader.ConnectivityHandler;
import com.google.android.downloader.DownloadConstraints;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.PlatformUrlEngine;
import com.google.android.downloader.UrlEngine;
import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
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
import com.google.android.libraries.mobiledatadownload.populator.ManifestFileGroupPopulator;
import com.google.android.libraries.mobiledatadownload.populator.SharedPreferencesManifestFileMetadata;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mobiledatadownload.DownloadConfigProto.ManifestFileFlag;
import com.google.protobuf.MessageLite;

import java.util.concurrent.Executor;

/** Mobile Data Download Factory. */
public class MobileDataDownloadFactory {
    private static MobileDataDownload sSingleton;

    private static final String MDD_METADATA_SHARED_PREFERENCES = "mdd_metadata_store";
    private static final String TOPICS_MANIFEST_ID = "TopicsManifestId";

    // TODO(b/236761740): We use this for now for testing. We need to update to the correct one
    // when we actually upload the models.
    private static final String TOPICS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/webref-ondevice/manifest_configs/nga-dev/webref_manifest_config.pb";

    /** Returns a singleton of MobileDataDownload. */
    @NonNull
    public static synchronized MobileDataDownload getMdd(
            @NonNull Context context, @NonNull Flags flags) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSingleton == null) {
                // TODO(b/236761740): This only adds the core MDD code. We still need other
                //  components:
                // Add TaskScheduler
                // Add Logger
                // Add Configurator.

                SynchronousFileStorage fileStorage = getFileStorage(context);
                FileDownloader fileDownloader = getFileDownloader(context, flags, fileStorage);
                NetworkUsageMonitor networkUsageMonitor =
                        new NetworkUsageMonitor(context, System::currentTimeMillis);

                sSingleton =
                        MobileDataDownloadBuilder.newBuilder()
                                .setContext(context)
                                .setControlExecutor(getControlExecutor())
                                .setNetworkUsageMonitor(networkUsageMonitor)
                                .setFileStorage(fileStorage)
                                .setFileDownloaderSupplier(() -> fileDownloader)
                                .addFileGroupPopulator(
                                        getTopicsManifestPopulator(
                                                context, flags, fileStorage, fileDownloader))
                                .setLoggerOptional(Optional.absent())
                                .build();
            }

            return sSingleton;
        }
    }

    // Connectivity constraints will be checked by JobScheduler/WorkManager instead.
    private static class NoOpConnectivityHandler implements ConnectivityHandler {
        @Override
        public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
            return Futures.immediateVoidFuture();
        }
    }

    @NonNull
    private static SynchronousFileStorage getFileStorage(@NonNull Context context) {
        return new SynchronousFileStorage(
                ImmutableList.of(
                        /*backends*/ AndroidFileBackend.builder(context).build(),
                        new JavaFileBackend()),
                ImmutableList.of(/*transforms*/ ),
                ImmutableList.of(/*monitors*/ ));
    }

    @NonNull
    private static ListeningExecutorService getControlExecutor() {
        return AdServicesExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static Executor getDownloadExecutor() {
        return AdServicesExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static UrlEngine getUrlEngine(@NonNull Flags flags) {
        // TODO(b/219594618): Switch to use CronetUrlEngine.
        return new PlatformUrlEngine(
                AdServicesExecutors.getBlockingExecutor(),
                /* connectTimeoutMs = */ flags.getDownloaderConnectionTimeoutMs(),
                /* readTimeoutMs = */ flags.getDownloaderReadTimeoutMs());
    }

    @NonNull
    private static ExceptionHandler getExceptionHandler() {
        return ExceptionHandler.withDefaultHandling();
    }

    @NonNull
    private static FileDownloader getFileDownloader(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull SynchronousFileStorage fileStorage) {
        DownloadMetadataStore downloadMetadataStore = getDownloadMetadataStore(context);

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

    @NonNull
    private static DownloadMetadataStore getDownloadMetadataStore(@NonNull Context context) {
        SharedPreferences sharedPrefs =
                context.getSharedPreferences(MDD_METADATA_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        DownloadMetadataStore downloadMetadataStore =
                new SharedPreferencesDownloadMetadata(
                        sharedPrefs, AdServicesExecutors.getBackgroundExecutor());
        return downloadMetadataStore;
    }

    @NonNull
    private static ManifestFileGroupPopulator getTopicsManifestPopulator(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull SynchronousFileStorage fileStorage,
            @NonNull FileDownloader fileDownloader) {

        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(TOPICS_MANIFEST_ID)
                        .setManifestFileUrl(TOPICS_MANIFEST_FILE_URL)
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
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
}
