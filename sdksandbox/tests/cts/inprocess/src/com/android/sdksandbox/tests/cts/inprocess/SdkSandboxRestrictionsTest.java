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

package com.android.sdksandbox.tests.cts.inprocess;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.UUID;

/**
 * Tests to check SDK sandbox process restrictions.
 */
@RunWith(JUnit4.class)
public class SdkSandboxRestrictionsTest {

    /**
     * Tests that sandbox cannot access the Widevine ID.
     */
    @Test
    public void testNoWidevineAccess() throws Exception {
        UUID widevineUuid = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

        UnsupportedSchemeException thrown = assertThrows(
                UnsupportedSchemeException.class,
                () -> new MediaDrm(widevineUuid));
        assertThat(thrown).hasMessageThat().contains("NO_INIT");
    }

    /**
     * Tests that the SDK sandbox cannot broadcast to PermissionController to request permissions.
     */
    @Test
    public void testCannotRequestPermissions() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Intent intent = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
        intent.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES,
                new String[] {Manifest.permission.INSTALL_PACKAGES});
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String packageName;
        try {
            packageName = context.getPackageManager().getPermissionControllerPackageName();
        } catch (Exception e) {
            packageName = "test.package";
        }
        intent.setPackage(packageName);

        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> context.startActivity(intent));
        assertThat(thrown).hasMessageThat().contains(
                "may not be broadcast from an SDK sandbox uid");
    }

    /**
     * Tests that sandbox cannot send implicit broadcast intents.
     */
    @Test
    public void testNoImplicitIntents() {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "text");
        sendIntent.setType("text/plain");
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> ctx.startActivity(sendIntent));
        assertThat(thrown).hasMessageThat().contains("may not be broadcast from an SDK sandbox");
    }

    /**
     * Tests that sandbox can open URLs in a browser.
     */
    @Test
    public void testUrlViewIntents() {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.android.com"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ctx.startActivity(intent);
    }

    /**
     * Tests that sandbox cannot access hidden API methods via reflection.
     */
    @Test
    public void testNoHiddenApiAccess() {
        assertThrows(NoSuchMethodException.class,
                () -> SandboxedSdkContext.class.getDeclaredMethod("getSdkName"));
    }

    /**
     * Tests that Sdk Sandbox cannot access app specific external storage
     */
    @Test
    public void testSanboxCannotAccess_AppSpecificFiles() throws Exception {
        // Check that the sandbox does not have legacy external storage access
        assertThat(Environment.isExternalStorageLegacy()).isFalse();

         // Can't read ExternalStorageDir
        assertThat(Environment.getExternalStorageDirectory().list()).isNull();

        final String[] types = new String[] {
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_PODCASTS,
                Environment.DIRECTORY_RINGTONES,
                Environment.DIRECTORY_ALARMS,
                Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_DOCUMENTS
        };

        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (String type : types) {
            File dir = ctx.getExternalFilesDir(type);
            assertThat(dir).isNull();
        }

        // Also, cannot access app-specific cache files
        assertThat(ctx.getExternalCacheDir()).isNull();
    }

    /** Tests that Sdk Sandbox cannot access app specific external storage */
    @Test
    @Ignore("b/234563287")
    public void testSanboxCannotAccess_MediaStoreApi() throws Exception {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final ContentResolver resolver = ctx.getContentResolver();

        // Cannot create new item on media store
        final Uri audioCollection = MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
        final ContentValues newItem = new ContentValues();
        newItem.put(MediaStore.Audio.Media.DISPLAY_NAME, "New Audio Item");
        newItem.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> resolver.insert(audioCollection, newItem));
        assertThat(thrown).hasMessageThat().contains("Unknown URL content");

        // Cannot query on media store
        String[] projection = new String[] {
            MediaStore.Audio.Media._ID,
        };
        try (Cursor cursor = resolver.query(audioCollection, projection, null, null, null, null)) {
            assertThat(cursor).isNull();
        }
    }

    /**
     * Tests that Sdk Sandbox cannot access Storage Access Framework
     */
    @Test
    public void testSanboxCannotAccess_StorageAccessFramework() throws Exception {
        final String[] intentList = {
                Intent.ACTION_CREATE_DOCUMENT,
                Intent.ACTION_OPEN_DOCUMENT,
                Intent.ACTION_OPEN_DOCUMENT_TREE};

        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (int i = 0; i < intentList.length; i++) {
            Intent intent = new Intent(intentList[i]);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            SecurityException thrown = assertThrows(SecurityException.class,
                    () -> ctx.startActivity(intent));
            assertThat(thrown).hasMessageThat().contains(
                    "may not be broadcast from an SDK sandbox uid");
        }
    }

    /** Test that sdk sandbox can't grant read uri permission. */
    @Test
    public void testCheckUriPermission() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Uri uri = Uri.parse("content://com.example.sdk.provider/abc");
        int ret =
                context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        assertThat(ret).isEqualTo(PackageManager.PERMISSION_DENIED);
    }
}
