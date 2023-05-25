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

package com.android.adservices.service.adselection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Wrapper class for data compression and decompression */
public class DataCompressor {
    /**
     * Buffer size controls the size of the batches we read from the input stream. If the batches
     * are too big then will consume more memory. If the batches are two small then will consume
     * more cycles. This number (1KB) is a commonly used sweet-spot. Can be changed when further
     * data is available.
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * Compresses {@link byte[]} data
     *
     * @throws IOException if {@link GZIPOutputStream} class fails IO
     */
    public byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
        gzipOutputStream.write(data);
        gzipOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Decompresses {@link byte[]} data
     *
     * @throws IOException if {@link GZIPInputStream} class fails IO
     */
    public byte[] gzipDecompress(byte[] compressedData) throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
        GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = gzipInputStream.read(buffer)) > 0) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        gzipInputStream.close();

        return byteArrayOutputStream.toByteArray();
    }
}
