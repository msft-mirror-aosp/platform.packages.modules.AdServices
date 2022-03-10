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

package com.android.adservices.data.topics;

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.data.DbHelper;

/**
 * Data Access Object for the Topics PPAPI module.
 */
public final class TopicsDao {
    private static TopicsDao sSingleton;

    @SuppressWarnings("unused")
    private final DbHelper mDbHelper; // Used in tests.

    private TopicsDao(DbHelper dbHelper) {
        mDbHelper = dbHelper;
    }

    /** Returns an instance of the TopicsDAO given a context. */
    @NonNull
    public static TopicsDao getInstance(@NonNull Context ctx) {
        synchronized (TopicsDao.class) {
            if (sSingleton == null) {
                sSingleton = new TopicsDao(DbHelper.getInstance(ctx));
            }
            return sSingleton;
        }
    }
}
