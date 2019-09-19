package org.teamapps.ux.component.progress;

import org.teamapps.dto.UiEvent;
import org.teamapps.dto.UiMultiProgressDisplay;
import org.teamapps.event.Event;
import org.teamapps.icons.api.Icon;
import org.teamapps.ux.component.AbstractComponent;
import org.teamapps.ux.component.field.DisplayField;
import org.teamapps.ux.component.flexcontainer.VerticalLayout;
import org.teamapps.ux.component.format.Spacing;
import org.teamapps.ux.component.notification.Notification;
import org.teamapps.ux.component.notification.NotificationPosition;
import org.teamapps.ux.task.ObservableProgress;
import org.teamapps.ux.task.ProgressCompletableFuture;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.teamapps.ux.task.ProgressStatus.*;

public class MultiProgressDisplay extends AbstractComponent implements MultiProgressConsumer {

	public final Event<Void> onClicked = new Event<>();

	private final List<ObservableProgress> progresses = new ArrayList<>();
	private final Notification progressListNotification;
	private final VerticalLayout progressListVerticalLayout;
	private final DisplayField noEntriesDisplayField;

	private boolean showNotificationOnProgressAdded = true;
	private int notificationDisplayTimeMillis = 3000;
	private NotificationPosition notificationPosition = NotificationPosition.BOTTOM_RIGHT;
	private int listEntryRemainTimeout = 5000;

	private boolean showingNotificationWithoutTimeout = false;

	public MultiProgressDisplay() {
		progressListVerticalLayout = new VerticalLayout();
		noEntriesDisplayField = new DisplayField();
		noEntriesDisplayField.setValue("<div style=\"text-align: center\">" + getSessionContext().getLocalized("dict.noRunningTasks") + "</div>");
		noEntriesDisplayField.setShowHtml(true);
		progressListVerticalLayout.addComponent(noEntriesDisplayField);
		progressListNotification = new Notification(progressListVerticalLayout);
		progressListNotification.setPadding(new Spacing(2, 4, 2, 4));
		progressListNotification.setShowProgressBar(false);
		progressListNotification.onClosed.addListener(byUser -> {
			showingNotificationWithoutTimeout = false;
		});
	}

	@Override
	public UiMultiProgressDisplay createUiComponent() {
		UiMultiProgressDisplay ui = new UiMultiProgressDisplay();
		mapAbstractUiComponentProperties(ui);
		ui.setRunningCount(progresses.size());
		ui.setStatusMessages(progresses.stream().map(p -> p.getStatusMessage()).collect(Collectors.toList()));
		return ui;
	}

	@Override
	public void handleUiEvent(UiEvent event) {
		switch (event.getUiEventType()) {
			case UI_MULTI_PROGRESS_DISPLAY_CLICKED: {
				this.onClicked.fire(null);
				if (showingNotificationWithoutTimeout) {
					this.showingNotificationWithoutTimeout = false;
					progressListNotification.close();
				} else {
					this.showingNotificationWithoutTimeout = true;
					if (progresses.size() > 0) {
						progressListNotification.setDisplayTimeInMillis(-1);
					} else {
						progressListNotification.setDisplayTimeInMillis(2000);
					}
					getSessionContext().showNotification(progressListNotification, notificationPosition);
				}
			}
		}
	}

	@Override
	public void addProgress(Icon icon, String taskName, ObservableProgress progress) {
		this.progresses.add(progress);
		progressListVerticalLayout.removeComponent(noEntriesDisplayField);
		ProgressDisplay progressDisplay = new ProgressDisplay(icon, taskName, progress);
		progressDisplay.setMargin(new Spacing(2, 0, 2, 0));
		progressListVerticalLayout.addComponent(progressDisplay);
		progress.onChanged().addListener(data -> {
			if (EnumSet.of(CANCELED, COMPLETE, FAILED).contains(data.getStatus())) {
				showNotificationDueToUpdate();
				this.progresses.remove(progress);
				this.update();

				new ProgressCompletableFuture<Void>().completeOnTimeout(null, listEntryRemainTimeout, TimeUnit.MILLISECONDS)
						.handleWithCurrentSessionContext((aVoid, throwable) -> {
							progressListVerticalLayout.removeComponent(progressDisplay);
							if (progressListVerticalLayout.getComponents().size() == 0) {
								progressListVerticalLayout.addComponent(noEntriesDisplayField);
							}
							if (this.progresses.size() == 0 && !showingNotificationWithoutTimeout) {
								this.progressListNotification.close();
							}
							return null;
						});
			}
		});
		showNotificationDueToUpdate();
		this.update();
	}

	private void showNotificationDueToUpdate() {
		if (!showingNotificationWithoutTimeout) {
			progressListNotification.setDisplayTimeInMillis(notificationDisplayTimeMillis);
			getSessionContext().showNotification(progressListNotification, notificationPosition);
		}
	}

	private void update() {
		queueCommandIfRendered(() -> new UiMultiProgressDisplay.UpdateCommand(this.getId(), createUiComponent()));
	}

	public boolean isShowNotificationOnProgressAdded() {
		return showNotificationOnProgressAdded;
	}

	public void setShowNotificationOnProgressAdded(boolean showNotificationOnProgressAdded) {
		this.showNotificationOnProgressAdded = showNotificationOnProgressAdded;
	}

	public int getNotificationDisplayTimeMillis() {
		return notificationDisplayTimeMillis;
	}

	public void setNotificationDisplayTimeMillis(int notificationDisplayTimeMillis) {
		this.notificationDisplayTimeMillis = notificationDisplayTimeMillis;
	}

	public int getListEntryRemainTimeout() {
		return listEntryRemainTimeout;
	}

	public void setListEntryRemainTimeout(int listEntryRemainTimeout) {
		this.listEntryRemainTimeout = listEntryRemainTimeout;
	}
}
