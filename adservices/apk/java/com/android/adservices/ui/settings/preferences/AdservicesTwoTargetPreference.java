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
import android.widget.Button;

import androidx.preference.PreferenceViewHolder;

import com.android.adservices.api.R;
import com.android.settingslib.widget.TwoTargetPreference;

public class AdservicesTwoTargetPreference extends TwoTargetPreference {
    private View.OnClickListener mOnClickListener;
    private String mButtonString;

    public AdservicesTwoTargetPreference(
            Context context,
            AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes,
            String buttonString) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mButtonString = buttonString;
    }

    public AdservicesTwoTargetPreference(
            Context context, AttributeSet attrs, int defStyleAttr, String buttonString) {
        super(context, attrs, defStyleAttr);
        mButtonString = buttonString;
    }

    public AdservicesTwoTargetPreference(Context context, AttributeSet attrs, String buttonString) {
        super(context, attrs);
        mButtonString = buttonString;
    }

    public AdservicesTwoTargetPreference(Context context, String buttonString) {
        super(context);
        mButtonString = buttonString;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Button mOptionButtonView = (Button) holder.findViewById(R.id.option_button);
        if (mOptionButtonView != null) {
            mOptionButtonView.setOnClickListener(mOnClickListener);
            mOptionButtonView.setText(mButtonString);
            // Remove divider
            View divider =
                    holder.findViewById(
                            com.android.settingslib.widget.preference.twotarget.R.id
                                    .two_target_divider);
            if (divider != null) {
                divider.setVisibility(View.GONE);
            }
        }
    }

    public void setOnClickListener(View.OnClickListener onClickListener) {
        this.mOnClickListener = onClickListener;
    }

    @Override
    public int getSecondTargetResId() {
        return R.layout.adservices_item_button;
    }
}
