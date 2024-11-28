/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.adservices.rollback;

import android.annotation.UserIdInt;
import android.app.adservices.AdServicesManager;
import android.util.SparseArray;

import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.LogUtil;
import com.android.server.adservices.errorlogging.AdServicesErrorLoggerImpl;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Manager to store information on handling a rollback of the AdServices module. We will have one
 * RollbackHandlingManager instance per user.
 *
 * @hide
 */
public final class RollbackHandlingManager {
    static final String STORAGE_XML_IDENTIFIER = "RollbackHandlingStorageIdentifier.xml";

    static final String MSMT_FILE_PREFIX = "Measurement";

    static final String DELETION_OCCURRED_KEY = "deletion_occurred";

    static final String VERSION_KEY = "adservices_version";

    @VisibleForTesting static final String DUMP_PREFIX = "  ";

    private final String mDatastoreDir;
    private final int mPackageVersion;

    // there's just 1 DeletionApiType , so initial capacity is 1
    private final SparseArray<AtomicFileDatastore> mAtomicFileDatastoreMap = new SparseArray<>(1);

    private RollbackHandlingManager(String datastoreDir, int packageVersion) {
        mDatastoreDir = Objects.requireNonNull(datastoreDir, "datastoreDir cannot be null");
        mPackageVersion = packageVersion;
    }

    /** Create a RollbackHandlingManager with base directory and for userIdentifier */
    public static RollbackHandlingManager createRollbackHandlingManager(
            String baseDir, @UserIdInt int userId, int packageVersion) throws IOException {
        Objects.requireNonNull(baseDir, "Base dir must be provided.");

        // The data store is in the directore with the following path:
        // /data/system/adservices/{user_id}/rollback/
        String rollbackHandlingDataStoreDir =
                RollbackHandlingDatastoreLocationHelper.getRollbackHandlingDataStoreDirAndCreateDir(
                        baseDir, userId);

        return new RollbackHandlingManager(rollbackHandlingDataStoreDir, packageVersion);
    }

    @VisibleForTesting
    AtomicFileDatastore getOrCreateAtomicFileDatastore(
            @AdServicesManager.DeletionApiType int deletionApiType) throws IOException {
        synchronized (this) {
            AtomicFileDatastore datastore = mAtomicFileDatastoreMap.get(deletionApiType);
            if (datastore == null) {
                if (deletionApiType == AdServicesManager.MEASUREMENT_DELETION) {
                    datastore =
                            new AtomicFileDatastore(
                                    new File(
                                            mDatastoreDir,
                                            MSMT_FILE_PREFIX + STORAGE_XML_IDENTIFIER),
                                    mPackageVersion,
                                    VERSION_KEY,
                                    AdServicesErrorLoggerImpl.getInstance());
                }
                mAtomicFileDatastoreMap.put(deletionApiType, datastore);
                datastore.initialize();
            }
            return datastore;
        }
    }

    /** Saves that a deletion of AdServices data occurred to the storage. */
    public void recordAdServicesDataDeletion(@AdServicesManager.DeletionApiType int deletionType)
            throws IOException {
        AtomicFileDatastore datastore = getOrCreateAtomicFileDatastore(deletionType);
        synchronized (this) {
            try {
                datastore.putBoolean(DELETION_OCCURRED_KEY, /* value */ true);
            } catch (IOException e) {
                LogUtil.e(e, "Record deletion failed due to IOException thrown by Datastore.");
            }
        }
    }

    /** Returns information about whether a deletion of AdServices data occurred. */
    public boolean wasAdServicesDataDeleted(@AdServicesManager.DeletionApiType int deletionType)
            throws IOException {
        AtomicFileDatastore datastore = getOrCreateAtomicFileDatastore(deletionType);
        synchronized (this) {
            return datastore.getBoolean(DELETION_OCCURRED_KEY, /* defaultValue */ false);
        }
    }

    /** Returns the previous version number saved in the datastore file. */
    public int getPreviousStoredVersion(@AdServicesManager.DeletionApiType int deletionType)
            throws IOException {
        AtomicFileDatastore datastore = getOrCreateAtomicFileDatastore(deletionType);
        return datastore.getPreviousStoredVersion();
    }

    /**
     * Deletes the previously stored information about whether a deletion of AdServices data
     * occurred.
     */
    public void resetAdServicesDataDeletion(@AdServicesManager.DeletionApiType int deletionType)
            throws IOException {
        AtomicFileDatastore datastore = getOrCreateAtomicFileDatastore(deletionType);
        synchronized (this) {
            try {
                datastore.putBoolean(DELETION_OCCURRED_KEY, /* value */ false);
            } catch (IOException e) {
                LogUtil.e(
                        e, "Reset deletion status failed due to IOException thrown by Datastore.");
            }
        }
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix) {
        writer.printf("%sRollbackHandlingManager:\n", prefix);
        String prefix2 = prefix + DUMP_PREFIX;

        writer.printf("%smDatastoreDir: %s\n", prefix2, mDatastoreDir);
        writer.printf("%smPackageVersion: %s\n", prefix2, mPackageVersion);

        int mapSize = mAtomicFileDatastoreMap.size();
        writer.printf("%s%d datastores", prefix2, mapSize);
        if (mapSize == 0) {
            writer.println();
            return;
        }
        writer.println(':');
        String prefix3 = prefix2 + DUMP_PREFIX;
        String prefix4 = prefix3 + DUMP_PREFIX;
        for (int i = 0; i < mapSize; i++) {
            writer.printf("%s deletion API type %d:\n", prefix3, mAtomicFileDatastoreMap.keyAt(i));
            mAtomicFileDatastoreMap
                    .valueAt(i)
                    .dump(writer, prefix4, AtomicFileDatastore.DUMP_ARGS_INCLUDE_CONTENTS_ONLY);
        }
    }
}
