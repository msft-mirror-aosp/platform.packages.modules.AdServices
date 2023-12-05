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

package com.android.adservices.data.topics.migration;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.topics.TopicsTables;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Migrator to perform DB schema change to version 9 in Topics API. Version 9 is to create new
 * ReturnedEncryptedTopic table.
 */
public class TopicsDbMigratorV9 extends AbstractTopicsDbMigrator {
    private static final int DATABASE_VERSION_V9 = 9;
    // Following go/gmscore-flagging-best-practices, we should clean dated table when upgrading and
    // do nothing when downgrading.
    private static final String[] QUERIES_TO_PERFORM = {
        "DROP TABLE IF EXISTS " + TopicsTables.ReturnedEncryptedTopicContract.TABLE,
        TopicsTables.CREATE_TABLE_RETURNED_ENCRYPTED_TOPIC
    };

    public TopicsDbMigratorV9() {
        super(DATABASE_VERSION_V9);
    }

    @Override
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public void performMigration(SQLiteDatabase db) {
        for (String query : QUERIES_TO_PERFORM) {
            db.execSQL(query);
        }
    }
}
