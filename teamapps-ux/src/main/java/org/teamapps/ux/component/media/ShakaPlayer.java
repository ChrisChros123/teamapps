package org.teamapps.ux.component.media;

import org.teamapps.common.format.Color;
import org.teamapps.dto.UiComponent;
import org.teamapps.dto.UiEvent;
import org.teamapps.dto.UiShakaPlayer;
import org.teamapps.event.Event;
import org.teamapps.ux.component.AbstractComponent;
import org.teamapps.ux.session.SessionContext;

public class ShakaPlayer extends AbstractComponent {

	public final Event<Void> onErrorLoading = new Event<>();
	public final Event<Long> onTimeUpdate = new Event<>();
	public final Event<Void> onEnded = new Event<>();

	public static void setDistinctManifestAudioTracksFixEnabled(boolean enabled) {
		SessionContext.current().queueCommand(new UiShakaPlayer.SetDistinctManifestAudioTracksFixEnabledCommand(enabled));
	}

	private String hlsUrl;
	private String dashUrl;
	private String posterImageUrl;
	private PosterImageSize posterImageSize = PosterImageSize.COVER;
	private int timeUpdateEventThrottleMillis = 1000;
	private Color backgroundColor = Color.BLACK;
	private TrackLabelFormat trackLabelFormat = TrackLabelFormat.LABEL;
	private boolean videoDisabled = false;

	private long timeMillis = 0;

	public ShakaPlayer() {
	}

	public ShakaPlayer(String hlsUrl, String dashUrl) {
		this.hlsUrl = hlsUrl;
		this.dashUrl = dashUrl;
	}

	@Override
	public UiComponent createUiComponent() {
		UiShakaPlayer ui = new UiShakaPlayer();
		mapAbstractUiComponentProperties(ui);
		ui.setHlsUrl(hlsUrl);
		ui.setDashUrl(dashUrl);
		ui.setPosterImageUrl(posterImageUrl);
		ui.setPosterImageSize(posterImageSize.toUiPosterImageSize());
		ui.setTimeUpdateEventThrottleMillis(timeUpdateEventThrottleMillis);
		ui.setBackgroundColor(backgroundColor != null ? backgroundColor.toHtmlColorString(): null);
		ui.setTrackLabelFormat(trackLabelFormat.toUiTrackLabelFormat());
		ui.setVideoDisabled(videoDisabled);
		ui.setTimeMillis(timeMillis);
		return ui;
	}

	@Override
	public void handleUiEvent(UiEvent event) {
		switch (event.getUiEventType()) {
			case UI_SHAKA_PLAYER_ERROR_LOADING: {
				onErrorLoading.fire(null);
				break;
			}
			case UI_SHAKA_PLAYER_TIME_UPDATE: {
				UiShakaPlayer.TimeUpdateEvent e = (UiShakaPlayer.TimeUpdateEvent) event;
				onTimeUpdate.fire(e.getTimeMillis());
				this.timeMillis = e.getTimeMillis();
				break;
			}
			case UI_SHAKA_PLAYER_ENDED: {
				onEnded.fire();
				break;
			}
		}
	}

	public void setTime(long timeMillis) {
		this.timeMillis = timeMillis;
		queueCommandIfRendered(() -> new UiShakaPlayer.SetTimeCommand(getId(), timeMillis));
	}

	public long getTime() {
		return timeMillis;
	}

	public void setUrls(String hlsUrl, String dashUrl) {
		this.timeMillis = 0;
		this.hlsUrl = hlsUrl;
		this.dashUrl = dashUrl;
		queueCommandIfRendered(() -> new UiShakaPlayer.SetUrlsCommand(getId(), hlsUrl, dashUrl));
	}

	public String getHlsUrl() {
		return hlsUrl;
	}

	public void setHlsUrl(String hlsUrl) {
		setUrls(hlsUrl, dashUrl);
	}

	public String getDashUrl() {
		return dashUrl;
	}

	public void setDashUrl(String dashUrl) {
		setUrls(hlsUrl, dashUrl);
	}

	public String getPosterImageUrl() {
		return posterImageUrl;
	}

	public void setPosterImageUrl(String posterImageUrl) {
		this.posterImageUrl = posterImageUrl;
		reRenderIfRendered();
	}

	public PosterImageSize getPosterImageSize() {
		return posterImageSize;
	}

	public void setPosterImageSize(PosterImageSize posterImageSize) {
		this.posterImageSize = posterImageSize;
		reRenderIfRendered();
	}

	public int getTimeUpdateEventThrottleMillis() {
		return timeUpdateEventThrottleMillis;
	}

	public void setTimeUpdateEventThrottleMillis(int timeUpdateEventThrottleMillis) {
		this.timeUpdateEventThrottleMillis = timeUpdateEventThrottleMillis;
		reRenderIfRendered();
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		this.backgroundColor = backgroundColor;
		reRenderIfRendered();
	}

	public TrackLabelFormat getTrackLabelFormat() {
		return trackLabelFormat;
	}

	public void setTrackLabelFormat(TrackLabelFormat trackLabelFormat) {
		this.trackLabelFormat = trackLabelFormat;
		reRenderIfRendered();
	}

	public boolean isVideoDisabled() {
		return videoDisabled;
	}

	public void setVideoDisabled(boolean videoDisabled) {
		this.videoDisabled = videoDisabled;
		reRenderIfRendered();
	}
}
