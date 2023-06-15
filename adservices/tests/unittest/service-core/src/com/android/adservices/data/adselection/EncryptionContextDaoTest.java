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

package com.android.adservices.data.adselection;

import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;

import static com.google.common.truth.Truth.assertThat;


import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.ohttp.EncapsulatedSharedSecret;
import com.android.adservices.ohttp.HpkeContextNativeRef;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;

import com.google.common.io.BaseEncoding;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;

public class EncryptionContextDaoTest {
    private static final long CONTEXT_ID_1 = 1L;
    private static final long CONTEXT_ID_2 = 2L;
    private static final String SHARED_SECRET_STRING = "1";
    private static final String KEY_CONFIG_HEX =
            "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                    + "00080001000100010003";

    private static final byte[] KEY_CONFIG_BYTES =
            BaseEncoding.base16().lowerCase().decode(KEY_CONFIG_HEX);
    private static final EncapsulatedSharedSecret SHARED_SECRET =
            EncapsulatedSharedSecret.create(SHARED_SECRET_STRING.getBytes(StandardCharsets.UTF_8));

    private static final long HPKE_REF_ADDRESS = 100L;
    private static final HpkeContextNativeRef HPKE_REF =
            HpkeContextNativeRef.fromNativeRefAddress(HPKE_REF_ADDRESS);
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private EncryptionContextDao mEncryptionContextDao;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private ObliviousHttpKeyConfig mObliviousHttpKeyConfig;

    @Before
    public void setup() throws InvalidKeySpecException {
        mEncryptionContextDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionEncryptionDatabase.class)
                        .build()
                        .encryptionContextDao();
        mObliviousHttpKeyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(KEY_CONFIG_BYTES);
    }

    @Test
    public void test_getEncryptionContext_emptyTable_returnsNull() throws Exception {
        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_1, ENCRYPTION_KEY_TYPE_AUCTION))
                .isNull();
    }

    @Test
    public void test_getEncryptionContext_returnsContext() throws Exception {
        DBEncryptionContext context = getDbEncryptionContext(CONTEXT_ID_1);
        mEncryptionContextDao.insertEncryptionContext(context);

        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_1, ENCRYPTION_KEY_TYPE_AUCTION))
                .isEqualTo(getExpectedDbEncryptionContext(context));
    }

    @Test
    public void test_getEncryptionContext_multipleEntriesInTable_returnsContext() throws Exception {
        DBEncryptionContext context1 = getDbEncryptionContext(CONTEXT_ID_1);
        DBEncryptionContext context2 = getDbEncryptionContext(CONTEXT_ID_2);

        mEncryptionContextDao.insertEncryptionContext(context1);
        mEncryptionContextDao.insertEncryptionContext(context2);

        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_2, ENCRYPTION_KEY_TYPE_AUCTION))
                .isEqualTo(getExpectedDbEncryptionContext(context2));
    }

    @Test
    public void test_insertEncryptionContext_returnsSuccess() throws Exception {
        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_1, ENCRYPTION_KEY_TYPE_AUCTION))
                .isNull();
        mEncryptionContextDao.insertEncryptionContext(getDbEncryptionContext(CONTEXT_ID_1));
        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_1, ENCRYPTION_KEY_TYPE_AUCTION))
                .isNotNull();
    }

    private DBEncryptionContext getDbEncryptionContext(long contextId) throws Exception {
        return DBEncryptionContext.builder()
                .setContextId(contextId)
                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                .setKeyConfig(mObliviousHttpKeyConfig)
                .setSharedSecret(SHARED_SECRET)
                .setHpkeContextNativeRef(HPKE_REF)
                .build();
    }

    private DBEncryptionContext getExpectedDbEncryptionContext(DBEncryptionContext insertedContext)
            throws Exception {
        return DBEncryptionContext.builder()
                .setContextId(insertedContext.getContextId())
                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                .setKeyConfig(ObliviousHttpKeyConfig.fromSerializedKeyConfig(KEY_CONFIG_BYTES))
                .setSharedSecret(insertedContext.getSharedSecret())
                .setHpkeContextNativeRef(insertedContext.getHpkeContextNativeRef())
                .build();
    }
}
