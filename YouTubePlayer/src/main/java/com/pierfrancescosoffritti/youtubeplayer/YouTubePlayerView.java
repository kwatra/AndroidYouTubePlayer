package com.pierfrancescosoffritti.youtubeplayer;

import android.content.Context;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.util.HashSet;
import java.util.Set;

public class YouTubePlayerView extends FrameLayout implements NetworkReceiver.NetworkListener {
    private static final String TAG = "NKSG:YouTubePlayerView";

    @NonNull private final NetworkReceiver networkReceiver;

    private YouTubePlayer youTubePlayer;

    private final boolean initControls = false;
    private View playerControls;
    private PlayerControlsWrapper playerControlsWrapper;

    @NonNull private final PlaybackResumer playbackResumer;

    private final Set<YouTubePlayerFullScreenListener> fullScreenListeners;

    private boolean isFullScreen;

    public YouTubePlayerView(Context context) {
        this(context, null);
    }

    public YouTubePlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YouTubePlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        isFullScreen = false;
        if (initControls) {
            playerControls = inflate(context, R.layout.player_controls, this);
            playerControlsWrapper = new PlayerControlsWrapper(this, playerControls);
        }

        playbackResumer = new PlaybackResumer(this);
        //youTubePlayer.addListener(playbackResumer);

        fullScreenListeners = new HashSet<>();
        if (playerControlsWrapper != null) {
            fullScreenListeners.add(playerControlsWrapper);
            youTubePlayer.addListener(playerControlsWrapper);
        }

        networkReceiver = new NetworkReceiver(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // if wrap content make the view 16:9
        if(getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            int sixteenNineHeight = View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec) * 9 / 16, View.MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, sixteenNineHeight);
        } else
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Set a custom behaviour to the full screen button.
     */
    public void onFullScreenButtonListener(OnClickListener listener) {
        if (playerControlsWrapper != null) {
            playerControlsWrapper.setOnFullScreenButtonListener(listener);
        }
    }

    public boolean isFullScreen() {
        return isFullScreen;
    }

    public void enterFullScreen() {
        if(isFullScreen)
            return;

        ViewGroup.LayoutParams viewParams = getLayoutParams();
        viewParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        viewParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        setLayoutParams(viewParams);

        isFullScreen = true;

        for(YouTubePlayerFullScreenListener fullScreenListener : fullScreenListeners)
            fullScreenListener.onYouTubePlayerEnterFullScreen();
    }

    public void exitFullScreen() {
        if(!isFullScreen)
            return;

        ViewGroup.LayoutParams viewParams = getLayoutParams();
        viewParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        setLayoutParams(viewParams);

        isFullScreen = false;

        for(YouTubePlayerFullScreenListener fullScreenListener : fullScreenListeners)
            fullScreenListener.onYouTubePlayerExitFullScreen();
    }

    public void toggleFullScreen() {
        if(isFullScreen)
            exitFullScreen();
        else
            enterFullScreen();
    }

    public boolean addFullScreenListener(@NonNull YouTubePlayerFullScreenListener fullScreenListener) {
        return fullScreenListeners.add(fullScreenListener);
    }

    public boolean removeFullScreenListener(@NonNull YouTubePlayerFullScreenListener fullScreenListener) {
        return fullScreenListeners.remove(fullScreenListener);
    }

    public void addYouTubeListener(YouTubePlayer.YouTubeListener listener) {
        youTubePlayer.addListener(listener);
    }

    // calls to YouTubePlayer

    private boolean initialized = false;
    private Callable onNetworkAvailableCallback;

    public void bind(@Nullable final YouTubePlayer.YouTubeListener youTubeListener) {
        if (youTubePlayer == null) {
            initializePlayer(youTubeListener, true  /*handleNetworkEvents*/);
        } else {
            youTubePlayer.clearListeners();
            youTubePlayer.addListener(youTubeListener);
        }
    }

    public void unbind() {
        // TODO: NKSG: Release into a pool.
        if (youTubePlayer == null) {
            Log.e(TAG, "unbind: youTubePlayer null");
        }
        removeView(youTubePlayer);
        youTubePlayer = null;
        initialized = false;
    }

    /**
     * Initialize the player
     * @param youTubeListener lister for player events
     * @param handleNetworkEvents if <b>true</b> a broadcast receiver will be registered.<br/>If <b>false</b> you should handle network events with your broadcast receiver. See {@link YouTubePlayerView#onNetworkAvailable()} and {@link YouTubePlayerView#onNetworkUnavailable()}
     */
    private void initializePlayer(@Nullable final YouTubePlayer.YouTubeListener youTubeListener, boolean handleNetworkEvents) {
        Log.v(TAG, "Initializing youtube player");
        youTubePlayer = new YouTubePlayer(getContext());  // TODO: NKSG: Get from a pool.
        addView(youTubePlayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if(handleNetworkEvents)
            getContext().registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        if(!Utils.isOnline(getContext())) {
            Log.e(TAG, "Can't initialize because device is not connected to the internet.");

            onNetworkAvailableCallback = new Callable() {
                @Override
                public void call() {
                    Log.d(TAG, "Network available. Initializing player.");
                    youTubePlayer.initialize(youTubeListener);
                    initialized = true;

                    onNetworkAvailableCallback = null;
                }
            };

            return;
        }

        youTubePlayer.initialize(youTubeListener);
        initialized = true;
    }

    /**
     * See {@link YouTubePlayer#loadVideo(String, float)}
     */
    public void loadVideo(String videoId, float startSecond) {
        if(!initialized) {
            Log.e(TAG, "loadVideo: the player has not been initialized");
            return;
        }

        youTubePlayer.loadVideo(videoId, startSecond);
        if (playerControlsWrapper != null) {
            playerControlsWrapper.onNewVideo();
        }
    }

    /**
     * See {@link YouTubePlayer#cueVideo(String, float)}
     */
    public void cueVideo(String videoId, float startSeconds) {
        if(!initialized) {
            Log.e(TAG, "cueVideo: the player has not been initialized");
            return;
        }

        youTubePlayer.cueVideo(videoId, startSeconds);
        if (playerControlsWrapper != null) {
            playerControlsWrapper.onNewVideo();
        }
    }

    /**
     * Calls {@link WebView#destroy()} on the player. And unregisters the broadcast receiver (for network events), if registered.
     * Call this method before destroying the host Fragment/Activity
     */
    public void release() {
        if(!initialized) {
            Log.e(TAG, "release: the player has not been initialized");
            return;
        }

        youTubePlayer.destroy();
        try {
            getContext().unregisterReceiver(networkReceiver);
        } catch (Exception ignore) {
        }
    }

    /**
     * See {@link YouTubePlayer#seekTo(int)}
     */
    public void seekTo(int time) {
        if(!initialized) {
            Log.e(TAG, "seekTo: the player has not been initialized");
            return;
        }

        youTubePlayer.seekTo(time);
    }

    /**
     * See {@link YouTubePlayer#play()}
     */
    public void playVideo() {
        if(!initialized) {
            Log.e(TAG, "playVideo: the player has not been initialized");
            return;
        }

        youTubePlayer.play();
    }

    /**
     * See {@link YouTubePlayer#pause()}
     */
    public void pauseVideo() {
        if(!initialized) {
            Log.e(TAG, "pauseVideo: the player has not been initialized");
            return;
        }

        youTubePlayer.pause();
    }

    public void mute() {
        if(!initialized) {
            Log.e(TAG, "mute: the player has not been initialized");
            return;
        }

        youTubePlayer.mute();
    }

    public void unMute() {
        if(!initialized) {
            Log.e(TAG, "unMute: the player has not been initialized");
            return;
        }

        youTubePlayer.unMute();
    }

    @Override
    public void onNetworkAvailable() {
        Log.d(TAG, "Network available.");
        if(!initialized && onNetworkAvailableCallback != null)
            onNetworkAvailableCallback.call();
        else
            playbackResumer.resume();
    }

    @Override
    public void onNetworkUnavailable() {

    }

    public void showTitle(boolean show) {
        if (playerControlsWrapper != null) {
            playerControlsWrapper.showTitle(show);
        }
    }

    public void setCustomActionRight(Drawable icon, View.OnClickListener clickListener) {
        if (playerControlsWrapper != null) {
            playerControlsWrapper.setCustomActionRight(icon, clickListener);
        }
    }

    public void setCustomActionLeft(Drawable icon, View.OnClickListener clickListener) {
        if (playerControlsWrapper != null) {
            playerControlsWrapper.setCustomActionLeft(icon, clickListener);
        }
    }

    public void showFullScreenButton(boolean show) {
        if (playerControlsWrapper != null) {
            playerControlsWrapper.showFullscreenButton(show);
        }
    }

    public void hideUI(boolean hide) {
        if (playerControlsWrapper != null) {
            playerControlsWrapper.hideUI(hide);
        }
    }
}
