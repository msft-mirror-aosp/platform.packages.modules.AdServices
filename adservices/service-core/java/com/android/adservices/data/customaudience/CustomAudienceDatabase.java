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

package com.android.adservices.data.customaudience;

import android.content.Context;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RenameColumn;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import com.google.errorprone.annotations.concurrent.GuardedBy;

/** Room based database for custom audience. */
@Database(
        entities = {
            DBCustomAudience.class,
            DBCustomAudienceBackgroundFetchData.class,
            DBCustomAudienceOverride.class,
            DBCustomAudienceQuarantine.class,
            DBPartialCustomAudience.class,
            DBScheduledCustomAudienceUpdate.class,
            DBCustomAudienceToLeave.class,
            DBComponentAdData.class
        },
        version = CustomAudienceDatabase.DATABASE_VERSION,
        autoMigrations = {
            @AutoMigration(from = 1, to = 2, spec = CustomAudienceDatabase.AutoMigration1To2.class),
            @AutoMigration(from = 2, to = 3),
            @AutoMigration(from = 3, to = 4),
            @AutoMigration(from = 4, to = 5),
            @AutoMigration(from = 5, to = 6),
            @AutoMigration(from = 7, to = 8),
            @AutoMigration(from = 8, to = 9),
            @AutoMigration(from = 9, to = 10),
            @AutoMigration(from = 10, to = 11),
        })
@TypeConverters({FledgeRoomConverters.class})
public abstract class CustomAudienceDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final int DATABASE_VERSION = 11;
    // TODO(b/230653780): Should we separate the DB.
    public static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("customaudience.db");

    @RenameColumn(
            tableName = DBCustomAudience.TABLE_NAME,
            fromColumnName = "bidding_logic_url",
            toColumnName = "bidding_logic_uri")
    @RenameColumn(
            tableName = DBCustomAudience.TABLE_NAME,
            fromColumnName = "trusted_bidding_data_url",
            toColumnName = "trusted_bidding_data_uri")
    @RenameColumn(
            tableName = DBCustomAudienceBackgroundFetchData.TABLE_NAME,
            fromColumnName = "daily_update_url",
            toColumnName = "daily_update_uri")
    static class AutoMigration1To2 implements AutoMigrationSpec {}

    @VisibleForTesting
    static final Migration MIGRATION_7_8 =
            new Migration(7, 8) {
                @Override
                public void migrate(SupportSQLiteDatabase db) {
                    final TableInfo info =
                            TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);

                    if (!info.columns.containsKey("is_debuggable")) {
                        db.execSQL(
                                "ALTER TABLE `scheduled_custom_audience_update` ADD COLUMN"
                                        + " `is_debuggable` INTEGER NOT NULL DEFAULT false");
                    }
                }
            };

    @GuardedBy("SINGLETON_LOCK")
    private static volatile CustomAudienceDatabase sSingleton;

    // TODO: How we want handle synchronized situation (b/228101878).

    /** Returns an instance of the CustomAudienceDatabase given a context. */
    public static CustomAudienceDatabase getInstance() {
        // Initialization pattern recommended on page 334 of "Effective Java" 3rd edition
        CustomAudienceDatabase singleReadResult = sSingleton;
        if (singleReadResult != null) {
            return singleReadResult;
        }
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                Context context = ApplicationContextSingleton.get();
                DBCustomAudience.Converters converters =
                        new DBCustomAudience.Converters(
                                BinderFlagReader.readFlag(
                                        () ->
                                                FlagsFactory.getFlags()
                                                        .getFledgeFrequencyCapFilteringEnabled()),
                                BinderFlagReader.readFlag(
                                        () ->
                                                FlagsFactory.getFlags()
                                                        .getFledgeAppInstallFilteringEnabled()),
                                BinderFlagReader.readFlag(
                                        () ->
                                                FlagsFactory.getFlags()
                                                        .getFledgeAuctionServerAdRenderIdEnabled()));
                sSingleton =
                        FileCompatUtils.roomDatabaseBuilderHelper(
                                        context, CustomAudienceDatabase.class, DATABASE_NAME)
                                .addMigrations(MIGRATION_7_8)
                                .fallbackToDestructiveMigration(true)
                                .addTypeConverter(converters)
                                .build();
            }
            return sSingleton;
        }
    }

    /**
     * Custom Audience Dao.
     *
     * @return Dao to access custom audience storage.
     */
    public abstract CustomAudienceDao customAudienceDao();
}
