/**
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.radio.media;

import static com.android.car.radio.utils.Remote.exec;
import static com.android.car.radio.utils.Remote.tryExec;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.android.car.broadcastradio.support.Program;
import com.android.car.broadcastradio.support.media.BrowseTree;
import com.android.car.broadcastradio.support.platform.ImageResolver;
import com.android.car.broadcastradio.support.platform.ProgramInfoExt;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.R;
import com.android.car.radio.audio.PlaybackStateListenerAdapter;
import com.android.car.radio.service.CurrentProgramListenerAdapter;
import com.android.car.radio.service.IRadioAppService;

import java.util.Objects;

/**
 * Implementation of tuner's MediaSession.
 */
public class TunerSession extends MediaSessionCompat {
    private static final String TAG = "BcRadioApp.msess";

    private final Object mLock = new Object();

    private final Context mContext;
    private final BrowseTree mBrowseTree;
    @Nullable private final ImageResolver mImageResolver;
    private final IRadioAppService mAppService;
    private final PlaybackStateCompat.Builder mPlaybackStateBuilder =
            new PlaybackStateCompat.Builder();
    @Nullable private ProgramInfo mCurrentProgram;

    public TunerSession(@NonNull Context context, @NonNull BrowseTree browseTree,
            @NonNull IRadioAppService uiSession, @Nullable ImageResolver imageResolver) {
        super(context, TAG);

        mContext = Objects.requireNonNull(context);
        mBrowseTree = Objects.requireNonNull(browseTree);
        mImageResolver = imageResolver;
        mAppService = Objects.requireNonNull(uiSession);

        // ACTION_PAUSE is reserved for time-shifted playback
        mPlaybackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SET_RATING
                | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_URI);
        setRatingType(RatingCompat.RATING_HEART);
        onPlaybackStateChanged(PlaybackStateCompat.STATE_NONE);
        setCallback(new TunerSessionCallback());

        exec(() -> uiSession.addCurrentProgramListener(
                new CurrentProgramListenerAdapter(this::onCurrentProgramChanged)));
        exec(() -> uiSession.addPlaybackStateListener(
                new PlaybackStateListenerAdapter(this::onPlaybackStateChanged)));

        setActive(true);
    }

    private void updateMetadata() {
        synchronized (mLock) {
            if (mCurrentProgram == null) return;
            boolean fav = mBrowseTree.isFavorite(mCurrentProgram.getSelector());
            setMetadata(MediaMetadataCompat.fromMediaMetadata(
                    ProgramInfoExt.toMediaMetadata(mCurrentProgram, fav, mImageResolver)));
        }
    }

    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        synchronized (mLock) {
            mCurrentProgram = info;
            updateMetadata();
        }
    }

    private void onPlaybackStateChanged(@PlaybackStateCompat.State int state) {
        synchronized (mPlaybackStateBuilder) {
            mPlaybackStateBuilder.setState(state,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            setPlaybackState(mPlaybackStateBuilder.build());
        }
    }

    public void notifyFavoritesChanged() {
        updateMetadata();
    }

    private void selectionError() {
        tryExec(() -> mAppService.mute());
        mPlaybackStateBuilder.setErrorMessage(mContext.getString(R.string.invalid_selection));
        onPlaybackStateChanged(PlaybackStateCompat.STATE_ERROR);
        mPlaybackStateBuilder.setErrorMessage(null);
    }

    private class TunerSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onStop() {
            tryExec(() -> mAppService.mute());
        }

        @Override
        public void onPlay() {
            tryExec(() -> mAppService.unMute());
        }

        @Override
        public void onSkipToNext() {
            tryExec(() -> mAppService.seekForward());
        }

        @Override
        public void onSkipToPrevious() {
            tryExec(() -> mAppService.seekBackward());
        }

        @Override
        public void onSetRating(RatingCompat rating) {
            synchronized (mLock) {
                if (mCurrentProgram == null) return;
                if (rating.hasHeart()) {
                    Program fav = Program.fromProgramInfo(mCurrentProgram);
                    tryExec(() -> mAppService.addFavorite(fav));
                } else {
                    ProgramSelector fav = mCurrentProgram.getSelector();
                    tryExec(() -> mAppService.removeFavorite(fav));
                }
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            if (mBrowseTree.getRoot().getRootId().equals(mediaId)) {
                // general play command
                onPlay();
                return;
            }

            ProgramSelector selector = mBrowseTree.parseMediaId(mediaId);
            if (selector != null) {
                tryExec(() -> mAppService.tune(selector));
            } else {
                Log.w(TAG, "Invalid media ID: " + mediaId);
                selectionError();
            }
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            ProgramSelector selector = ProgramSelectorExt.fromUri(uri);
            if (selector != null) {
                tryExec(() -> mAppService.tune(selector));
            } else {
                Log.w(TAG, "Invalid URI: " + uri);
                selectionError();
            }
        }
    }
}
