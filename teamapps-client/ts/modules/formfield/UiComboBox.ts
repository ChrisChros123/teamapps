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
import {TrivialComboBox} from "../trivial-components/TrivialComboBox";
import {TrivialTreeBox} from "../trivial-components/TrivialTreeBox";

import {UiFieldEditingMode} from "../../generated/UiFieldEditingMode";
import {UiTextMatchingMode} from "../../generated/UiTextMatchingMode";
import {UiField} from "./UiField";
import {TeamAppsUiContext} from "../TeamAppsUiContext";
import {UiComboBoxCommandHandler, UiComboBoxConfig, UiComboBoxEventSource} from "../../generated/UiComboBoxConfig";
import {TeamAppsEvent} from "../util/TeamAppsEvent";
import {TeamAppsUiComponentRegistry} from "../TeamAppsUiComponentRegistry";
import {
	UiTextInputHandlingField_SpecialKeyPressedEvent,
	UiTextInputHandlingField_TextInputEvent
} from "../../generated/UiTextInputHandlingFieldConfig";
import {UiSpecialKey} from "../../generated/UiSpecialKey";
import {UiComboBoxTreeRecordConfig} from "../../generated/UiComboBoxTreeRecordConfig";
import {UiTemplateConfig} from "../../generated/UiTemplateConfig";
import {buildObjectTree, NodeWithChildren, Renderer} from "../Common";
import {TreeBoxDropdown} from "../trivial-components/dropdown/TreeBoxDropdown";

export function isFreeTextEntry(o: UiComboBoxTreeRecordConfig): boolean {
	return o != null && o.id < 0;
}

export class UiComboBox extends UiField<UiComboBoxConfig, UiComboBoxTreeRecordConfig> implements UiComboBoxEventSource, UiComboBoxCommandHandler {
	public readonly onTextInput: TeamAppsEvent<UiTextInputHandlingField_TextInputEvent> = new TeamAppsEvent(this, {throttlingMode: "debounce", delay: 250});
	public readonly onSpecialKeyPressed: TeamAppsEvent<UiTextInputHandlingField_SpecialKeyPressedEvent> = new TeamAppsEvent(this, {throttlingMode: "debounce", delay: 250});

	private trivialComboBox: TrivialComboBox<NodeWithChildren<UiComboBoxTreeRecordConfig>>;
	private templateRenderers: { [name: string]: Renderer };

	private freeTextIdEntryCounter = -1;

	protected initialize(config: UiComboBoxConfig, context: TeamAppsUiContext) {
		this.templateRenderers = config.templates != null ? context.templateRegistry.createTemplateRenderers(config.templates) : {};

		this.trivialComboBox = new TrivialComboBox<NodeWithChildren<UiComboBoxTreeRecordConfig>>({
			allowFreeText: config.allowAnyText,
			selectedEntryRenderingFunction: entry => {
				if (entry == null) {
					return "";
				} else if (isFreeTextEntry(entry)) {
					return `<div class="free-text-entry">${entry.asString}</div>`;
				} else {
					return this.renderRecord(entry, false);
				}
			},
			autoComplete: !!config.autoComplete,
			showTrigger: config.showDropDownButton,
			editingMode: config.editingMode === UiFieldEditingMode.READONLY ? 'readonly' : config.editingMode === UiFieldEditingMode.DISABLED ? 'disabled' : 'editable',

			spinnerTemplate: `<div class="teamapps-spinner" style="height: 20px; width: 20px; margin: 4px auto 4px auto;"></div>`,
			textHighlightingEntryLimit: config.textHighlightingEntryLimit,
			showDropDownOnResultsOnly: config.showDropDownAfterResultsArrive,
			showClearButton: config.showClearButton,
			entryToEditorTextFunction: e => e.asString,
			freeTextEntryFactory: (freeText) => {
				return {id: this.freeTextIdEntryCounter--, values: {}, asString: freeText};
			},
		}, new TreeBoxDropdown({
			queryFunction: (queryString: string) => {
				this.onTextInput.fire({enteredString: queryString}); // TODO this is definitely the wrong place for this!!
				return config.retrieveDropdownEntries({queryString})
					.then(entries => buildObjectTree(entries, "id", "parentId"));
			},
			textHighlightingEntryLimit: config.textHighlightingEntryLimit,
			preselectionMatcher: (query, entry) => entry.asString.toLowerCase().indexOf(query.toLowerCase()) >= 0
		}, new TrivialTreeBox<NodeWithChildren<UiComboBoxTreeRecordConfig>>({
			childrenProperty: "__children",
			expandedProperty: "expanded",
			showExpanders: config.showExpanders,
			entryRenderingFunction: entry => this.renderRecord(entry, true),
			idFunction: entry => entry && entry.id,
			lazyChildrenQueryFunction: async (node: NodeWithChildren<UiComboBoxTreeRecordConfig>) => buildObjectTree(await config.lazyChildren({parentId: node.id}), "id", "parentId"),
			lazyChildrenFlag: entry => entry.lazyChildren,
			selectableDecider: entry => entry.selectable,
			selectOnHover: true,
			highlightHoveredEntries: false,
			animationDuration: this._config.animate ? 120 : 0
		})));
		this.trivialComboBox.getMainDomElement().classList.add("UiComboBox");
		this.trivialComboBox.onSelectedEntryChanged.addListener(() => this.commit());
		this.trivialComboBox.getEditor().addEventListener("keydown", (e: KeyboardEvent) => {
			if (e.key === "Escape") {
				this.onSpecialKeyPressed.fire({
					key: UiSpecialKey.ESCAPE
				});
			} else if (e.key === "Enter") {
				this.onSpecialKeyPressed.fire({
					key: UiSpecialKey.ENTER
				});
			}
		});

		this.trivialComboBox.getMainDomElement().classList.add("field-border", "field-border-glow", "field-background");
		this.trivialComboBox.getMainDomElement().querySelector<HTMLElement>(":scope .tr-editor").classList.add("field-background");
		this.trivialComboBox.getMainDomElement().querySelector<HTMLElement>(":scope .tr-trigger").classList.add("field-border");
		this.trivialComboBox.onFocus.addListener(() => this.getMainElement().classList.add("focus"));
		this.trivialComboBox.onBlur.addListener(() => this.getMainElement().classList.remove("focus"));
	}

	private renderRecord(record: NodeWithChildren<UiComboBoxTreeRecordConfig>, dropdown: boolean): string {
		const templateId = dropdown ? record.dropDownTemplateId : record.displayTemplateId;
		if (templateId != null && this.templateRenderers[templateId] != null) {
			const renderer = this.templateRenderers[templateId];
			return renderer.render(record.values);
		} else {
			return `<div class="string-template">${record.asString}</div>`;
		}
	}

	isValidData(v: any): boolean {
		return v == null || typeof v === "object" && (v as UiComboBoxTreeRecordConfig).id != null;
	}

	static createTrivialMatchingOptions(config: { textMatchingMode?: UiTextMatchingMode }) {
		let matchingModes: { [x: number]: 'contains' | 'prefix' | 'prefix-word' | 'prefix-levenshtein' | 'levenshtein' } = {
			[UiTextMatchingMode.PREFIX]: "prefix",
			[UiTextMatchingMode.PREFIX_WORD]: "prefix-word",
			[UiTextMatchingMode.CONTAINS]: "contains",
			[UiTextMatchingMode.SIMILARITY]: "prefix-levenshtein"
		};
		return {
			matchingMode: matchingModes[config.textMatchingMode],
			ignoreCase: true,
			maxLevenshteinDistance: 3
		};
	}

	public getMainInnerDomElement(): HTMLElement {
		return this.trivialComboBox.getMainDomElement() as HTMLElement;
	}

	public getFocusableElement(): HTMLElement {
		return this.trivialComboBox.getMainDomElement() as HTMLElement;
	}

	protected displayCommittedValue(): void {
		let uiValue = this.getCommittedValue();
		this.trivialComboBox.setSelectedEntry(uiValue, true);
	}

	public getTransientValue(): any {
		return this.trivialComboBox.getSelectedEntry();
	}

	protected convertValueForSendingToServer(value: UiComboBoxTreeRecordConfig): any {
		if (value == null) {
			return null;
		}
		return isFreeTextEntry(value) ? value.asString : value.id;
	}

	focus(): void {
		this.trivialComboBox.focus();
	}

	public hasFocus(): boolean {
		return this.getMainInnerDomElement().matches('.focus');
	}

	protected onEditingModeChanged(editingMode: UiFieldEditingMode): void {
		this.getMainElement().classList.remove(...Object.values(UiField.editingModeCssClasses));
		this.getMainElement().classList.add(UiField.editingModeCssClasses[editingMode]);
		if (editingMode === UiFieldEditingMode.READONLY) {
			this.trivialComboBox.setEditingMode("readonly");
		} else if (editingMode === UiFieldEditingMode.DISABLED) {
			this.trivialComboBox.setEditingMode("disabled");
		} else {
			this.trivialComboBox.setEditingMode("editable");
		}
	}

	registerTemplate(id: string, template: UiTemplateConfig): void {
		this.templateRenderers[id] = this._context.templateRegistry.createTemplateRenderer(template);
	}

	replaceFreeTextEntry(freeText: string, record: UiComboBoxTreeRecordConfig): void {
		const selectedEntry = this.trivialComboBox.getSelectedEntry();
		if (isFreeTextEntry(selectedEntry) && selectedEntry.asString === freeText) {
			this.setCommittedValue(record);
		}
	}

	destroy(): void {
		super.destroy();
		this.trivialComboBox.destroy();
	}

	public getReadOnlyHtml(value: UiComboBoxTreeRecordConfig, availableWidth: number): string {
		if (value != null) {
			return `<div class="static-readonly-UiComboBox">${this.renderRecord(value, false)}</div>`;
		} else {
			return "";
		}
	}

	getDefaultValue(): any {
		return null;
	}

	public valuesChanged(v1: UiComboBoxTreeRecordConfig, v2: UiComboBoxTreeRecordConfig): boolean {
		let nullAndNonNull = ((v1 == null) !== (v2 == null));
		let nonNullAndValuesDifferent = (v1 != null && v2 != null && (
			v1.id !== v2.id
			|| v1.id !== v2.id
		));
		return nullAndNonNull || nonNullAndValuesDifferent;
	}
}

TeamAppsUiComponentRegistry.registerFieldClass("UiComboBox", UiComboBox);
