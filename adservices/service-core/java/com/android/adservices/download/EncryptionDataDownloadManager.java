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

package com.android.adservices.download;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/** Handles EncryptionData download from MDD server to device. */
@RequiresApi(Build.VERSION_CODES.S)
public final class EncryptionDataDownloadManager {
    private static volatile EncryptionDataDownloadManager sEncryptionDataDownloadManager;

    private final Context mContext;
    private final MobileDataDownload mMobileDataDownload;
    private final SynchronousFileStorage mFileStorage;
    private final LoggerFactory.Logger mLogger = LoggerFactory.getLogger();

    private static final String GROUP_NAME = "encryption-keys";
    private static final String DOWNLOADED_ENCRYPTION_DATA_FILE_TYPE = ".json";

    @VisibleForTesting
    EncryptionDataDownloadManager(Context context, Flags flags) {
        mContext = context.getApplicationContext();
        mMobileDataDownload = MobileDataDownloadFactory.getMdd(flags);
        mFileStorage = MobileDataDownloadFactory.getFileStorage();
    }

    /** Gets an instance of EncryptionDataDownloadManager to be used. */
    public static EncryptionDataDownloadManager getInstance() {
        // TODO(b/331428431): Fix singleton creation behaviour.
        if (sEncryptionDataDownloadManager == null) {
            synchronized (EncryptionDataDownloadManager.class) {
                if (sEncryptionDataDownloadManager == null) {
                    sEncryptionDataDownloadManager =
                            new EncryptionDataDownloadManager(
                                    ApplicationContextSingleton.get(), FlagsFactory.getFlags());
                }
            }
        }
        return sEncryptionDataDownloadManager;
    }

    public enum DownloadStatus {
        SUCCESS,
        NO_FILE_AVAILABLE,
    }

    /**
     * Find, open and read the encryption keys data file from MDD. Insert all keys into database.
     */
    public ListenableFuture<DownloadStatus> readAndInsertEncryptionDataFromMdd() {
        mLogger.v("Reading encryption MDD data for group name: %s", GROUP_NAME);
        List<ClientConfigProto.ClientFile> jsonKeyFiles = getEncryptionDataFiles();
        if (jsonKeyFiles == null || jsonKeyFiles.isEmpty()) {
            return Futures.immediateFuture(DownloadStatus.NO_FILE_AVAILABLE);
        }

        for (ClientConfigProto.ClientFile clientFile : jsonKeyFiles) {
            Optional<List<EncryptionKey>> encryptionKeys = processDownloadedFile(clientFile);
            if (!encryptionKeys.isPresent()) {
                mLogger.d("Parsing keys failed for %s ", clientFile.getFileId());
            }
        }
        return Futures.immediateFuture(DownloadStatus.SUCCESS);
    }

    @Nullable
    private List<ClientConfigProto.ClientFile> getEncryptionDataFiles() {
        GetFileGroupRequest getFileGroupRequest =
                GetFileGroupRequest.newBuilder().setGroupName(GROUP_NAME).build();
        try {
            ListenableFuture<ClientConfigProto.ClientFileGroup> fileGroupFuture =
                    mMobileDataDownload.getFileGroup(getFileGroupRequest);
            ClientConfigProto.ClientFileGroup fileGroup = fileGroupFuture.get();
            if (fileGroup == null) {
                mLogger.d("MDD has not downloaded the Encryption Data Files yet.");
                return null;
            }

            List<ClientConfigProto.ClientFile> jsonKeyFiles = new ArrayList<>();
            for (ClientConfigProto.ClientFile file : fileGroup.getFileList()) {
                if (file.getFileId().endsWith(DOWNLOADED_ENCRYPTION_DATA_FILE_TYPE)) {
                    jsonKeyFiles.add(file);
                }
            }
            return jsonKeyFiles;

        } catch (ExecutionException | InterruptedException e) {
            mLogger.e(e, "Unable to load MDD file group for encryption.");
            // TODO(b/329334770): Add CEL log
            return null;
        }
    }

    private Optional<List<EncryptionKey>> processDownloadedFile(
            ClientConfigProto.ClientFile encryptionDataFile) {
        mLogger.d("Inserting Encryption MDD data into DB.");
        try (InputStream inputStream =
                        mFileStorage.open(
                                Uri.parse(encryptionDataFile.getFileUri()),
                                ReadStreamOpener.create());
                BufferedReader bufferedReader =
                        new BufferedReader(new InputStreamReader(inputStream))) {

            String jsonString = bufferedReader.lines().collect(Collectors.joining("\n"));
            JSONArray jsonArray = new JSONArray(jsonString);

            List<EncryptionKey> encryptionKeys = new ArrayList<>();
            for (int index = 0; index < jsonArray.length(); index++) {
                JSONObject jsonKeyObject = jsonArray.getJSONObject(index);
                EncryptionKey key = createEncryptionKeyFromJson(jsonKeyObject);
                encryptionKeys.add(key);
            }

            EncryptionKeyDao encryptionKeyDao = EncryptionKeyDao.getInstance(mContext);
            encryptionKeyDao.insert(encryptionKeys);

            return Optional.of(encryptionKeys);
        } catch (IOException | JSONException e) {
            mLogger.e(
                    e,
                    "Parsing of encryption keys failed for %s.",
                    encryptionDataFile.getFileUri());
            return Optional.empty();
        }
    }

    private EncryptionKey createEncryptionKeyFromJson(JSONObject jsonObject) throws JSONException {
        EncryptionKey.Builder builder = new EncryptionKey.Builder();
        builder.setEnrollmentId(jsonObject.getString("enrollmentId"));
        // TODO(b/329334769): Implement JSON parsing based on version types.
        return builder.build();
    }
}
