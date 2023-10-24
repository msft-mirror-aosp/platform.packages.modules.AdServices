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

import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * A simple datastore utilizing {@link android.util.AtomicFile} and {@link
 * android.os.PersistableBundle} to read/write a simple key/value map to file.
 *
 * <p>The datastore is loaded from file only when initialized and written to file on every write.
 * When using this datastore, it is up to the caller to ensure that each datastore file is accessed
 * by exactly one datastore object. If multiple writing threads or processes attempt to use
 * different instances pointing to the same file, transactions may be lost.
 *
 * <p>Keys must be non-null, non-empty strings, and values must be booleans.
 */
public final class BooleanFileDatastore
        extends com.android.adservices.shared.storage.BooleanFileDatastore {
    private static final String VERSION_KEY = "com.android.adservices.data.common.VERSION";

    public BooleanFileDatastore(Context adServicesContext, String filename, int datastoreVersion) {
        super(
                FileCompatUtils.newFileHelper(
                        Objects.requireNonNull(adServicesContext).getFilesDir(),
                        Preconditions.checkStringNotEmpty(filename, "Filename must not be empty")),
                datastoreVersion,
                VERSION_KEY);
    }
}
