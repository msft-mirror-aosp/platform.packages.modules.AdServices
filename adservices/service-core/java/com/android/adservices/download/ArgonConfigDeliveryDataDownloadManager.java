/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.net.Uri;
import android.os.Build.VERSION_CODES;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.data.configdelivery.ArgonConfigurationManager;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.proto.config_delivery.MddConfigs.MddConfig;
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RequiresApi(VERSION_CODES.S)
public final class ArgonConfigDeliveryDataDownloadManager {

    static final String ARGON_CONFIG_MANIFEST_ID_PREFIX = "ArgonConfigManifest_";
    private static final LoggerFactory.Logger LOGGER = LoggerFactory.getLogger();
    private static final Object SINGLETON_LOCK = new Object();
    private final MobileDataDownload mMobileDataDownload;
    private final SynchronousFileStorage mFileStorage;

    @VisibleForTesting
    ArgonConfigDeliveryDataDownloadManager(Flags flags) {
        mMobileDataDownload = MobileDataDownloadFactory.getMdd(flags);
        mFileStorage = MobileDataDownloadFactory.getFileStorage();
    }

    @GuardedBy("SINGLETON_LOCK")
    private static volatile ArgonConfigDeliveryDataDownloadManager sInstance;

    /** Returns an instance of the ArgonDataDownloadManager. */
    public static ArgonConfigDeliveryDataDownloadManager getInstance() {
        // Initialization pattern recommended on page 334 of "Effective Java" 3rd edition.
        // Author states it provided 1.4x performance improvement.
        @SuppressWarnings("GuardedBy") // Lint is not smart enough to understand the optimization.
        ArgonConfigDeliveryDataDownloadManager singleReadResult = sInstance;
        if (singleReadResult != null) {
            return singleReadResult;
        }

        synchronized (SINGLETON_LOCK) {
            if (sInstance == null) {
                sInstance = new ArgonConfigDeliveryDataDownloadManager(FlagsFactory.getFlags());
            }
            return sInstance;
        }
    }

    /**
     * Retrieves Argon configurations from MDD and persists them using {@link
     * ArgonConfigurationManager}.
     *
     * <p>This method is blocking and should only be called on a background thread.
     */
    public void syncArgonConfigurations() {
        LOGGER.v("Attempting to retrieve and persist argon configurations from MDD");
        List<VersionedConfiguration> configurations = getArgonConfigurations();
        if (configurations.isEmpty()) {
            LOGGER.d("No files available for argon configurations");
            return;
        }

        for (VersionedConfiguration configuration : configurations) {
            ArgonConfigurationManager.insertConfigurationsIfNotExist(configuration);
        }
    }

    private List<VersionedConfiguration> getArgonConfigurations() {
        List<VersionedConfiguration> configurations = new ArrayList<>();
        for (MddConfig mddConfig :
                FlagsFactory.getFlags().getConfigDeliveryMddConfigs().getMddConfigsList()) {
            for (String groupName : mddConfig.getFileGroupNamesList()) {
                GetFileGroupRequest getFileGroupRequest =
                        GetFileGroupRequest.newBuilder().setGroupName(groupName).build();
                try {
                    ListenableFuture<ClientFileGroup> fileGroupFuture =
                            mMobileDataDownload.getFileGroup(getFileGroupRequest);
                    ClientFileGroup fileGroup = fileGroupFuture.get();
                    if (fileGroup == null) {
                        LogUtil.d(
                                "Unable to retrieve client file group for argon configuration"
                                        + "mdd file group: %s",
                                groupName);
                        continue;
                    }
                    for (ClientFile file : fileGroup.getFileList()) {
                        InputStream inputStream =
                                mFileStorage.open(
                                        Uri.parse(file.getFileUri()), ReadStreamOpener.create());
                        configurations.add(VersionedConfiguration.parseFrom(inputStream));
                        LogUtil.d(
                                "Successfully retrieved argon configuration for MDD file group: %s",
                                groupName);
                    }
                } catch (ExecutionException | InterruptedException | IOException e) {
                    LogUtil.e(
                            e,
                            "Exception thrown while attempting to retrieve argon configuration for"
                                    + " file group %s",
                            groupName);
                }
            }
        }
        return configurations;
    }

    /**
     * Generates a unique manifest ID for Argon configurations by prefixing the given
     * argonScopedManifestId with a predefined prefix.
     *
     * @param argonScopedManifestId The ID is unique the Argon configurations.
     * @return A unique manifest ID for Argon configurations within adservices package.
     */
    static String generateArgonConfigManifestId(String argonScopedManifestId) {
        return ARGON_CONFIG_MANIFEST_ID_PREFIX + argonScopedManifestId;
    }
}
