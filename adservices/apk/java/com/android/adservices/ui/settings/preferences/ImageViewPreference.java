/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.ui.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.adservices.api.R;
import com.android.settingslib.widget.GroupSectionDividerMixin;

/** Simple class to hold image in preferences. */
public class ImageViewPreference extends Preference implements GroupSectionDividerMixin {
    Context mContext;

    public ImageViewPreference(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
    }

    public ImageViewPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    public ImageViewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public ImageViewPreference(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View imageView = holder.findViewById(R.id.main_view_image);
        imageView.setContentDescription(
                mContext.getString(R.string.ic_main_view_ga_image_description));
    }
}
