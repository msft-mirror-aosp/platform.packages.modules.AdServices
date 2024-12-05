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
package com.android.adservices.data.common;

import android.content.Context;

import com.android.adservices.errorlogging.AdServicesErrorLoggerImpl;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.File;
import java.util.Objects;

/**
 * Factory for {@link AtomicFileDatastore} instances.
 *
 * @deprecated Please use {@link androidx.datastore.guava.GuavaDataStore} wrapper class for any new
 *     features for storage.
 */
@Deprecated
public final class LegacyAtomicFileDatastoreFactory {

    @VisibleForTesting
    static final String VERSION_KEY = "com.android.adservices.data.common.VERSION";

    /** Creates a datastore with the given args and a default {@code versionKey}. */
    @SuppressWarnings("AvoidStaticContext") // Factory method
    public static AtomicFileDatastore createAtomicFileDatastore(
            Context context, String filename, int datastoreVersion) {
        return createAtomicFileDatastore(
                context, filename, datastoreVersion, AdServicesErrorLoggerImpl.getInstance());
    }

    /** Creates a datastore with the given args. */
    @SuppressWarnings("AvoidStaticContext") // Factory method
    public static AtomicFileDatastore createAtomicFileDatastore(
            Context context,
            String filename,
            int datastoreVersion,
            AdServicesErrorLogger adServicesErrorLogger) {
        return new AtomicFileDatastore(
                getDataStoreFile(context, filename),
                datastoreVersion,
                VERSION_KEY,
                adServicesErrorLogger);
    }

    @VisibleForTesting
    @SuppressWarnings("AvoidStaticContext") // Factory method
    static File getDataStoreFile(Context context, String filename) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(filename, "filename cannot be null");
        Preconditions.checkStringNotEmpty(filename, "filename must not be empty or null");

        return FileCompatUtils.newFileHelper(context.getFilesDir(), filename);
    }

    private LegacyAtomicFileDatastoreFactory() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
