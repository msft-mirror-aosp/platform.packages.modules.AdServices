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

package com.android.adservices.service.measurement.registration;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Wrapper for a list of redirect Uris and a redirect type */
public class AsyncRedirect {
    private final List<Uri> mRedirects;
    private @AsyncRegistration.RedirectType int mRedirectType;

    public AsyncRedirect() {
        mRedirects = new ArrayList<>();
        mRedirectType = AsyncRegistration.RedirectType.ANY;
    }

    public AsyncRedirect(List<Uri> redirects, @AsyncRegistration.RedirectType int redirectType) {
        mRedirects = redirects;
        mRedirectType = redirectType;
    }

    /** The list the redirect Uris */
    public List<Uri> getRedirects() {
        return new ArrayList<>(mRedirects);
    }

    /** The redirect type */
    public @AsyncRegistration.RedirectType int getRedirectType() {
        return mRedirectType;
    }

    /** Add to the list the redirect Uris */
    public void addToRedirects(List<Uri> uris) {
        mRedirects.addAll(uris);
    }

    public void setRedirectType(@AsyncRegistration.RedirectType int redirectType) {
        mRedirectType = redirectType;
    }
}
