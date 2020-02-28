/*-
 * ========================LICENSE_START=================================
 * TeamApps
 * ---
 * Copyright (C) 2014 - 2020 TeamApps.org
 * ---
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
 * =========================LICENSE_END==================================
 */
///<reference path="../custom-declarations/mediaelement.d.ts"/>

import "mediaelement/full";


import {AbstractUiComponent} from "./AbstractUiComponent";
import {TeamAppsEvent} from "./util/TeamAppsEvent";
import {TeamAppsUiContext} from "./TeamAppsUiContext";
import {
	UiVideoPlayer_ErrorLoadingEvent,
	UiVideoPlayer_PlayerProgressEvent,
	UiVideoPlayer_PosterImageSize,
	UiVideoPlayerCommandHandler,
	UiVideoPlayerConfig,
	UiVideoPlayerEventSource
} from "../generated/UiVideoPlayerConfig";
import {TeamAppsUiComponentRegistry} from "./TeamAppsUiComponentRegistry";
import {createUiColorCssString} from "./util/CssFormatUtil";
import {UiMediaPreloadMode} from "../generated/UiMediaPreloadMode";
import {parseHtml} from "./Common";

export class UiVideoPlayer extends AbstractUiComponent<UiVideoPlayerConfig> implements UiVideoPlayerCommandHandler, UiVideoPlayerEventSource {

	public readonly onPlayerProgress: TeamAppsEvent<UiVideoPlayer_PlayerProgressEvent> = new TeamAppsEvent<UiVideoPlayer_PlayerProgressEvent>(this);
	public readonly onErrorLoading: TeamAppsEvent<UiVideoPlayer_ErrorLoadingEvent> = new TeamAppsEvent<UiVideoPlayer_ErrorLoadingEvent>(this);

	private $componentWrapper: HTMLElement;
	private $video: HTMLElement;
	private mediaPlayer: any;
	private contentReady: boolean = false;
	private jumpToPositionWhenReady: number = 0;

	private destroyed: boolean;
	private autoplay: boolean;
	private playState: "initial" | "playing" | "paused" = "initial";

	constructor(config: UiVideoPlayerConfig, context: TeamAppsUiContext) {
		super(config, context);

		const posterImageSizeCssClass = `poster-${UiVideoPlayer_PosterImageSize[config.posterImageSize].toLowerCase()}`;
		this.$componentWrapper = parseHtml(
			`<div id="${config.id}" class="UiVideoPlayer ${posterImageSizeCssClass} ${config.url == null ? "not-playable" : ""}">
                    <video src="${config.url || ""}" width="100%" height="100%" poster="${config.posterImageUrl || ''}" preload="${config.preloadMode === UiMediaPreloadMode.AUTO ? 'auto' : config.preloadMode === UiMediaPreloadMode.METADATA ? 'metadata' : 'none'}" ${config.autoplay ? "autoplay" : ""}></video>
                </div>`);
		this.$componentWrapper.classList.toggle("hide-controls", !config.showControls);
		this.$video = this.$componentWrapper.querySelector<HTMLElement>(":scope video");

		// TODO this is the point where the element was inserted to the DOM

		this.mediaPlayer = new mejs.MediaElementPlayer(this.$componentWrapper.querySelector<HTMLElement>(':scope video'), {
			enablePluginDebug: false,
			plugins: ['fasterslower'],
			type: '',
			silverlightName: 'silverlightmediaelement.xap',
			features: ['playpause', 'current', 'progress', 'duration', 'tracks', 'volume', 'fullscreen'],
			timerRate: 250,
			success: (mediaElement: HTMLMediaElement) => {
				this.onContentReady();

				mediaElement.addEventListener('play', (e) => {
					this.onPlayerProgress.fire({
						positionInSeconds: 0
					});
				}, false);

				let lastPlayTime = 0;
				mediaElement.addEventListener('timeupdate', (e) => {
					let currentPlayTime = mediaElement.currentTime;
					if (lastPlayTime < currentPlayTime && lastPlayTime % config.sendPlayerProgressEventsEachXSeconds > currentPlayTime % config.sendPlayerProgressEventsEachXSeconds) {
						this.onPlayerProgress.fire({
							positionInSeconds: Math.floor(currentPlayTime)
						});
					}
					lastPlayTime = currentPlayTime;
				}, false);
			},
			error: () => {
				if (!this.destroyed) {
					this.onErrorLoading.fire({})
				}
			}
		});

		this.$componentWrapper.style.backgroundColor = createUiColorCssString(config.backgroundColor);
		this.$componentWrapper.querySelector<HTMLElement>(':scope .mejs__container').style.backgroundColor = createUiColorCssString(config.backgroundColor);

		this.setPreloadMode(config.preloadMode);
		this.setAutoplay(config.autoplay);

		this.displayedDeferredExecutor.invokeWhenReady(() => {
			if (this.autoplay) {
				this.play();
			}
		});
	}

	public doGetMainElement(): HTMLElement {
		return this.$componentWrapper;
	}

	private onContentReady() {
		this.contentReady = true;
		if (this.playState === "playing" || (this.autoplay && this.playState !== "paused")) {
			this.play();
		}
	}

	public play() {
		this.playState = "playing";
		if (this.contentReady) {
			this.mediaPlayer.play();
		}
	}

	public pause() {
		this.mediaPlayer.pause();
		this.playState = "paused";
	}

	public jumpTo(seconds: number) {
		if (this.contentReady) {
			this.mediaPlayer.setCurrentTime(seconds);
		} else {
			this.jumpToPositionWhenReady = seconds;
		}
	}

	public onResize(): void {
		// console.log(this.getWidth(), this.getHeight(), Math.min(this.getHeight(), this.mediaPlayer.height), this.mediaPlayer.width, this.mediaPlayer.height)
		// this.mediaPlayer.setPlayerSize(this.getWidth(), Math.min(this.getHeight(), this.mediaPlayer.height)); // CAUTION: maybe we will have to handle fullscreen mode
		this.mediaPlayer.setPlayerSize(this.mediaPlayer.width, this.mediaPlayer.height); // CAUTION: maybe we will have to handle fullscreen mode
		this.mediaPlayer.setControlsSize();
	}

	public destroy(): void {
		super.destroy();
		this.destroyed = true;
		try {
			this.mediaPlayer.pause();
			this.mediaPlayer.setSrc("");
			this.mediaPlayer.load();
		} catch (e) {
			this.logger.error("Error while destroying video player: " + e.toString());
		}
	}

	setAutoplay(autoplay: boolean): void {
		this.autoplay = autoplay;
		this.playState = "initial";

		if (autoplay) {
			if (this.contentReady) {
				this.mediaPlayer.play();
			}
			this.$video.setAttribute("autoplay", "autoplay")
		} else {
			this.$video.removeAttribute("autoplay");
		}
	}

	setPreloadMode(preloadMode: UiMediaPreloadMode): void {
		this.$video.setAttribute("preload", `${preloadMode === UiMediaPreloadMode.AUTO ? 'auto' : preloadMode === UiMediaPreloadMode.METADATA ? 'metadata' : 'none'}`);
	}

	setUrl(url: string): void {
		this.getMainElement().querySelector<HTMLElement>(":scope .mejs__overlay-error").parentElement.classList.add("hidden");
		this.getMainElement().querySelector<HTMLElement>(":scope .mejs__poster").classList.remove("hidden");
		this.getMainElement().querySelector<HTMLElement>(":scope .mejs__overlay-play").style.display = "flex";
		this.mediaPlayer.pause();
		this.contentReady = false;
		if (url == null) {
			this.pause();
		} else {
			this.mediaPlayer.setSrc(url);
		}
		this.$componentWrapper.classList.toggle("not-playable", url == null);
	}
}

TeamAppsUiComponentRegistry.registerComponentClass("UiVideoPlayer", UiVideoPlayer);
