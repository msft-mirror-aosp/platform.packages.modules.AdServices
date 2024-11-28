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

package com.android.adservices.data.common;

import android.content.Context;

import com.android.adservices.errorlogging.AdServicesErrorLoggerImpl;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.File;
import java.util.Objects;

/**
 * {@inheritDoc}
 *
 * <p>This service-side version of {@link com.android.adservices.shared.storage.AtomicFileDatastore}
 * uses a hardcoded version key ({@link #VERSION_KEY}}.
 *
 * @deprecated Please use {@link androidx.datastore.guava.GuavaDataStore} wrapper class for any new
 *     features for storage.
 */
@Deprecated
public final class AtomicFileDatastore
        extends com.android.adservices.shared.storage.AtomicFileDatastore {

    @VisibleForTesting
    static final String VERSION_KEY = "com.android.adservices.data.common.VERSION";

    public AtomicFileDatastore(Context context, String filename, int datastoreVersion) {
        this(context, filename, datastoreVersion, AdServicesErrorLoggerImpl.getInstance());
    }

    public AtomicFileDatastore(
            Context context,
            String filename,
            int datastoreVersion,
            AdServicesErrorLogger adServicesErrorLogger) {
        this(
                FileCompatUtils.newFileHelper(
                        Objects.requireNonNull(context, "context cannot be null").getFilesDir(),
                        Preconditions.checkStringNotEmpty(
                                filename, "Filename must not be empty or null")),
                datastoreVersion,
                adServicesErrorLogger);
    }

    @VisibleForTesting
    public AtomicFileDatastore(
            File datastoreFile, int datastoreVersion, AdServicesErrorLogger adServicesErrorLogger) {
        super(datastoreFile, datastoreVersion, VERSION_KEY, adServicesErrorLogger);
    }
}
