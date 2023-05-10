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

    public enum ViewType {
        RANDOM_COLOUR,
        INFLATED,
        VIDEO
    }

    public enum OnClick {
        OPEN_CHROME
    }

    public enum Placement {
        BOTTOM
    }

    private final ViewType mViewType;
    private final String mVideoUrl;
    private final OnClick mOnClick;
    private final Placement mPlacement;

    public ViewType getViewType() {
        return mViewType;
    }

    public OnClick getOnClick() {
        return mOnClick;
    }

    public Placement getPlacement() {
        return mPlacement;
    }

    public String getVideoUrl() {
        return mVideoUrl;
    }

    @Override
    public String toString() {
        return String.format(
                "BannerOptions { ViewType=%s, VideoUrl=%s, OnClick=%s, Placement=%s }",
                mViewType, mVideoUrl, mOnClick, mPlacement);
    }

    private BannerOptions(
            ViewType viewType, String videoUrl, OnClick onClick, Placement placement) {
        mViewType = viewType;
        mVideoUrl = videoUrl;
        mOnClick = onClick;
        mPlacement = placement;
    }

    public static BannerOptions fromSharedPreferences(SharedPreferences sharedPreferences) {
        return new BannerOptions(
                ViewType.valueOf(sharedPreferences.getString("banner_view_type", "")),
                sharedPreferences.getString("banner_video_url", ""),
                OnClick.valueOf(sharedPreferences.getString("banner_on_click", "")),
                Placement.valueOf(sharedPreferences.getString("banner_placement", "")));
    }
}
