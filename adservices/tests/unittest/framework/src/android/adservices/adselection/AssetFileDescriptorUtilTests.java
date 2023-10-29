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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.res.AssetFileDescriptor;

import org.junit.Test;

public class AssetFileDescriptorUtilTests {
    private static final byte[] EXPECTED = new byte[] {1, 2, 3, 4};

    @Test
    public void testSetupAssetFileDescriptorResponseReturnsCorrectResult() throws Exception {
        AssetFileDescriptor assetFileDescriptor =
                AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(EXPECTED);
        byte[] result = new byte[EXPECTED.length];

        int length =
                AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(
                        result, assetFileDescriptor);
        assertThat(result).isEqualTo(EXPECTED);
        assertThat(length).isEqualTo(EXPECTED.length);
    }

    @Test
    public void testSetupAssetFileDescriptorResponseThrowsExceptionWhenBufferIsNull()
            throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(null);
                });
    }

    @Test
    public void testReadAssetFileDescriptorIntoBufferThrowsExceptionWhenBufferIsNull()
            throws Exception {
        AssetFileDescriptor assetFileDescriptor =
                AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(EXPECTED);
        assertThrows(
                NullPointerException.class,
                () -> {
                    AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(
                            null, assetFileDescriptor);
                });
    }

    @Test
    public void testReadAssetFileDescriptorIntoBufferThrowsExceptionWhenAssetFileDescriptorIsNull()
            throws Exception {
        byte[] result = new byte[EXPECTED.length];

        assertThrows(
                NullPointerException.class,
                () -> {
                    AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(result, null);
                });
    }
}
