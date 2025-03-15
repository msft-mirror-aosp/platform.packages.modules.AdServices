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

import com.android.adservices.service.common.CoordinatorOriginUriValidator;

import java.util.List;
import java.util.Objects;

public class ServerAuctionProdCoordinatorUriStrategy
        implements ServerAuctionCoordinatorUriStrategy {
    private final List<Uri> mAllowList;

    public ServerAuctionProdCoordinatorUriStrategy(@NonNull List<Uri> allowList) {
        Objects.requireNonNull(allowList);
        mAllowList = allowList;
    }

    @Override
    public CoordinatorOriginUriValidator getCoordinatorOriginUriValidator() {
        return CoordinatorOriginUriValidator.createEnabledInstance(mAllowList);
    }

    @Override
    public Uri getAuctionEncryptionKeyFetchUri(Uri coordinatorOriginUri) {
        return mAllowList.stream()
                .filter(
                        allowedUri ->
                                Objects.equals(
                                        coordinatorOriginUri.getHost(), allowedUri.getHost()))
                .findFirst()
                .orElse(null);
    }
}
