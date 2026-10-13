/**
 * Copyright 2013-2025 Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.netshot.netshot.diagnostic;

import java.util.Objects;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.config.attribute.LongTextConfiguration;
import net.netshot.netshot.inventory.device.Device;

@Entity
@DiscriminatorValue("T")
public class DiagnosticLongTextResult extends DiagnosticResult {

	@Getter(onMethod = @__({
		@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	private LongTextConfiguration longText;

	protected DiagnosticLongTextResult() {
	}

	public DiagnosticLongTextResult(Device device, Diagnostic diagnostic, String value) {
		super(device, diagnostic);
		this.longText = new LongTextConfiguration(value);
	}

	@Override
	@Transient
	public String getAsText() {
		if (longText == null || longText.getText() == null) {
			return "";
		}
		return longText.getText();
	}

	@Override
	@Transient
	public Object getData() {
		if (getLongText() == null) {
			return null;
		}
		return getLongText().getText();
	}

	@Override
	public boolean valueEquals(DiagnosticResult obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DiagnosticLongTextResult)) {
			return false;
		}
		DiagnosticLongTextResult other = (DiagnosticLongTextResult) obj;
		return Objects.equals(this.longText, other.longText);
	}

}
