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

package com.android.adservices.service.measurement.registration;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.android.modules.utils.build.SdkLevel;

/** ContentProvider for monitoring changes to {@link AsyncRegistration}. */
public class AsyncRegistrationContentProvider extends ContentProvider {
    /**
     * Gets the Trigger URI of this content provider
     *
     * @return the trigger uri, which can be different based on the Android version (S- vs. T+)
     */
    public static Uri getTriggerUri() {
        String authority =
                SdkLevel.isAtLeastT()
                        ? "com.android.adservices.provider.asyncregistration"
                        : "com.android.ext.adservices.provider.asyncregistration";
        return Uri.parse("content://" + authority);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // 1) Call ContentProvider after insert in DAO
        // Sample Code:
        // Uri triggerUri = AsyncRegistrationContentProvider.getTriggerUri();
        // ContentProviderClient contentProviderClient = ctx.getContentResolver()
        //        .acquireContentProviderClient(triggerUri)
        //        .insert(triggerUri, null);
        // 2) Call ContentProvider for inserting during registration and call DAO from here.
        // Sample Code:
        // MeasurementDAO.getInstance(getContext()).insertAsyncRegistration(asyncReg);
        Uri triggerUri = getTriggerUri();
        getContext().getContentResolver().notifyChange(triggerUri, null);
        return triggerUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
