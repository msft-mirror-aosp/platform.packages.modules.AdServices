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

package com.android.sdksandboxclient;

import android.content.SharedPreferences;

public class BannerOptions {

    public enum ContentType {
        RANDOM_COLOUR
    }

    public enum OnClick {
        OPEN_CHROME
    }

    public enum Placement {
        BOTTOM
    }

    private final ContentType mContentType;
    private final OnClick mOnClick;
    private final Placement mPlacement;

    public ContentType getContentType() {
        return mContentType;
    }

    public OnClick getOnClick() {
        return mOnClick;
    }

    public Placement getPlacement() {
        return mPlacement;
    }

    @Override
    public String toString() {
        return String.format(
                "BannerOptions { ContentType=%s, OnClick=%s, Placement=%s }",
                mContentType, mOnClick, mPlacement);
    }

    private BannerOptions(ContentType contentType, OnClick onClick, Placement placement) {
        mContentType = contentType;
        mOnClick = onClick;
        mPlacement = placement;
    }

    public static BannerOptions fromSharedPreferences(SharedPreferences sharedPreferences) {
        return new BannerOptions(
                ContentType.valueOf(sharedPreferences.getString("banner_content_type", "")),
                OnClick.valueOf(sharedPreferences.getString("banner_on_click", "")),
                Placement.valueOf(sharedPreferences.getString("banner_placement", "")));
    }
}
