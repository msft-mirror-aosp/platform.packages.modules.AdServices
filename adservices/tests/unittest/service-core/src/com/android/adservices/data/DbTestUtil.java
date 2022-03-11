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

package com.android.adservices.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

public final class DbTestUtil {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final DbHelper sDbHelper = DbHelper.getInstanceForTest(sContext);

    /**  Erases all data from the table rows */
    public static void deleteTable(String tableName) {
        SQLiteDatabase db = sDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        db.delete(tableName, /* whereClause = */ null, /* whereArgs = */ null);
    }
}
