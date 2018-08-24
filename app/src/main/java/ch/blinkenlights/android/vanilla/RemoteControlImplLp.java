/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

@TargetApi(21)
public class RemoteControlImplLp implements RemoteControl.Client {
	/**
	 * Context of this instance
	 */
	private final Context mContext;
	/**
	 * Objects MediaSession handle
	 */
	private MediaSessionCompat mMediaSession;
	/**
	 * Whether the cover should be shown. 1 for yes, 0 for no, -1 for
	 * uninitialized.
	 */
	private int mShowCover = -1;

	private int fakeRepeatMode = 0;
	private int lastInt = 0;
	private int numCommands = 0;
	private String lastCommand = "";

	/**
	 * Creates a new instance
	 *
	 * @param context The context to use
	 */
	public RemoteControlImplLp(Context context) {
		mContext = context;
	}

	/**
	 * Registers a new MediaSession on the device
	 */
	public void initializeRemote() {
		// make sure there is only one registered remote
		unregisterRemote();
		if (MediaButtonReceiver.useHeadsetControls(mContext) == false)
			return;

		mMediaSession = new MediaSessionCompat(mContext, "Vanilla Music");

		mMediaSession.setCallback(new MediaSessionCompat.Callback() {
			void Output()
			{
				MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
					.putString(MediaMetadata.METADATA_KEY_TITLE, lastCommand.substring(0, 10));
				if (lastCommand.length()>10)
					metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, lastCommand.substring(10, 20));
				if (lastCommand.length()>20)
					metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, lastCommand.substring(20));

				// logic copied from FullPlaybackActivity.updateQueuePosition()
				metadataBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, lastCommand.length());
				metadataBuilder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, numCommands);

				mMediaSession.setMetadata(metadataBuilder.build());
			}
			@Override
			public void onPause() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
			}
			@Override
			public void onPlay() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
			}
			@Override
			public void onSkipToNext() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
			}
			@Override
			public void onSkipToPrevious() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
			}
			@Override
			public void onStop() {
				// We will behave the same as Google Play Music: for "Stop" we unconditionally Pause instead
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
			}
			@Override
			public void onCommand(String command, Bundle extras, ResultReceiver cb) {
				numCommands++;
				lastCommand = ":"+command;
				Output();
			}
			@Override
			public boolean onMediaButtonEvent(Intent mediaButtonEvent){
				numCommands++;
				lastCommand = "m:"+mediaButtonEvent.getAction();
				KeyEvent keyEvent = (KeyEvent)mediaButtonEvent.getParcelableExtra("android.intent.extra.KEY_EVENT");
				Output();
				return super.onMediaButtonEvent(mediaButtonEvent);
			}

										  /**
                                           * Override to handle the setting of the repeat mode.
                                           * <p>
                                           * You should call {@link #setRepeatMode} before end of this method in order to notify
                                           * the change to the {@link MediaControllerCompat}, or
                                           * {@link MediaControllerCompat#getRepeatMode} could return an invalid value.
                                           *
                                           * @param repeatMode The repeat mode which is one of followings:
                                           *            {@link PlaybackStateCompat#REPEAT_MODE_NONE},
                                           *            {@link PlaybackStateCompat#REPEAT_MODE_ONE},
                                           *            {@link PlaybackStateCompat#REPEAT_MODE_ALL},
                                           *            {@link PlaybackStateCompat#REPEAT_MODE_GROUP}
                                           */
			@Override
			public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
				lastInt = repeatMode;
				fakeRepeatMode = repeatMode;

				mMediaSession.setRepeatMode(fakeRepeatMode);

				MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

				// logic copied from FullPlaybackActivity.updateQueuePosition()
					metadataBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, 100+lastInt);
					metadataBuilder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, 100+lastInt);

				mMediaSession.setMetadata(metadataBuilder.build());

				lastCommand = "onSetRepeatMode";
				Output();
			}
			@Override
			public void onSetShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
				numCommands++;
				lastCommand = "onSetShuffleMode";
				Output();
			}
			public void onCustomAction(String action, Bundle extras) {
				numCommands++;
				lastCommand = action;
				Output();
			}
		});

		Intent intent = new Intent();
		intent.setComponent(new ComponentName(mContext.getPackageName(), MediaButtonReceiver.class.getName()));
		PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
		// This Seems to overwrite our MEDIA_BUTTON intent filter and there seems to be no way to unregister it
		// Well: We intent to keep this around as long as possible anyway. But WHY ANDROID?!
		mMediaSession.setMediaButtonReceiver(pendingIntent);
		mMediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
	}

	/**
	 * Unregisters a registered media session
	 */
	public void unregisterRemote() {
		if (mMediaSession != null) {
			mMediaSession.setActive(false);
			mMediaSession.release();
			mMediaSession = null;
		}
	}

	/**
	 * Uninitializes our cached preferences, forcing a reload
	 */
	public void reloadPreference() {
		mShowCover = -1;
	}

	/**
	 * Update the remote with new metadata.
	 * {@link #initializeRemote()} must have been called
	 * first.
	 *
	 * @param song The song containing the new metadata.
	 * @param state PlaybackService state, used to determine playback state.
	 * @param keepPaused whether or not to keep the remote updated in paused mode
	 */
	public void updateRemote(Song song, int state, boolean keepPaused) {
		MediaSessionCompat session = mMediaSession;
		if (session == null)
			return;

		boolean isPlaying = ((state & PlaybackService.FLAG_PLAYING) != 0);

		if (mShowCover == -1) {
			SharedPreferences settings = PlaybackService.getSettings(mContext);
			mShowCover = settings.getBoolean(PrefKeys.COVER_ON_LOCKSCREEN, PrefDefaults.COVER_ON_LOCKSCREEN) ? 1 : 0;
		}

		PlaybackService service = PlaybackService.get(mContext);

		if (song != null) {
			Bitmap bitmap = null;
			if (mShowCover == 1 && (isPlaying || keepPaused)) {
				bitmap = song.getCover(mContext);
			}

			MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
				.putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
				.putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
				.putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
				.putLong(MediaMetadata.METADATA_KEY_DURATION, song.duration)
				.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);

			// logic copied from FullPlaybackActivity.updateQueuePosition()
			if (PlaybackService.finishAction(service.getState()) != SongTimeline.FINISH_RANDOM) {
				metadataBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, 100+lastInt);
				metadataBuilder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, 100+lastInt);
			}

			session.setMetadata(metadataBuilder.build());
		}

		int playbackState = (isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);

		session.setPlaybackState(new PlaybackStateCompat.Builder()
			.setState(playbackState, service.getPosition(), 1.0f)
			.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
				PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE | PlaybackStateCompat.ACTION_SET_REPEAT_MODE)
			.build());

		//session.setRepeatMode(service.getTimelinePosition() % 2 == 0 ? PlaybackStateCompat.REPEAT_MODE_ALL : PlaybackStateCompat.REPEAT_MODE_NONE);

		mMediaSession.setActive(true);
	}
}
