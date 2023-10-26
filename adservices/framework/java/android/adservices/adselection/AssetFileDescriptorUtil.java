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

package android.adservices.adselection;

import android.annotation.NonNull;
import android.content.res.AssetFileDescriptor;
import android.os.ParcelFileDescriptor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Utility class used to set up the read and write pipes for the usage of reading pointers from
 * shared memory.
 *
 * @hide
 */
public class AssetFileDescriptorUtil {

    private AssetFileDescriptorUtil() {}

    /**
     * Creates a read and write pipe, writes the data in {@code buffer} into the write end, and
     * returns the read end in the form of a {@link AssetFileDescriptor}.
     *
     * @throws IOException if an exception is encountered while creating or writing to the pipe
     */
    public static AssetFileDescriptor setupAssetFileDescriptorResponse(@NonNull byte[] buffer)
            throws IOException {
        Objects.requireNonNull(buffer);

        ParcelFileDescriptor[] descriptors = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor writeDescriptor = descriptors[1];
        try (FileOutputStream outputStream =
                new FileOutputStream(writeDescriptor.getFileDescriptor())) {
            outputStream.write(buffer);
        }
        return new AssetFileDescriptor(descriptors[0], 0, buffer.length);
    }

    /**
     * Reads the content the {@link AssetFileDescriptor} points to into a buffer and returns the
     * number of bytes read.
     *
     * @throws IOException if an exception is encountered while reading the content.
     */
    public static int readAssetFileDescriptorIntoBuffer(
            @NonNull byte[] buffer, @NonNull AssetFileDescriptor assetFileDescriptor)
            throws IOException {
        Objects.requireNonNull(buffer);
        Objects.requireNonNull(assetFileDescriptor);

        try (InputStream inputStream = assetFileDescriptor.createInputStream()) {
            return inputStream.read(buffer);
        }
    }
}
