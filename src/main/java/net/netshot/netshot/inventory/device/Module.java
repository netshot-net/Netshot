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
package net.netshot.netshot.inventory.device;

import java.util.Date;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * A network device line module.
 */
@Entity
public class Module {

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	private long id;

	/** The slot. */
	@Getter
	@Setter
	protected String slot;

	/** The part number. */
	@Getter
	@Setter
	protected String partNumber;

	/** The serial number. */
	@Getter
	@Setter
	protected String serialNumber;

	/** The device. */
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	protected Device device;

	/** When the module was first seen. */
	@Getter
	@Setter
	protected Date firstSeenDate;

	/** When the module was last seen. */
	@Getter
	@Setter
	protected Date lastSeenDate;

	/** Whether the module was removed or is still present. */
	@Getter
	@Setter
	protected boolean removed;


	public Module() {

	}

	public Module(String slot, String partNumber, String serialNumber, Device device) {
		this.slot = slot == null ? "" : slot;
		this.partNumber = partNumber == null ? "" : partNumber;
		this.serialNumber = serialNumber == null ? "" : serialNumber;
		this.device = device;
		this.removed = false;
	}

}
