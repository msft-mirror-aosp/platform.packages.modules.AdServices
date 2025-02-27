/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.cobalt.CobaltRegistry;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CancellationException;

@SpyStatic(MobileDataDownloadFactory.class)
public final class CobaltDownloadRegistryManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final String DOWNLOADED_REGISTRY_FILE_ID = "cobalt_registry.binarypb";
    private static final String TEST_REGISTRY_ASSET_FILE = "cobalt/cobalt_registry_test.binarypb";
    @Mock SynchronousFileStorage mMockSynchronousFileStorage;
    @Mock MobileDataDownload mMockMdd;
    @Mock ClientFileGroup mMockClientFileGroup;
    ListenableFuture<ClientFileGroup> mSpyClientFileGroupFuture;

    @Test
    public void testGetInstance() {
        CobaltDownloadRegistryManager instance1 = CobaltDownloadRegistryManager.getInstance();
        CobaltDownloadRegistryManager instance2 = CobaltDownloadRegistryManager.getInstance();

        expect.withMessage("getInstance()").that(instance1).isNotNull();
        expect.withMessage("Two getInstance()").that(instance1).isSameInstanceAs(instance2);
    }

    @SuppressLint("CheckResult")
    @Test
    public void testGetMddRegistry_iOException_returnEmpty() throws Exception {
        doThrow(new IOException()).when(mMockSynchronousFileStorage).open(any(), any());
        CobaltDownloadRegistryManager cobaltDownloadRegistryManager =
                createCobaltDownloadRegistryManager(mMockSynchronousFileStorage);
        mockClientFile();

        assertWithMessage("getMddRegistry()")
                .that(cobaltDownloadRegistryManager.getMddRegistry())
                .isEqualTo(Optional.empty());
    }

    @Test
    @SuppressLint("CheckResult")
    public void testGetMddRegistry_returnNonEmpty() throws Exception {
        doReturn(getInputStream()).when(mMockSynchronousFileStorage).open(any(), any());
        CobaltDownloadRegistryManager cobaltDownloadRegistryManager =
                createCobaltDownloadRegistryManager(mMockSynchronousFileStorage);
        mockClientFile();

        assertWithMessage("getMddRegistry()")
                .that(cobaltDownloadRegistryManager.getMddRegistry())
                .isEqualTo(Optional.of(getTestMddRegistry()));
    }

    @Test
    public void testGetMddRegistry_interruptException_returnEmpty() {
        mockClientFileThrowsException(new InterruptedException());
        CobaltDownloadRegistryManager cobaltDownloadRegistryManager =
                createCobaltDownloadRegistryManager(mMockSynchronousFileStorage);

        assertWithMessage("getMddRegistry()")
                .that(cobaltDownloadRegistryManager.getMddRegistry())
                .isEqualTo(Optional.empty());
    }

    @Test
    public void testGetMddRegistry_cancellationException_returnEmpty() {
        mockClientFileThrowsException(new CancellationException());
        CobaltDownloadRegistryManager cobaltDownloadRegistryManager =
                createCobaltDownloadRegistryManager(mMockSynchronousFileStorage);

        assertWithMessage("getMddRegistry()")
                .that(cobaltDownloadRegistryManager.getMddRegistry())
                .isEqualTo(Optional.empty());
    }

    private CobaltDownloadRegistryManager createCobaltDownloadRegistryManager(
            SynchronousFileStorage synchronousFileStorage) {
        return new CobaltDownloadRegistryManager(mMockFlags, synchronousFileStorage);
    }

    private void mockClientFileThrowsException(Throwable th) {
        doReturn(mMockMdd).when(() -> MobileDataDownloadFactory.getMdd(any()));
        mSpyClientFileGroupFuture = Futures.immediateFailedFuture(th);
        when(mMockMdd.getFileGroup(any())).thenReturn(mSpyClientFileGroupFuture);

        ClientFile clientFile =
                ClientFile.newBuilder().setFileId(DOWNLOADED_REGISTRY_FILE_ID).build();
        when(mMockClientFileGroup.getFileList()).thenReturn(Collections.singletonList(clientFile));
    }

    private void mockClientFile() {
        doReturn(mMockMdd).when(() -> MobileDataDownloadFactory.getMdd(any()));
        mSpyClientFileGroupFuture = Futures.immediateFuture(mMockClientFileGroup);
        when(mMockMdd.getFileGroup(any())).thenReturn(mSpyClientFileGroupFuture);

        ClientFile clientFile =
                ClientFile.newBuilder().setFileId(DOWNLOADED_REGISTRY_FILE_ID).build();
        when(mMockClientFileGroup.getFileList()).thenReturn(Collections.singletonList(clientFile));
    }

    private InputStream getInputStream() throws IOException {
        AssetManager assertManager = mContext.getAssets();
        return assertManager.open(TEST_REGISTRY_ASSET_FILE);
    }

    private CobaltRegistry getTestMddRegistry() throws IOException {
        return CobaltRegistry.parseFrom(ByteStreams.toByteArray(getInputStream()));
    }
}
