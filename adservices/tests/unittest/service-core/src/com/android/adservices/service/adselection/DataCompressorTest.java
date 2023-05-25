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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class DataCompressorTest {

    private DataCompressor mDataCompressor;

    @Before
    public void setup() {
        mDataCompressor = new DataCompressor();
    }

    @Test
    public void testCompress() throws IOException {
        byte[] data =
                "repetitive test string repetitive test string repetitive test string".getBytes();
        byte[] compressedData = mDataCompressor.gzipCompress(data);
        Assert.assertTrue(compressedData.length < data.length);
    }

    @Test
    public void testDecompress() throws IOException {
        byte[] compressedData = mDataCompressor.gzipCompress("Hello, world!".getBytes());
        byte[] decompressedData = mDataCompressor.gzipDecompress(compressedData);
        Assert.assertArrayEquals(decompressedData, "Hello, world!".getBytes());
    }
}
