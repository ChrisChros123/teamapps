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
package org.teamapps.ux.component.charting.pie;

import org.teamapps.common.format.Color;
import org.teamapps.dto.UiChartNamedDataPoint;

public class ChartNamedDataPoint {

	private final String name;
	private final double value;
	private final Color color;

	public ChartNamedDataPoint(String name, double value, Color color) {
		this.name = name;
		this.value = value;
		this.color = color;
	}

	public UiChartNamedDataPoint createUiChartNamedDataPoint() {
		UiChartNamedDataPoint ui = new UiChartNamedDataPoint(name, value);
		ui.setColor(color != null ? color.toHtmlColorString() : null);
		return ui;
	}

	public String getName() {
		return name;
	}

	public double getValue() {
		return value;
	}

	public Color getColor() {
		return color;
	}
}
