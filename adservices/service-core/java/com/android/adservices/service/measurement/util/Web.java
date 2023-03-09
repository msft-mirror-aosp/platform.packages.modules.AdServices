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

package com.android.adservices.service.measurement.util;

import android.net.Uri;

import com.google.common.net.InternetDomainName;

import java.util.Optional;

/** Web utilities for measurement. */
public final class Web {

    private Web() { }

    /**
     * Returns a {@code Uri} of the scheme concatenated with the first subdomain of the provided
     * URL that is beneath the public suffix.
     *
     * @param uri the Uri to parse.
     */
    public static Optional<Uri> topPrivateDomainAndScheme(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return Optional.empty();
        }

        try {
            InternetDomainName domainName = InternetDomainName.from(host);
            String url = scheme + "://" + domainName.topPrivateDomain();
            return Optional.of(Uri.parse(url));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns a {@code Uri} of the scheme concatenated with the first subdomain of the provided
     * URL that is beneath the public suffix.
     *
     * @param uri the URL string to parse.
     */
    public static Optional<Uri> topPrivateDomainAndScheme(String uri) {
        return topPrivateDomainAndScheme(Uri.parse(uri));
    }
}
