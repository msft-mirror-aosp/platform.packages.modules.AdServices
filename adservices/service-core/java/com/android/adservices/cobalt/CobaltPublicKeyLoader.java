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

import android.content.res.AssetManager;
import android.util.JsonReader;

import com.android.adservices.LogUtil;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.cobalt.crypto.HpkeEncrypter;
import com.android.cobalt.crypto.PublicEncryptionKeys;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Container for static keys that retrieved from asset file used in {@link HpkeEncrypter}. */
public final class CobaltPublicKeyLoader implements PublicEncryptionKeys {
    private static final CobaltPublicKeyLoader sSingleton = new CobaltPublicKeyLoader();

    private static final String SHUFFLER_KEY_PROD_KEY = "SHUFFLER_KEY_PROD";
    private static final String SHUFFLER_KEY_DEV_KEY = "SHUFFLER_KEY_DEV";
    private static final String ANALYZER_KEY_PROD_KEY = "ANALYZER_KEY_PROD";
    private static final String ANALYZER_KEY_DEV_KEY = "ANALYZER_KEY_DEV";
    private static final String X25519_PUBLIC_VALUE_LEN_KEY = "X25519_PUBLIC_VALUE_LEN";
    private static final String SHUFFLER_KEY_INDEX_PROD_KEY = "SHUFFLER_KEY_INDEX_PROD";
    private static final String SHUFFLER_KEY_INDEX_DEV_KEY = "SHUFFLER_KEY_INDEX_DEV";
    private static final String ANALYZER_KEY_INDEX_PROD_KEY = "ANALYZER_KEY_INDEX_PROD";
    private static final String ANALYZER_KEY_INDEX_DEV_KEY = "ANALYZER_KEY_INDEX_DEV";
    private static final String SHUFFLER_CONTEXT_INFO_KEY = "SHUFFLER_CONTEXT_INFO";
    private static final String ANALYZER_CONTEXT_INFO_KEY = "ANALYZER_CONTEXT_INFO";

    private static final String PUBLIC_KEY_ASSET_FILE = "cobalt/public_keys.json";

    private static final ImmutableSet<String> KEY_NAME_SET =
            ImmutableSet.of(
                    SHUFFLER_KEY_PROD_KEY,
                    SHUFFLER_KEY_DEV_KEY,
                    ANALYZER_KEY_PROD_KEY,
                    ANALYZER_KEY_DEV_KEY,
                    X25519_PUBLIC_VALUE_LEN_KEY,
                    SHUFFLER_KEY_INDEX_PROD_KEY,
                    SHUFFLER_KEY_INDEX_DEV_KEY,
                    ANALYZER_KEY_INDEX_PROD_KEY,
                    ANALYZER_KEY_INDEX_DEV_KEY,
                    SHUFFLER_CONTEXT_INFO_KEY,
                    ANALYZER_CONTEXT_INFO_KEY);

    private final Object mLock = new Object();

    private ImmutableMap<String, String> mPublicKeyMap = ImmutableMap.of();

    /** Returns the static singleton PublicKeyLoader. */
    public static CobaltPublicKeyLoader getInstance() {
        return sSingleton;
    }

    @VisibleForTesting
    CobaltPublicKeyLoader() {}

    @Override
    public byte[] getShufflerKeyProd() {
        return getKeyInByteArray(SHUFFLER_KEY_PROD_KEY);
    }

    @Override
    public byte[] getShufflerKeyDev() {
        return getKeyInByteArray(SHUFFLER_KEY_DEV_KEY);
    }

    @Override
    public byte[] getAnalyzerKeyProd() {
        return getKeyInByteArray(ANALYZER_KEY_PROD_KEY);
    }

    @Override
    public byte[] getAnalyzerKeyDev() {
        return getKeyInByteArray(ANALYZER_KEY_DEV_KEY);
    }

    @Override
    public int getShufflerKeyIndexProd() {
        return getIndexInt(SHUFFLER_KEY_INDEX_PROD_KEY);
    }

    @Override
    public int getShufflerKeyIndexDev() {
        return getIndexInt(SHUFFLER_KEY_INDEX_DEV_KEY);
    }

    @Override
    public int getAnalyzerKeyIndexProd() {
        return getIndexInt(ANALYZER_KEY_INDEX_PROD_KEY);
    }

    @Override
    public int getAnalyzerKeyIndexDev() {
        return getIndexInt(ANALYZER_KEY_INDEX_DEV_KEY);
    }

    @Override
    public byte[] getShufflerContextInfoBytes() {
        return getContextInfoBytes(SHUFFLER_CONTEXT_INFO_KEY);
    }

    @Override
    public byte[] getAnalyzerContextInfoBytes() {
        return getContextInfoBytes(ANALYZER_CONTEXT_INFO_KEY);
    }

    @Override
    public int getX25519PublicValueLen() {
        return getIndexInt(X25519_PUBLIC_VALUE_LEN_KEY);
    }

    private byte[] getContextInfoBytes(String keyName) {
        loadKeysFromAsset();
        return mPublicKeyMap.get(keyName).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getKeyInByteArray(String keyName) {
        loadKeysFromAsset();
        return stringToByteArray(mPublicKeyMap.get(keyName));
    }

    private int getIndexInt(String keyName) {
        loadKeysFromAsset();
        return Integer.parseInt(mPublicKeyMap.get(keyName));
    }

    @VisibleForTesting
    void loadKeysFromAsset() {
        // mPublicKeyMap is already populated by another task.
        if (!mPublicKeyMap.isEmpty()) {
            return;
        }

        synchronized (mLock) {
            if (!mPublicKeyMap.isEmpty()) {
                return;
            }

            ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
            AssetManager assetManager = ApplicationContextSingleton.get().getAssets();
            InputStream inputStream = null;
            try {
                inputStream = assetManager.open(PUBLIC_KEY_ASSET_FILE);

                JsonReader reader =
                        new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (KEY_NAME_SET.contains(name)) {
                        mapBuilder.put(name, reader.nextString());
                    } else {
                        reader.skipValue();
                        LogUtil.w("Unknown key name: %s", name);
                    }
                }
                reader.endObject();
                mPublicKeyMap = mapBuilder.build();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load public keys from asset", e);
            }

            // Check all required keys are present.
            for (String key : KEY_NAME_SET) {
                if (!mPublicKeyMap.containsKey(key)) {
                    throw new IllegalStateException("Missing required key: " + key);
                }
            }
        }
    }

    private byte[] stringToByteArray(String s) {
        String[] numberStrings = s.split(",");

        byte[] byteArray = new byte[numberStrings.length];
        for (int i = 0; i < numberStrings.length; i++) {
            byteArray[i] = Byte.parseByte(numberStrings[i].trim());
        }

        return byteArray;
    }
}
