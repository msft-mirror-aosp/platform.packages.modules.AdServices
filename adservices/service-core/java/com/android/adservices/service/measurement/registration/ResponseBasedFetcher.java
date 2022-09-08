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

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;

import java.util.List;
import java.util.Map;


/**
 * Common handling for Response Based Registration
 *
 * @hide
 */
class ResponseBasedFetcher {
    /**
     * Limit recursion.
     */
    static final int REDIRECT_LIMIT = 5;

    /**
     * Determine all redirects.
     *
     * Generates a list of:
     *   (url, allows_regular_redirects) tuples.
     * Returns true if all steps succeed.
     * Returns false if there are any failures.
     */
    static void parseRedirects(
            boolean initialFetch,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<Uri> redirectsOut) {
        List<String> field = headers.get("Attribution-Reporting-Redirect");
        if (field != null) {
            if (initialFetch) {
                for (int i = 0; i < Math.min(field.size(), REDIRECT_LIMIT); i++) {
                    redirectsOut.add(Uri.parse(field.get(i)));
                }
            } else {
                LogUtil.d("Unexpected use of Attribution-Reporting-Redirect");
            }
        }
    }

    /**
     * Check HTTP response codes that indicate a redirect.
     */
    static boolean isRedirect(int responseCode) {
        // TODO: Decide if all of thse should be allowed.
        return responseCode == 301 ||  // Moved Permanently.
               responseCode == 308 ||  // Permanent Redirect.
               responseCode == 302 ||  // Found
               responseCode == 303 ||  // See Other
               responseCode == 307;    // Temporary Redirect.
    }

    /**
     * Check HTTP response code for success.
     */
    static boolean isSuccess(int responseCode) {
        return responseCode == 200;
    }
}
