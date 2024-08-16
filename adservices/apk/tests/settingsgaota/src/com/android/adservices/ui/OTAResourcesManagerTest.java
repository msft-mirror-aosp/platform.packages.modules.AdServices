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
package com.android.adservices.ui;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_OTA_FILE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RESOURCES_PROVIDER_ADD_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;

import android.content.res.loader.ResourcesProvider;
import android.os.ParcelFileDescriptor;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.concurrent.ExecutionException;

@SpyStatic(MobileDataDownloadFactory.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(ParcelFileDescriptor.class)
@SpyStatic(ResourcesProvider.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX)
public final class OTAResourcesManagerTest extends AdServicesExtendedMockitoTestCase {
    @Mock
    MobileDataDownload mMockMdd;
    @Mock
    ParcelFileDescriptor mMockParcelFileDescriptor;

    @Before
    public void setUp() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(ApkTestUtil.isDeviceSupported());

        doReturn(mMockMdd).when(() -> MobileDataDownloadFactory.getMdd(any(Flags.class)));
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockParcelFileDescriptor)
                .when(() -> ParcelFileDescriptor.open(any(File.class), anyInt()));
    }

    /** Verify that MDD throws an ExecutionException, getDownloadedFiles return a null object. */
    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE)
    public void testGetDownloadedFiles_futureExecutionException() {
        ListenableFuture<ClientFileGroup> testFuture =
                Futures.immediateFailedFuture(
                        new ExecutionException("mockExecutionException", new Throwable()));
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        assertThat(OTAResourcesManager.getDownloadedFiles()).isNull();
    }

    /**
     * * Verify that MDD throws an InterruptedException, getDownloadedFiles return a null object.
     */
    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE)
    public void testGetDownloadedFiles_futureInterruptedException() {
        ListenableFuture<ClientFileGroup> testFuture =
                Futures.immediateFailedFuture(new InterruptedException("mockInterruptedException"));
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        assertThat(OTAResourcesManager.getDownloadedFiles()).isNull();
    }

    /**
     * * Verify that when the file group is not downloaded, getDownloadedFiles return an empty map.
     */
    @Test
    public void testGetDownloadedFiles_nullFileGroup() {
        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(null);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        assertThat(OTAResourcesManager.getDownloadedFiles()).isNull();
    }

    /**
     * * Verify that MDD downloads an empty client file group, getDownloadedFiles returns an empty
     * map.
     */
    @Test
    public void testGetDownloadedFiles_emptyClientFileGroup() {
        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder().setStatus(ClientFileGroup.Status.DOWNLOADED).build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        assertThat(OTAResourcesManager.getDownloadedFiles()).hasSize(0);
    }

    /** Verify that when MDD download is pending, getDownloadedFiles returns null. */
    @Test
    public void testGetDownloadedFiles_pendingClientFileGroup() {
        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder()
                        .setGroupName("testGroupName")
                        .setOwnerPackage("testOwnerPackageName")
                        .setVersionNumber(0)
                        .setStatus(ClientFileGroup.Status.PENDING)
                        .build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        assertThat(OTAResourcesManager.getDownloadedFiles()).isNull();
    }

    /**
     * * Verify that MDD downloads a valid file group, getDownloadedFiles return a non-empty map.
     */
    @Test
    public void testGetDownloadedFiles_validClientFileGroup() {
        ClientFile testCf = ClientFile.newBuilder().setFileId("testFileId1").build();

        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder()
                        .setGroupName("testGroupName")
                        .setOwnerPackage("testOwnerPackageName")
                        .setVersionNumber(0)
                        .setStatus(ClientFileGroup.Status.DOWNLOADED)
                        .addFile(testCf)
                        .build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        assertThat(OTAResourcesManager.getDownloadedFiles()).hasSize(1);
    }

    /** Verify that when an empty file group was downloaded, file descriptor was never used. */
    @Test
    public void testRefreshOTAResources_emptyClientFileGroup() throws Exception {
        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder().setStatus(ClientFileGroup.Status.DOWNLOADED).build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        OTAResourcesManager.refreshOTAResources(mContext);

        verify(mMockParcelFileDescriptor, times(0)).close();
    }

    /**
     * * Verify that when a valid file group but (non-ota) was downloaded, * file descriptor was
     * never used.
     */
    @Test
    public void testRefreshOTAResources_nonOTAClientFileGroup() throws Exception {
        ClientFile testCf = ClientFile.newBuilder().setFileId("testFileId1").build();

        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder()
                        .setGroupName("testGroupName")
                        .setOwnerPackage("testOwnerPackageName")
                        .setVersionNumber(0)
                        .setStatus(ClientFileGroup.Status.DOWNLOADED)
                        .addFile(testCf)
                        .build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        OTAResourcesManager.refreshOTAResources(mContext);

        verify(mMockParcelFileDescriptor, times(0)).close();
    }

    /**
     * * Verify that when a valid file group (arsc) was downloaded but files were corrupted (no
     * uri), * file descriptor was never used.
     */
    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_OTA_FILE_ERROR)
    public void testRefreshOTAResources_nonUriClientFile() throws Exception {
        ClientFile testCf = ClientFile.newBuilder().setFileId("resources.arsc").build();

        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder()
                        .setGroupName("testGroupName")
                        .setOwnerPackage("testOwnerPackageName")
                        .setVersionNumber(0)
                        .setStatus(ClientFileGroup.Status.DOWNLOADED)
                        .addFile(testCf)
                        .build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));

        OTAResourcesManager.refreshOTAResources(mContext);

        verify(mMockParcelFileDescriptor, times(0)).close();
    }

    /**
     * * Verify that when a valid file group (arsc) was downloaded but file descriptor contains no *
     * providers, file descriptor was never closed.
     */
    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RESOURCES_PROVIDER_ADD_ERROR)
    public void testRefreshOTAResources_nullResourceArsc() throws Exception {
        ClientFile testCf =
                ClientFile.newBuilder()
                        .setFileId(OTAResourcesManager.DOWNLOADED_OTA_FILE_ID)
                        .setFileUri("testUrl")
                        .build();

        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder()
                        .setGroupName("testGroupName")
                        .setOwnerPackage("testOwnerPackageName")
                        .setVersionNumber(0)
                        .setStatus(ClientFileGroup.Status.DOWNLOADED)
                        .addFile(testCf)
                        .build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));
        doReturn(null).when(() -> ResourcesProvider.loadFromTable(mMockParcelFileDescriptor, null));

        OTAResourcesManager.refreshOTAResources(mContext);

        verify(mMockParcelFileDescriptor, times(0)).close();
    }

    /**
     * * Verify that when a valid file group (apk) was downloaded but file descriptor contains no *
     * providers, file descriptor was never closed.
     */
    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RESOURCES_PROVIDER_ADD_ERROR)
    public void testRefreshOTAResources_nullResourceApk() throws Exception {
        ClientFile testCf =
                ClientFile.newBuilder()
                        .setFileId(OTAResourcesManager.DOWNLOADED_OTA_APK_ID)
                        .setFileUri("testUrl")
                        .build();

        ClientFileGroup testCfg =
                ClientFileGroup.newBuilder()
                        .setGroupName("testGroupName")
                        .setOwnerPackage("testOwnerPackageName")
                        .setVersionNumber(0)
                        .setStatus(ClientFileGroup.Status.DOWNLOADED)
                        .addFile(testCf)
                        .build();

        ListenableFuture<ClientFileGroup> testFuture = Futures.immediateFuture(testCfg);
        doReturn(testFuture).when(mMockMdd).getFileGroup(any(GetFileGroupRequest.class));
        doReturn(null).when(() -> ResourcesProvider.loadFromApk(mMockParcelFileDescriptor, null));

        OTAResourcesManager.refreshOTAResources(mContext);

        verify(mMockParcelFileDescriptor, times(0)).close();
    }
}
