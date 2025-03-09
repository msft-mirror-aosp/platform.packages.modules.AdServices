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

package com.android.adservices.service.adselection.encryption;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.devapi.DevContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Factory for {@link ServerAuctionCoordinatorUriStrategy}s */
public class ServerAuctionCoordinatorUriStrategyFactory {
    private final String mAllowList;

    public ServerAuctionCoordinatorUriStrategyFactory(@NonNull String allowList) {
        Objects.requireNonNull(allowList);
        mAllowList = allowList;
    }

    /**
     * Returns the appropriate ServerAuctionCoordinatorUriStrategy based whether server auction test
     * keys are enabled
     *
     * @param devContext the dev context associated with the caller package.
     * @return An implementation of ServerAuctionCoordinatorUriStrategy
     */
    public ServerAuctionCoordinatorUriStrategy createStrategy(DevContext devContext) {

        boolean isServerAuctionTestKeysEnabled =
                devContext.getDevSession().isServerAuctionTestKeysEnabled();

        if (isServerAuctionTestKeysEnabled) {
            return new ServerAuctionTestCoordinatorUriStrategy();
        }

        return new ServerAuctionProdCoordinatorUriStrategy(
                getListOfUrisFromCommaSeparatedAllowlist());
    }

    @VisibleForTesting
    protected List<Uri> getListOfUrisFromCommaSeparatedAllowlist() {
        List<Uri> allowlist = new ArrayList<>();

        for (String str : AllowLists.splitAllowList(mAllowList)) {
            allowlist.add(Uri.parse(str));
        }

        return allowlist;
    }
}
