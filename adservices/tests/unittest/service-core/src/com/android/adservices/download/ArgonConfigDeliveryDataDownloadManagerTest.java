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

package com.android.adservices.download;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.android.adservices.data.configdelivery.ArgonConfigurationManager;
import com.android.adservices.service.Flags;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.configdelivery.ArgonConfigurationManager;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.proto.config_delivery.MddConfigs;
import com.android.adservices.service.proto.config_delivery.MddConfigs.MddConfig;
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;

@SpyStatic(FlagsFactory.class)
@MockStatic(MobileDataDownloadFactory.class)
@MockStatic(ArgonConfigurationManager.class)
@SetErrorLogUtilDefaultParams(throwable = Any.class)
public final class ArgonConfigDeliveryDataDownloadManagerTest
        extends AdServicesExtendedMockitoTestCase {
    private static final String TEST_CONFIG_FILE_PATH = "argon_config.binarypb";
    private ArgonConfigDeliveryDataDownloadManager mArgonConfigDeliveryDataDownloadManager;
    @Mock private SynchronousFileStorage mMockFileStorage;
    @Mock private MobileDataDownload mMockMdd;

    private static class InterruptedListenableFuture<V> extends AbstractFuture<V> {
        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException();
        }

        @Override
        public V get() throws InterruptedException {
            throw new InterruptedException();
        }
    }

    @Before
    public void setup() {
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        mocker.mockGetFlags(mMockFlags);
        MddConfigs mddConfigs =
                MddConfigs.newBuilder()
                        .addMddConfigs(
                                MddConfig.newBuilder()
                                        .setManifestId("test_manifest_id")
                                        .addFileGroupNames("test_file_group")
                                        .build())
                        .build();
        when(mMockFlags.getConfigDeliveryMddConfigs()).thenReturn(mddConfigs);
    }

    @After
    public void tearDown() throws Exception {
        // Reset singleton instance between tests to avoid test pollution.
        Field instance = ArgonConfigDeliveryDataDownloadManager.class.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void testGetInstance() {
        ArgonConfigDeliveryDataDownloadManager firstInstance =
                ArgonConfigDeliveryDataDownloadManager.getInstance();
        ArgonConfigDeliveryDataDownloadManager secondInstance =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        assertThat(firstInstance).isSameInstanceAs(secondInstance);
    }

    @Test
    public void testSyncArgonConfigurations_success() throws Exception {
        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId(TEST_CONFIG_FILE_PATH)
                                                        .setFileUri(TEST_CONFIG_FILE_PATH)
                                                        .build())
                                        .build()));
        InputStream inputStream =
                new ByteArrayInputStream(VersionedConfiguration.getDefaultInstance().toByteArray());
        when(mMockFileStorage.open(any(), any())).thenReturn(inputStream);
        mArgonConfigDeliveryDataDownloadManager =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        mArgonConfigDeliveryDataDownloadManager.syncArgonConfigurations();

        verify(() -> ArgonConfigurationManager.insertConfigurationsIfNotExist(any()));
    }

    @Test
    public void testSyncArgonConfigurations_noFileGroup() {
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(null));
        mArgonConfigDeliveryDataDownloadManager =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        mArgonConfigDeliveryDataDownloadManager.syncArgonConfigurations();

        verify(() -> ArgonConfigurationManager.insertConfigurationsIfNotExist(any()), never());
    }

    @Test
    public void testSyncArgonConfigurations_emptyFileList() {
        when(mMockMdd.getFileGroup(any()))
                .thenReturn(Futures.immediateFuture(ClientFileGroup.newBuilder().build()));
        mArgonConfigDeliveryDataDownloadManager =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        mArgonConfigDeliveryDataDownloadManager.syncArgonConfigurations();

        verify(() -> ArgonConfigurationManager.insertConfigurationsIfNotExist(any()), never());
    }

    @Test
    public void testSyncArgonConfigurations_fileStorageIOException() throws Exception {
        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId(TEST_CONFIG_FILE_PATH)
                                                        .setFileUri(TEST_CONFIG_FILE_PATH)
                                                        .build())
                                        .build()));
        when(mMockFileStorage.open(any(), any())).thenThrow(new IOException());
        mArgonConfigDeliveryDataDownloadManager =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        mArgonConfigDeliveryDataDownloadManager.syncArgonConfigurations();

        verify(() -> ArgonConfigurationManager.insertConfigurationsIfNotExist(any()), never());
    }

    @Test
    public void testSyncArgonConfigurations_fileGroupFutureException() {
        when(mMockMdd.getFileGroup(any()))
                .thenReturn(Futures.immediateFailedFuture(new CancellationException()));
        mArgonConfigDeliveryDataDownloadManager =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        mArgonConfigDeliveryDataDownloadManager.syncArgonConfigurations();

        verify(() -> ArgonConfigurationManager.insertConfigurationsIfNotExist(any()), never());
    }

    @Test
    public void testSyncArgonConfigurations_interruptedException_restoresThreadState()
            throws Exception {
        when(mMockMdd.getFileGroup(any())).thenReturn(new InterruptedListenableFuture<>());
        mArgonConfigDeliveryDataDownloadManager =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        mArgonConfigDeliveryDataDownloadManager.syncArgonConfigurations();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // Cleanup interrupted status for subsequent tests
        Thread.interrupted();
    }

    @Test
    public void testSyncArgonConfigurations_insertConfigurationsException() throws Exception {
        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId(TEST_CONFIG_FILE_PATH)
                                                        .setFileUri(TEST_CONFIG_FILE_PATH)
                                                        .build())
                                        .build()));
        InputStream inputStream =
                new ByteArrayInputStream(VersionedConfiguration.getDefaultInstance().toByteArray());
        when(mMockFileStorage.open(any(), any())).thenReturn(inputStream);
        doThrow(new RuntimeException("DB error"))
                .when(() -> ArgonConfigurationManager.insertConfigurationsIfNotExist(any()));
        mArgonConfigDeliveryDataDownloadManager =
                ArgonConfigDeliveryDataDownloadManager.getInstance();

        mArgonConfigDeliveryDataDownloadManager.syncArgonConfigurations();

        verify(() -> ArgonConfigurationManager.insertConfigurationsIfNotExist(any()));
    }

    @Test
    public void testGenerateArgonConfigManifestId() {
        String manifestIdUniqueWithinArgonConfig = "test_manifest_id";

        String manifestId =
                ArgonConfigDeliveryDataDownloadManager.generateArgonConfigManifestId(
                        manifestIdUniqueWithinArgonConfig);

        assertThat(manifestId).isEqualTo("ArgonConfigManifest_test_manifest_id");
    }
}