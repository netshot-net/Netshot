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
package net.netshot.netshot.credentials;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**
 * SSH account for a device to upload data to Netshot.
 */
@Entity
public class NetshotSshAccount {

	/** Unique ID. */
	@Getter(onMethod = @__({
		@Id,
		@GeneratedValue(strategy = GenerationType.IDENTITY),
	}))
	@Setter
	private long id;

	/** Date/time of creation. */
	@Getter
	@Setter
	private Date creationDate;

	/** Date/time of expiration (null if no expiration). */
	@Getter
	@Setter
	private Date expirationDate;

	/** The username. */
	@Getter
	@Setter
	private String username;

	/** The hashed password. */
	@Getter
	@Setter
	private String hashedPassword;
	
}
