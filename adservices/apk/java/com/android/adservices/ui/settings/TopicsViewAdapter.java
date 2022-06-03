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
package com.android.adservices.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * ViewAdapter to handle data binding for the list of {@link Topic}s on {@link
 * AdServicesSettingsTopicsFragment}.
 */
public class TopicsViewAdapter extends RecyclerView.Adapter {
    private final LiveData<ImmutableList<Topic>> mTopics;

    /** ViewHolder to display the text for a topic item */
    public static class TopicsViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTopicTextView;

        public TopicsViewHolder(final View itemView) {
            super(itemView);
            mTopicTextView = itemView.findViewById(R.id.topic_text);
        }

        /** set the human readable string for the topic */
        public void setTopic(Topic topic) {
            // TODO(b/234655984): show readable string of topic
            mTopicTextView.setText(Integer.toString(topic.getTopic()));
        }
    }

    public TopicsViewAdapter(TopicsViewModel viewModel) {
        mTopics = viewModel.getTopics();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new TopicsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((TopicsViewHolder) holder)
                .setTopic(Objects.requireNonNull(mTopics.getValue()).get(position));
    }

    @Override
    public int getItemCount() {
        return Objects.requireNonNull(mTopics.getValue()).size();
    }

    @Override
    public int getItemViewType(final int position) {
        return R.layout.topic_item;
    }
}
