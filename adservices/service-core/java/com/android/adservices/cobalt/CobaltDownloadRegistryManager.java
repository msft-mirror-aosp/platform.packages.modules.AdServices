/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.cobalt;

import android.net.Uri;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.cobalt.CobaltRegistry;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** This class handles MDD download Cobalt Registry. */
public final class CobaltDownloadRegistryManager {
    private static final CobaltDownloadRegistryManager sSingleton =
            new CobaltDownloadRegistryManager();
    private static final String COBALT_REGISTRY_FILE_GROUP_NAME = "rubidium-registry";
    private static final String DOWNLOADED_REGISTRY_FILE_ID = "cobalt_registry.binarypb";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private final Flags mFlags;
    private final SynchronousFileStorage mFileStorage;

    public static CobaltDownloadRegistryManager getInstance() {
        return sSingleton;
    }

    @VisibleForTesting
    CobaltDownloadRegistryManager(Flags flags, SynchronousFileStorage fileStorage) {
        mFlags = flags;
        mFileStorage = fileStorage;
    }

    private CobaltDownloadRegistryManager() {
        this(FlagsFactory.getFlags(), MobileDataDownloadFactory.getFileStorage());
    }

    /**
     * Returns {@code Optional<CobaltRegistry>} using MDD download registry binary proto. Will
     * return {@link Optional#empty()} if no MDD download registry available or exceptions occurred
     * when parsing the file.
     */
    public Optional<CobaltRegistry> getMddRegistry() {
        MobileDataDownload mdd = MobileDataDownloadFactory.getMdd(mFlags);
        GetFileGroupRequest getFileGroupRequest =
                GetFileGroupRequest.newBuilder()
                        .setGroupName(COBALT_REGISTRY_FILE_GROUP_NAME)
                        .build();

        try {
            for (ClientFile clientFile :
                    mdd.getFileGroup(getFileGroupRequest).get().getFileList()) {
                if (clientFile.getFileId().equals(DOWNLOADED_REGISTRY_FILE_ID)) {
                    sLogger.d(
                            "Parse downloaded Cobalt registry using %s.",
                            DOWNLOADED_REGISTRY_FILE_ID);
                    try {
                        InputStream inputStream =
                                mFileStorage.open(
                                        Uri.parse(clientFile.getFileUri()),
                                        ReadStreamOpener.create());
                        return Optional.of(CobaltRegistry.parseFrom(inputStream));

                    } catch (IOException e) {
                        // TODO(b/368050053): Add CEL.
                        sLogger.e(e, "Error in parsing downloaded Cobalt registry");
                    }
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            // TODO(b/368050053): Add CEL.
            sLogger.e(e, "Error in loading downloaded Cobalt Registry");
        }
        return Optional.empty();
    }
}
