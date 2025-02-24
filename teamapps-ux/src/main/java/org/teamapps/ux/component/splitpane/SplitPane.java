/*-
 * ========================LICENSE_START=================================
 * TeamApps
 * ---
 * Copyright (C) 2014 - 2022 TeamApps.org
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
package org.teamapps.ux.component.splitpane;

import org.teamapps.dto.UiComponent;
import org.teamapps.dto.UiEvent;
import org.teamapps.dto.UiSplitPane;
import org.teamapps.event.Event;
import org.teamapps.ux.component.AbstractComponent;
import org.teamapps.ux.component.Component;

public class SplitPane extends AbstractComponent {

	public Event<Float> onResized = new Event<>();

	private SplitDirection splitDirection;
	private SplitSizePolicy sizePolicy;
	private float referenceChildSize;
	private Component firstChild;
	private Component lastChild;
	private int firstChildMinSize = 10;
	private int lastChildMinSize = 10;
	private boolean resizable = true;
	private boolean fillIfSingleChild = true;
	private boolean collapseEmptyChildren = true;

	public static SplitPane createHorizontalSplitPane() {
		return new SplitPane(SplitDirection.HORIZONTAL);
	}

	public static SplitPane createVerticalSplitPane() {
		return new SplitPane(SplitDirection.VERTICAL);
	}

	public SplitPane(SplitDirection splitDirection) {
		this(splitDirection, SplitSizePolicy.RELATIVE, 0.5f);
	}

	public SplitPane(SplitDirection splitDirection, SplitSizePolicy sizePolicy, float referenceChildSize) {
		this.splitDirection = splitDirection;
		this.sizePolicy = sizePolicy;
		this.referenceChildSize = referenceChildSize;
	}

	@Override
	public UiComponent createUiComponent() {
		UiSplitPane uiSplitPane = new UiSplitPane(splitDirection.toUiSplitDirection(), sizePolicy.toUiSplitSizePolicy());
		mapAbstractUiComponentProperties(uiSplitPane);
		uiSplitPane.setReferenceChildSize(referenceChildSize);
		if (firstChild != null) {
			uiSplitPane.setFirstChild(firstChild.createUiReference());
		}
		if (lastChild != null) {
			uiSplitPane.setLastChild(lastChild.createUiReference());
		}
		uiSplitPane.setFirstChildMinSize(firstChildMinSize);
		uiSplitPane.setLastChildMinSize(lastChildMinSize);
		uiSplitPane.setResizable(resizable);
		uiSplitPane.setFillIfSingleChild(fillIfSingleChild);
		uiSplitPane.setCollapseEmptyChildren(collapseEmptyChildren);

		return uiSplitPane;
	}

	@Override
	public void handleUiEvent(UiEvent event) {
		switch (event.getUiEventType()) {
			case UI_SPLIT_PANE_SPLIT_RESIZED:
				UiSplitPane.SplitResizedEvent resizedEvent = (UiSplitPane.SplitResizedEvent) event;
				this.referenceChildSize = resizedEvent.getReferenceChildSize();
				onResized.fire(resizedEvent.getReferenceChildSize());
				break;
		}
	}

	public Component getFirstChild() {
		return firstChild;
	}

	public void setFirstChild(Component firstChild) {
		this.firstChild = firstChild;
		queueCommandIfRendered(() -> new UiSplitPane.SetFirstChildCommand(getId(), firstChild != null ? firstChild.createUiReference() : null));
	}

	public Component getLastChild() {
		return lastChild;
	}

	public void setLastChild(Component lastChild) {
		this.lastChild = lastChild;
		queueCommandIfRendered(() -> new UiSplitPane.SetLastChildCommand(getId(), lastChild != null ? lastChild.createUiReference() : null));
	}

	public void setSize(float referenceChildSize, SplitSizePolicy sizePolicy) {
		if (sizePolicy == null) {
			return;
		}
		this.referenceChildSize = referenceChildSize;
		this.sizePolicy = sizePolicy;
		queueCommandIfRendered(() -> new UiSplitPane.SetSizeCommand(getId(), referenceChildSize, sizePolicy.toUiSplitSizePolicy()));
	}

	public SplitDirection getSplitDirection() {
		return splitDirection;
	}

	public void setSplitDirection(SplitDirection splitDirection) {
		this.splitDirection = splitDirection;
		reRenderIfRendered();
	}

	public SplitSizePolicy getSizePolicy() {
		return sizePolicy;
	}

	public void setSizePolicy(SplitSizePolicy sizePolicy) {
		this.sizePolicy = sizePolicy;
		queueCommandIfRendered(() -> new UiSplitPane.SetSizeCommand(getId(), referenceChildSize, sizePolicy.toUiSplitSizePolicy()));
	}

	public float getReferenceChildSize() {
		return referenceChildSize;
	}

	public void setReferenceChildSize(float referenceChildSize) {
		this.referenceChildSize = referenceChildSize;
		queueCommandIfRendered(() -> new UiSplitPane.SetSizeCommand(getId(), referenceChildSize, sizePolicy.toUiSplitSizePolicy()));
	}

	public int getFirstChildMinSize() {
		return firstChildMinSize;
	}

	public void setFirstChildMinSize(int firstChildMinSize) {
		this.firstChildMinSize = firstChildMinSize;
		this.queueCommandIfRendered(() -> new UiSplitPane.SetFirstChildMinSizeCommand(getId(), firstChildMinSize));
	}

	public int getLastChildMinSize() {
		return lastChildMinSize;
	}

	public void setLastChildMinSize(int lastChildMinSize) {
		this.lastChildMinSize = lastChildMinSize;
		this.queueCommandIfRendered(() -> new UiSplitPane.SetLastChildMinSizeCommand(getId(), lastChildMinSize));
	}

	public boolean isResizable() {
		return resizable;
	}

	public void setResizable(boolean resizable) {
		this.resizable = resizable;
		this.reRenderIfRendered();
	}

	public boolean isFillIfSingleChild() {
		return fillIfSingleChild;
	}

	public void setFillIfSingleChild(boolean fillIfSingleChild) {
		this.fillIfSingleChild = fillIfSingleChild;
		this.reRenderIfRendered();
	}

	public boolean isCollapseEmptyChildren() {
		return collapseEmptyChildren;
	}

	public void setCollapseEmptyChildren(boolean collapseEmptyChildren) {
		this.collapseEmptyChildren = collapseEmptyChildren;
		this.reRenderIfRendered();
	}
}
