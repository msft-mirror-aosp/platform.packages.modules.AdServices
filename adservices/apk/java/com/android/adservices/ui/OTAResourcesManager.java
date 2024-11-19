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

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_OTA_FILE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RESOURCES_PROVIDER_ADD_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Manages OTA (over the air) Resources downloaded from MDD. This allows device to use updated OTA
 * resources. Currently only strings are supported.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class OTAResourcesManager {
    // this value needs to be updated if bundled resources are updated
    private static final long BUNDLED_RESOURCES_VERSION = 4265;
    private static final long NO_OTA_RESOURCES_VERSION = -1;
    private static final String FILE_GROUP_NAME = "ui-ota-strings";
    public static final String DOWNLOADED_OTA_FILE_ID = "resources.arsc";
    public static final String DOWNLOADED_OTA_APK_ID = "AdServicesOtaResourcesApp.apk";
    private static final ResourcesLoader OTAResourcesLoader = new ResourcesLoader();

    private static long sOTAResourcesVersion = NO_OTA_RESOURCES_VERSION;

    /**
     * If shouldRefresh, then create a new OTA {@link ResourcesLoader} from ARSC file on device.
     * Checks if OTA version > bundled version: If true, then add OTAResourcesLoader to the current
     * context's {@link Resources}. Else, do nothing.
     *
     * @param context {@link Context}
     */
    @SuppressWarnings("AvoidStaticContext") // UX class
    public static void applyOTAResources(Context context, boolean shouldRefresh) {
        if (shouldRefresh || sOTAResourcesVersion == NO_OTA_RESOURCES_VERSION) {
            refreshOTAResources(context.getApplicationContext());
        }
        if (sOTAResourcesVersion > BUNDLED_RESOURCES_VERSION) {
            context.getApplicationContext().getResources().addLoaders(OTAResourcesLoader);
        }
    }

    @SuppressWarnings("AvoidStaticContext") // UX class
    static void refreshOTAResources(Context context) {
        LogUtil.d("createResourceLoaderFromMDDFiles called.");
        Map<String, ClientFile> downloadedOTAFiles = getDownloadedFiles();

        // check if there are OTA Resources
        if (downloadedOTAFiles == null || downloadedOTAFiles.size() == 0) {
            return;
        }
        // get OTA strings file
        File resourcesFile = getOtaFile(context, downloadedOTAFiles, DOWNLOADED_OTA_FILE_ID);
        // get OTA resources apk
        File resourcesApk = getOtaFile(context, downloadedOTAFiles, DOWNLOADED_OTA_APK_ID);
        if (resourcesFile == null && resourcesApk == null) {
            LogUtil.d("No OTA files");
            return;
        }

        // Clear previous ResourceProvider
        OTAResourcesLoader.clearProviders();
        // Add new ResourceProvider created from arsc file
        if (resourcesFile != null) {
            try {
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(resourcesFile, MODE_READ_ONLY);
                OTAResourcesLoader.addProvider(ResourcesProvider.loadFromTable(fd, null));
                fd.close();
            } catch (Exception e) {
                LogUtil.e("Caught exception while adding OTA string provider: " + e.getMessage());
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RESOURCES_PROVIDER_ADD_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                OTAResourcesLoader.clearProviders();
            }
        }
        // Add new ResourceProvider created from OTA apk
        else if (resourcesApk != null) {
            try {
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(resourcesApk, MODE_READ_ONLY);
                OTAResourcesLoader.addProvider(ResourcesProvider.loadFromApk(fd, null));
                fd.close();
            } catch (Exception e) {
                LogUtil.e("Caught exception while adding OTA apk provider: " + e.getMessage());
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__RESOURCES_PROVIDER_ADD_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            }
        }
    }

    @SuppressWarnings("AvoidStaticContext") // UX class
    private static File getOtaFile(
            Context context, Map<String, ClientFile> otaFilesMap, String fileId) {
        // get OTA file
        ClientFile otaFile = otaFilesMap.get(fileId);
        if (otaFile == null) {
            LogUtil.d(fileId + " not found");
            return null;
        }
        if (!otaFile.hasFileUri()) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_OTA_FILE_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            return null;
        }
        @SuppressLint("NewAdServicesFile")
        File f = new File(context.getDataDir() + Uri.parse(otaFile.getFileUri()).getPath());
        LogUtil.d("got this file:" + otaFile.getFileUri());
        return f;
    }

    /**
     * This function populates metadata files to a map.
     *
     * @return A {@link Map} containing downloaded fileId mapped to ClientFile or null if no
     *     downloaded files found.
     */
    static @Nullable Map<String, ClientFile> getDownloadedFiles() {
        LogUtil.d("getDownloadedFiles called.");
        MobileDataDownload mobileDataDownload =
                MobileDataDownloadFactory.getMdd(FlagsFactory.getFlags());
        GetFileGroupRequest getFileGroupRequest =
                GetFileGroupRequest.newBuilder().setGroupName(FILE_GROUP_NAME).build();
        ClientFileGroup fileGroup;
        try {
            // TODO(b/242908564):We potentially cannot do callback here since we need to get the OTA
            //  strings before we create the UI, as the UI needs the updated strings if they exist.
            fileGroup = mobileDataDownload.getFileGroup(getFileGroupRequest).get();
        } catch (ExecutionException | InterruptedException e) {
            LogUtil.e(e, "Unable to load MDD file group for " + FILE_GROUP_NAME);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            return null;
        }

        if (fileGroup == null || fileGroup.getStatus() != ClientFileGroup.Status.DOWNLOADED) {
            return null;
        }
        LogUtil.d("found fileGroup: " + fileGroup);
        Map<String, ClientFile> downloadedFiles = new ArrayMap<>();
        if (fileGroup != null) {
            LogUtil.d("Populating downloadFiles map for " + FILE_GROUP_NAME);
            for (ClientFile file : fileGroup.getFileList()) {
                downloadedFiles.put(file.getFileId(), file);
            }
            LogUtil.d("setting fileGroup version for " + FILE_GROUP_NAME);
            sOTAResourcesVersion = fileGroup.getBuildId();
        }
        return downloadedFiles;
    }
}
