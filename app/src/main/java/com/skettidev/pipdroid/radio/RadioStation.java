package com.skettidev.pipdroid.radio;

public class RadioStation {

    private final RadioStationId id;       // Unique enum ID
    private final FalloutGame game;        // Fallout game
    private final String title;            // Full display title
    private final String streamUrl;        // Audio stream URL
    private final int nowPlayingId;        // ID used to fetch "Now Playing" from API
    private final String imageUrl;         // Image URL for the station
    private final boolean enabled;         // Enabled/disabled

    private static final String NOW_PLAYING_BASE_URL = "https://stations.fallout.radio/api/nowplaying/";

    public RadioStation(RadioStationId id, FalloutGame game, String title,
                        String streamUrl, int nowPlayingId,
                        String imageUrl, boolean enabled) {
        this.id = id;
        this.game = game;
        this.title = title;
        this.streamUrl = streamUrl;
        this.nowPlayingId = nowPlayingId;
        this.imageUrl = imageUrl;
        this.enabled = enabled;
    }

    // Getters
    public RadioStationId getId() {
        return id;
    }

    public FalloutGame getGame() {
        return game;
    }

    public String getTitle() {
        return title;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNowPlayingUrl() {
        return NOW_PLAYING_BASE_URL + nowPlayingId;
    }
}
