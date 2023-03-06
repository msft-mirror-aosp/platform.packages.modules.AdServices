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

package com.android.adservices.data.adselection;

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/**
 * Data access object abstract class for running app install related queries.
 *
 * <p>Annotation will generate Room based SQLite Dao implementation.
 */
@Dao
public abstract class AppInstallDao {

    /**
     * Checks if a buyer is allowed to filter a given package.
     *
     * @param buyer the name of the buyer.
     * @param packageName the name of the package.
     * @return true if the (buyer, package name) pair is in the database, false otherwise
     */
    @Query(
            "SELECT EXISTS(SELECT 1 FROM app_install WHERE buyer = :buyer"
                    + " AND package_name = :packageName)")
    public abstract boolean canBuyerFilterPackage(
            @NonNull AdTechIdentifier buyer, @NonNull String packageName);

    /**
     * Insert new buyer, package pairs which will allow the buyer to filter on the package. If the
     * entry already exists, nothing is inserted or changed.
     *
     * @param appInstalls The buyer, package pairs to insert
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract void insertAllAppInstallPermissions(
            @NonNull List<DBAppInstallPermissions> appInstalls);

    /**
     * Removes all entries associated with a package
     *
     * @param packageName The name of the package.
     */
    @Query("DELETE FROM app_install WHERE package_name = :packageName")
    public abstract int deleteByPackageName(@NonNull String packageName);

    /**
     * Runs deleteByPackageName on the given packagename then insertAllAppInstallPermissions on the
     * given list of DBAppInstallPermissions in a single transaction. Note that there is no that the
     * package/packages in the DBAppInstallPermissions match the packageName parameter.
     *
     * @param packageName The package name to clear all entries for
     * @param appInstalls The DBAppInstallPermissions to insert
     */
    @Transaction
    public void setAdTechsForPackage(
            @NonNull String packageName, @NonNull List<DBAppInstallPermissions> appInstalls) {
        deleteByPackageName(packageName);
        insertAllAppInstallPermissions(appInstalls);
    }
}
