/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.content.res.AssetManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@SpyStatic(ApplicationContextSingleton.class)
public class CobaltPublicKeyLoaderTest extends AdServicesExtendedMockitoTestCase {
    private static final int X25519_PUBLIC_VALUE_LEN = 32;

    private static final byte[] SHUFFLER_KEY_PROD =
            new byte[] {
                -111, -111, 86, 123, -6, -114, -109, 121, -84, -113, -92, -5, 50, 103, 22, -53, 103,
                -57, 97, 11, 80, -5, 105, -26, -122, 106, 65, 107, -32, -74, -23, 10
            };

    private static final int SHUFFLER_KEY_INDEX_PROD = 11;

    private static final byte[] SHUFFLER_KEY_DEV =
            new byte[] {
                -90, -73, 32, -62, 119, -72, 48, -40, -127, -103, -7, -58, 35, -88, -4, 45, 33, 21,
                32, 48, 42, 43, 89, 33, -43, -81, -64, 111, 118, 76, 77, 32
            };

    private static final int SHUFFLER_KEY_INDEX_DEV = 9;

    private static final byte[] SHUFFLER_CONTEXT_INFO_BYTES =
            "cobalt-1.0-shuffler".getBytes(StandardCharsets.UTF_8);

    private static final byte[] ANALYZER_KEY_PROD =
            new byte[] {
                75, -121, 55, -37, -24, -80, -119, -113, -64, 25, -91, 114, -56, -23, 108, 5, 90,
                -3, 24, -62, 1, 109, 51, 123, -88, 36, 36, 0, 51, 104, -37, 1
            };

    private static final int ANALYZER_KEY_INDEX_PROD = 12;

    private static final byte[] ANALYZER_KEY_DEV =
            new byte[] {
                -5, -81, 123, 9, -16, -83, -75, -106, 122, -13, 111, -106, 123, -65, -7, -78, 125,
                107, -23, 69, 120, -59, 40, 19, 6, 92, -119, 6, -58, 126, 125, 41
            };

    private static final int ANALYZER_KEY_INDEX_DEV = 10;

    private static final byte[] ANALYZER_CONTEXT_INFO_BYTES =
            "cobalt-1.0-analyzer".getBytes(StandardCharsets.UTF_8);
    public static final String INVALID_KEY_JSON = "{\"INVALID_KEY\": \"KEY_VALUE\"}";

    private CobaltPublicKeyLoader mPublicKeyLoader;
    @Mock private AssetManager mMockAssetManager;

    @Before
    public void setup() {
        mPublicKeyLoader = CobaltPublicKeyLoader.getInstance();
    }

    @Test
    public void testGetInstance() {
        assertThat(mPublicKeyLoader).isNotNull();
        assertThat(mPublicKeyLoader).isSameInstanceAs(CobaltPublicKeyLoader.getInstance());
    }

    @Test
    public void testGetShuffleKeyProd() {
        assertThat(mPublicKeyLoader.getShufflerKeyProd()).isEqualTo(SHUFFLER_KEY_PROD);
    }

    @Test
    public void testGetShufflerContextInfoBytes() {
        assertThat(mPublicKeyLoader.getShufflerContextInfoBytes())
                .isEqualTo(SHUFFLER_CONTEXT_INFO_BYTES);
    }

    @Test
    public void testGetShufflerKeyIndexProd() {
        assertThat(mPublicKeyLoader.getShufflerKeyIndexProd()).isEqualTo(SHUFFLER_KEY_INDEX_PROD);
    }

    @Test
    public void testGetShufflerKeyIndexDev() {
        assertThat(mPublicKeyLoader.getShufflerKeyIndexDev()).isEqualTo(SHUFFLER_KEY_INDEX_DEV);
    }

    @Test
    public void testGetAnalyzerKeyProd() {
        assertThat(mPublicKeyLoader.getAnalyzerKeyProd()).isEqualTo(ANALYZER_KEY_PROD);
    }

    @Test
    public void testGetAnalyzerContextInfoBytes() {
        assertThat(mPublicKeyLoader.getAnalyzerContextInfoBytes())
                .isEqualTo(ANALYZER_CONTEXT_INFO_BYTES);
    }

    @Test
    public void testGetAnalyzerKeyIndexProd() {
        assertThat(mPublicKeyLoader.getAnalyzerKeyIndexProd()).isEqualTo(ANALYZER_KEY_INDEX_PROD);
    }

    @Test
    public void testGetAnalyzerKeyIndexDev() {
        assertThat(mPublicKeyLoader.getAnalyzerKeyIndexDev()).isEqualTo(ANALYZER_KEY_INDEX_DEV);
    }

    @Test
    public void testGetX25519PublicValueLen() {
        assertThat(mPublicKeyLoader.getX25519PublicValueLen()).isEqualTo(X25519_PUBLIC_VALUE_LEN);
    }

    @Test
    public void testGetShufflerKeyDev() {
        assertThat(mPublicKeyLoader.getShufflerKeyDev()).isEqualTo(SHUFFLER_KEY_DEV);
    }

    @Test
    public void testGetAnalyzerKeyDev() {
        assertThat(mPublicKeyLoader.getAnalyzerKeyDev()).isEqualTo(ANALYZER_KEY_DEV);
    }

    @Test
    public void testLoadKeysFromAsset_failedToOpenAsset() throws Exception {
        doReturn(mMockContext).when(() -> ApplicationContextSingleton.get());
        when(mMockContext.getAssets()).thenReturn(mMockAssetManager);
        when(mMockAssetManager.open(any())).thenThrow(new IOException());

        CobaltPublicKeyLoader cobaltPublicKeyLoader = new CobaltPublicKeyLoader();

        Exception exception =
                assertThrows(
                        RuntimeException.class, () -> cobaltPublicKeyLoader.loadKeysFromAsset());
        assertThat(exception.getMessage()).isEqualTo("Failed to load public keys from asset");
    }

    @Test
    public void testLoadKeysFromAsset_missingKey() throws Exception {
        String invalidKeyAsset = INVALID_KEY_JSON;
        InputStream inputStream =
                new ByteArrayInputStream(invalidKeyAsset.getBytes(StandardCharsets.UTF_8));
        doReturn(mMockContext).when(() -> ApplicationContextSingleton.get());
        when(mMockContext.getAssets()).thenReturn(mMockAssetManager);
        when(mMockAssetManager.open(any())).thenReturn(inputStream);

        CobaltPublicKeyLoader cobaltPublicKeyLoader = new CobaltPublicKeyLoader();

        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> cobaltPublicKeyLoader.loadKeysFromAsset());
        assertThat(exception.getMessage()).contains("Missing required key:");
    }
}
