/*-
 * ========================LICENSE_START=================================
 * TeamApps
 * ---
 * Copyright (C) 2014 - 2021 TeamApps.org
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
package org.teamapps.ux.session;

import com.ibm.icu.util.ULocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teamapps.common.format.Color;
import org.teamapps.common.format.RgbaColor;
import org.teamapps.dto.UiCommand;
import org.teamapps.dto.UiRootPanel;
import org.teamapps.dto.UiSessionClosingReason;
import org.teamapps.event.Event;
import org.teamapps.icons.Icon;
import org.teamapps.icons.SessionIconProvider;
import org.teamapps.server.UxServerContext;
import org.teamapps.uisession.*;
import org.teamapps.ux.component.ClientObject;
import org.teamapps.ux.component.animation.EntranceAnimation;
import org.teamapps.ux.component.animation.ExitAnimation;
import org.teamapps.ux.component.field.Button;
import org.teamapps.ux.component.field.DisplayField;
import org.teamapps.ux.component.flexcontainer.VerticalLayout;
import org.teamapps.ux.component.linkbutton.LinkButton;
import org.teamapps.ux.component.notification.Notification;
import org.teamapps.ux.component.notification.NotificationPosition;
import org.teamapps.ux.component.popup.Popup;
import org.teamapps.ux.component.rootpanel.RootPanel;
import org.teamapps.ux.component.rootpanel.WakeLock;
import org.teamapps.ux.component.template.Template;
import org.teamapps.ux.component.template.TemplateReference;
import org.teamapps.ux.component.window.Window;
import org.teamapps.ux.i18n.ResourceBundleTranslationProvider;
import org.teamapps.ux.i18n.TranslationProvider;
import org.teamapps.ux.icon.IconBundle;
import org.teamapps.ux.icon.TeamAppsIconBundle;
import org.teamapps.ux.json.UxJacksonSerializationTemplate;
import org.teamapps.ux.resource.Resource;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.teamapps.common.util.ExceptionUtil.softenExceptions;

public class SessionContext {

	private static final Logger LOGGER = LoggerFactory.getLogger(SessionContext.class);
	private static final String DEFAULT_BACKGROUND_NAME = "defaultBackground";
	private static final String DEFAULT_BACKGROUND_URL = "/resources/backgrounds/default-bl.jpg";

	private final ExecutorService sessionExecutor;

	public final Event<UiSessionActivityState> onActivityStateChanged = new Event<>();
	public final Event<Void> onDestroyed = new Event<>();
	/**
	 * Decorators around all executions inside this SessionContext. These will be invoked when the Thread is already bound to the SessionContext, so SessionContext.current() will
	 * return this instance.
	 */
	public final ExecutionDecoratorStack executionDecorators = new ExecutionDecoratorStack();

	private boolean active = true;
	private volatile boolean destroyed = false;

	private final QualifiedUiSessionId sessionId;
	private final ClientInfo clientInfo;
	private final HttpSession httpSession;
	private final UiCommandExecutor commandExecutor;
	private final UxServerContext serverContext;
	private final SessionIconProvider iconProvider;
	private final UxJacksonSerializationTemplate uxJacksonSerializationTemplate;
	private final HashMap<String, ClientObject> clientObjectsById = new HashMap<>();
	private final ClientSessionResourceProvider sessionResourceProvider;

	private TranslationProvider translationProvider;

	private final Map<String, Template> registeredTemplates = new ConcurrentHashMap<>();
	private SessionConfiguration sessionConfiguration;

	private final Map<String, Icon<?, ?>> bundleIconByKey = new HashMap<>();

	private boolean defaultBackgroundRegistered;

	private Window sessionExpiredWindow;
	private Window sessionErrorWindow;
	private Window sessionTerminatedWindow;


	public SessionContext(QualifiedUiSessionId sessionId,
						  ExecutorService sessionExecutor,
						  ClientInfo clientInfo,
						  SessionConfiguration sessionConfiguration,
						  HttpSession httpSession,
						  UiCommandExecutor commandExecutor,
						  UxServerContext serverContext,
						  SessionIconProvider iconProvider) {
		this.sessionExecutor = sessionExecutor;
		this.sessionId = sessionId;
		this.httpSession = httpSession;
		this.clientInfo = clientInfo;
		this.sessionConfiguration = sessionConfiguration;
		this.commandExecutor = commandExecutor;
		this.serverContext = serverContext;
		this.iconProvider = iconProvider;
		this.sessionResourceProvider = new ClientSessionResourceProvider(sessionId);
		this.uxJacksonSerializationTemplate = new UxJacksonSerializationTemplate(this);
		this.translationProvider = new ResourceBundleTranslationProvider("org.teamapps.ux.i18n.DefaultCaptions", Locale.ENGLISH);
		addIconBundle(TeamAppsIconBundle.createBundle());
		runWithContext(this::updateSessionMessageWindows);
	}

	public static SessionContext current() {
		return CurrentSessionContext.get();
	}

	public static SessionContext currentOrNull() {
		return CurrentSessionContext.getOrNull();
	}

	public void setTranslationProvider(TranslationProvider translationProvider) {
		this.translationProvider = translationProvider;
	}

	public TranslationProvider getTranslationProvider() {
		return translationProvider;
	}

	public void addIconBundle(IconBundle iconBundle) {
		iconBundle.getEntries().forEach(entry -> bundleIconByKey.put(entry.getKey(), entry.getIcon()));
	}

	public Icon<?, ?> getIcon(String key) {
		return bundleIconByKey.get(key);
	}

	public ULocale getULocale() {
		return sessionConfiguration.getULocale();
	}

	public Locale getLocale() {
		return sessionConfiguration.getLocale();
	}

	public void setLocale(Locale locale) {
		setULocale(ULocale.forLocale(locale));
	}

	public void setULocale(ULocale locale) {
		sessionConfiguration.setULocale(locale);
		setConfiguration(sessionConfiguration);
	}

	public String getLocalized(String key, Object... parameters) {
		String value = translationProvider.getTranslation(key, getLocale());
		if (value != null && parameters != null) {
			return MessageFormat.format(value, parameters);
		}
		return value;
	}

	public boolean isActive() {
		return active;
	}

	public void handleActivityStateChangedInternal(boolean active) {
		this.active = active;
		onActivityStateChanged.fire(new UiSessionActivityState(active));
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	public void destroy() {
		commandExecutor.closeSession(sessionId, UiSessionClosingReason.TERMINATED_BY_APPLICATION);
	}

	public void handleSessionDestroyedInternal() {
		onDestroyed.fireIgnoringExceptions(null);
		runWithContext(() -> { // Do in runWithContext() with forceEnqueue=true so all onDestroyed handlers have already been executed before disabling any more executions inside the context!
			destroyed = true;
			sessionExecutor.shutdown();
		}, true);
	}

	public Event<Void> onDestroyed() {
		return onDestroyed;
	}

	public <RESULT> void queueCommand(UiCommand<RESULT> command, Consumer<RESULT> resultCallback) {
		if (CurrentSessionContext.get() != this) {
			String errorMessage = "Trying to queue a command for foreign/null SessionContext (CurrentSessionContext.get() != this)."
					+ " Please use SessionContext.runWithContext(Runnable). NOTE: The command will not get queued!";
			LOGGER.error(errorMessage);
			throw new IllegalStateException(errorMessage);
		}
		Consumer<RESULT> wrappedCallback = resultCallback != null ? result -> this.runWithContext(() -> resultCallback.accept(result)) : null;

		uxJacksonSerializationTemplate.doWithUxJacksonSerializers(() -> commandExecutor.sendCommand(sessionId, new UiCommandWithResultCallback<>(command, wrappedCallback)));
	}

	public void queueCommand(UiCommand<?> command) {
		this.queueCommand(command, null);
	}

	public ClientInfo getClientInfo() {
		return clientInfo;
	}

	public HttpSession getHttpSession() {
		return httpSession;
	}

	/**
	 * @deprecated no more needed. commands are sent as early as the client can handle them.
	 */
	@Deprecated
	public void flushCommands() {
		// TODO remove methods
	}

	public ClientBackPressureInfo getClientBackPressureInfo() {
		return commandExecutor.getClientBackPressureInfo(sessionId);
	}

	public String createFileLink(File file) {
		return sessionResourceProvider.createFileLink(file);
	}

	public String createResourceLink(Resource resource, String uniqueIdentifier) {
		return sessionResourceProvider.createResourceLink(resource, uniqueIdentifier);
	}

	public Resource getBinaryResource(int resourceId) {
		return sessionResourceProvider.getBinaryResource(resourceId);
	}

	public File getUploadedFileByUuid(String uuid) {
		return this.serverContext.getUploadedFileByUuid(uuid);
	}

	public TemplateReference registerTemplate(String id, Template template) {
		registeredTemplates.put(id, template);
		queueCommand(new UiRootPanel.RegisterTemplateCommand(id, template.createUiTemplate()));
		return new TemplateReference(template, id);
	}

	public void registerTemplates(Map<String, Template> templates) {
		registeredTemplates.putAll(templates);
		queueCommand(new UiRootPanel.RegisterTemplatesCommand(templates.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().createUiTemplate()))));
	}

	public Template getTemplate(String id) {
		return registeredTemplates.get(id);
	}

	public CompletableFuture<Void> runWithContext(Runnable runnable) {
		return this.runWithContext(runnable, false);
	}

	/**
	 * @param runnable
	 * @param forceEnqueue No synchronous execution! Enqueue this at the end of this SessionContext's work queue.
	 */
	public CompletableFuture<Void> runWithContext(Runnable runnable, boolean forceEnqueue) {
		return runWithContext(() -> {
			runnable.run();
			return null;
		}, forceEnqueue);
	}

	public <R> CompletableFuture<R> runWithContext(Callable<R> runnable) {
		return runWithContext(runnable, false);
	}

	public <R> CompletableFuture<R> runWithContext(Callable<R> runnable, boolean forceEnqueue) {
		if (destroyed) {
			LOGGER.info("This SessionContext ({}) is already destroyed. Not sending command.", sessionId);
			return CompletableFuture.failedFuture(new IllegalStateException("SessionContext destroyed."));
		}
		if (CurrentSessionContext.getOrNull() == this && !forceEnqueue) {
			// Fast lane! This thread is already bound to this SessionContext. Just execute the runnable.
			try {
				return CompletableFuture.completedFuture(runnable.call());
			} catch (Exception e) {
				LOGGER.error("Exception while executing within session context (fast lane execution)", e);
				return CompletableFuture.failedFuture(e);
			}
		} else {
			return CompletableFuture.supplyAsync(() -> {
				CurrentSessionContext.set(this);
				try {
					Object[] resultHolder = new Object[1];
					executionDecorators
							.createWrappedRunnable(() -> resultHolder[0] = softenExceptions(runnable))
							.run();
					return ((R) resultHolder[0]);
				} finally {
					CurrentSessionContext.unset();
				}
			}, sessionExecutor);
		}
	}

	/**
	 * Adds a decorator that gets invoked whenever a Thread is bound to this SessionContext.
	 * The decorator will be called right <strong>after</strong> the Thread is bound to this SessionContext, so SessionContext.current() will return this instance.
	 *
	 * @param decorator
	 * @param outer     Whether to add this decorator as outermost or innermost execution wrapper.
	 */
	public void addExecutionDecorator(ExecutionDecorator decorator, boolean outer) {
		if (outer) {
			executionDecorators.addOuterDecorator(decorator);
		} else {
			executionDecorators.addInnerDecorator(decorator);
		}
	}

	public SessionConfiguration getConfiguration() {
		return sessionConfiguration;
	}

	public void setConfiguration(SessionConfiguration config) {
		this.sessionConfiguration = config;
		queueCommand(new UiRootPanel.SetConfigCommand(config.createUiConfiguration()));
		updateSessionMessageWindows();
	}

	public void showPopupAtCurrentMousePosition(Popup popup) {
		queueCommand(new UiRootPanel.ShowPopupAtCurrentMousePositionCommand(popup.createUiReference()));
	}

	public void showPopup(Popup popup) {
		queueCommand(new UiRootPanel.ShowPopupCommand(popup.createUiReference()));
	}

	public ZoneId getTimeZone() {
		return sessionConfiguration.getTimeZone();
	}

	public SessionIconProvider getIconProvider() {
		return iconProvider;
	}

	public <I extends Icon<I, S>, S> void setDefaultStyleForIconClass(Class<I> iconClass, S defaultStyle) {
		this.runWithContext(() -> {
			iconProvider.setDefaultStyleForIconClass(iconClass, defaultStyle);
		});
	}

	public String resolveIcon(Icon icon) {
		if (icon == null) {
			return null;
		}
		return sessionConfiguration.getIconPath() + "/" + iconProvider.encodeIcon(icon, true);
	}

	public void registerClientObject(ClientObject clientObject) {
		clientObjectsById.put(clientObject.getId(), clientObject);
	}

	public void unregisterClientObject(ClientObject clientObject) {
		clientObjectsById.remove(clientObject.getId());
	}

	public ClientObject getClientObject(String clientObjectId) {
		return clientObjectsById.get(clientObjectId);
	}

	public String createResourceLink(Resource resource) {
		return createResourceLink(resource, null);
	}

	public void showWindow(Window window, int animationDuration) {
		window.show(animationDuration);
	}

	public void downloadFile(String fileUrl, String downloadFileName) {
		runWithContext(() -> queueCommand(new UiRootPanel.DownloadFileCommand(fileUrl, downloadFileName)));
	}

	public void downloadFile(File file, String downloadFileName) {
		runWithContext(() -> queueCommand(new UiRootPanel.DownloadFileCommand(createFileLink(file), downloadFileName)));
	}

	public void registerBackgroundImage(String id, String image, String blurredImage) {
		queueCommand(new UiRootPanel.RegisterBackgroundImageCommand(id, image, blurredImage));
	}

	public void setBackgroundImage(String id, int animationDuration) {
		queueCommand(new UiRootPanel.SetBackgroundImageCommand(id, animationDuration));
	}

	public void showDefaultBackground(int animationDuration) {
		if (!defaultBackgroundRegistered) {
			defaultBackgroundRegistered = true;
			registerBackgroundImage(DEFAULT_BACKGROUND_NAME, DEFAULT_BACKGROUND_URL, DEFAULT_BACKGROUND_URL);
		}
		setBackgroundImage(DEFAULT_BACKGROUND_NAME, animationDuration);
	}

	public void setBackgroundColor(Color color, int animationDuration) {
		queueCommand(new UiRootPanel.SetBackgroundColorCommand(color != null ? color.toHtmlColorString() : null, animationDuration));
	}

	public void exitFullScreen() {
		queueCommand(new UiRootPanel.ExitFullScreenCommand());
	}

	@Deprecated
	public void addRootComponent(String containerElementId, RootPanel rootPanel) {
		addRootPanel(containerElementId, rootPanel);
	}

	public void addRootPanel(String containerElementId, RootPanel rootPanel) {
		queueCommand(new UiRootPanel.BuildRootPanelCommand(containerElementId, rootPanel.createUiReference()));
	}

	public RootPanel addRootPanel(String containerElementId) {
		RootPanel rootPanel = new RootPanel();
		addRootPanel(containerElementId, rootPanel);
		return rootPanel;
	}

	public RootPanel addRootPanel() {
		return addRootPanel(null);
	}

	public void addClientToken(String token) {
		getClientInfo().getClientTokens().add(token);
		queueCommand(new UiRootPanel.AddClientTokenCommand(token));
	}

	public void removeClientToken(String token) {
		getClientInfo().getClientTokens().remove(token);
		queueCommand(new UiRootPanel.RemoveClientTokenCommand(token));
	}

	public void clearClientTokens() {
		getClientInfo().getClientTokens().clear();
		queueCommand(new UiRootPanel.ClearClientTokensCommand());
	}

	public void showNotification(Notification notification, NotificationPosition position, EntranceAnimation entranceAnimation, ExitAnimation exitAnimation) {
		runWithContext(() -> {
			queueCommand(new UiRootPanel.ShowNotificationCommand(notification.createUiReference(), position.toUiNotificationPosition(), entranceAnimation.toUiEntranceAnimation(),
					exitAnimation.toUiExitAnimation()));
		});
	}

	public void showNotification(Notification notification, NotificationPosition position) {
		runWithContext(() -> {
			showNotification(notification, position, EntranceAnimation.SLIDE_IN_RIGHT, ExitAnimation.FADE_OUT);
		});
	}

	public void showNotification(Icon icon, String caption) {
		runWithContext(() -> {
			Notification notification = Notification.createWithIconAndCaption(icon, caption);
			notification.setDismissible(true);
			notification.setShowProgressBar(false);
			notification.setDisplayTimeInMillis(5000);
			showNotification(notification, NotificationPosition.TOP_RIGHT, EntranceAnimation.SLIDE_IN_RIGHT, ExitAnimation.FADE_OUT);
		});
	}

	public void showNotification(Icon icon, String caption, String description) {
		runWithContext(() -> {
			Notification notification = Notification.createWithIconAndTextAndDescription(icon, caption, description);
			notification.setDismissible(true);
			notification.setShowProgressBar(false);
			notification.setDisplayTimeInMillis(5000);
			showNotification(notification, NotificationPosition.TOP_RIGHT, EntranceAnimation.SLIDE_IN_RIGHT, ExitAnimation.FADE_OUT);
		});
	}

	public void showNotification(Icon icon, String caption, String description, boolean dismissable, int displayTimeInMillis, boolean showProgress) {
		runWithContext(() -> {
			Notification notification = Notification.createWithIconAndTextAndDescription(icon, caption, description);
			notification.setDismissible(dismissable);
			notification.setDisplayTimeInMillis(displayTimeInMillis);
			notification.setShowProgressBar(showProgress);
			showNotification(notification, NotificationPosition.TOP_RIGHT, EntranceAnimation.SLIDE_IN_RIGHT, ExitAnimation.FADE_OUT);
		});
	}

	public void setSessionExpiredWindow(Window sessionExpiredWindow) {
		this.sessionExpiredWindow = sessionExpiredWindow;
		updateSessionMessageWindows();
	}

	public void setSessionErrorWindow(Window sessionErrorWindow) {
		this.sessionErrorWindow = sessionErrorWindow;
		updateSessionMessageWindows();
	}

	public void setSessionTerminatedWindow(Window sessionTerminatedWindow) {
		this.sessionTerminatedWindow = sessionTerminatedWindow;
		updateSessionMessageWindows();
	}

	private void updateSessionMessageWindows() {
		queueCommand(new UiRootPanel.SetSessionMessageWindowsCommand(
				sessionExpiredWindow != null ? sessionExpiredWindow.createUiReference()
						: createDefaultSessionMessageWindow(getLocalized("teamapps.common.sessionExpired"), getLocalized("teamapps.common.sessionExpiredText"),
						getLocalized("teamapps.common.refresh"), getLocalized("teamapps.common.cancel")).createUiReference(),
				sessionErrorWindow != null ? sessionErrorWindow.createUiReference() : createDefaultSessionMessageWindow(getLocalized("teamapps.common.error"), getLocalized("teamapps.common.sessionErrorText"),
						getLocalized("teamapps.common.refresh"), getLocalized("teamapps.common.cancel")).createUiReference(),
				sessionTerminatedWindow != null ? sessionTerminatedWindow.createUiReference() : createDefaultSessionMessageWindow(getLocalized("teamapps.common.sessionTerminated"), getLocalized("teamapps.common.sessionTerminatedText"),
						getLocalized("teamapps.common.refresh"), getLocalized("teamapps.common.cancel")).createUiReference())
		);
	}

	public static Window createDefaultSessionMessageWindow(String title, String message, String refreshButtonCaption, String cancelButtonCaption) {
		Window window = new Window(null, title, null, 300, 300, false, false, false);
		window.setPadding(10);

		VerticalLayout verticalLayout = new VerticalLayout();

		DisplayField messageField = new DisplayField(false, false);
		messageField.setCssStyle("font-size", "110%");
		messageField.setValue(message);
		verticalLayout.addComponentFillRemaining(messageField);

		Button<?> refreshButton = new Button<>(null, refreshButtonCaption);
		refreshButton.setCssStyle("margin", "10px 0");
		refreshButton.setCssStyle(".UiButton", "background-color", RgbaColor.MATERIAL_BLUE_600.toHtmlColorString());
		refreshButton.setCssStyle(".UiButton", "color", RgbaColor.WHITE.toHtmlColorString());
		refreshButton.setCssStyle(".UiButton", "font-size", "120%");
		refreshButton.setCssStyle(".UiButton", "height", "50px");
		refreshButton.setOnClickJavaScript("window.location.reload()");
		verticalLayout.addComponentAutoSize(refreshButton);

		if (cancelButtonCaption != null) {
			LinkButton cancelLink = new LinkButton(cancelButtonCaption);
			cancelLink.setCssStyle("text-align", "center");
			cancelLink.setOnClickJavaScript("context.getClientObjectById(\"" + window.createUiReference().getId() + "\").close();");
			verticalLayout.addComponentAutoSize(cancelLink);
		}

		window.setContent(verticalLayout);
		return window;
	}

	public CompletableFuture<WakeLock> requestWakeLock() {
		String uuid = UUID.randomUUID().toString();
		CompletableFuture<WakeLock> completableFuture = new CompletableFuture<>();
		runWithContext(() -> {
			queueCommand(new UiRootPanel.RequestWakeLockCommand(uuid), successful -> {
				if (successful) {
					completableFuture.complete(() -> queueCommand(new UiRootPanel.ReleaseWakeLockCommand(uuid)));
				} else {
					completableFuture.completeExceptionally(new RuntimeException("Could not acquire WakeLock"));
				}
			});
		});
		return completableFuture;
	}

	public void goToUrl(String url, boolean blankPage) {
		queueCommand(new UiRootPanel.GoToUrlCommand(url, blankPage));
	}
}
