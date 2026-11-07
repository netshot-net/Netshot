/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.platform.database;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.jooq.impl.AbstractConverter;

/**
 * jOOQ converter of the {@code timestamp} (without time zone) columns to
 * {@link Instant}: the dates are stored in UTC (as Hibernate stored them, with
 * {@code hibernate.jdbc.time_zone = UTC}), so a stored value is the UTC
 * date and time of the instant.
 * Used by the generated classes, see the jOOQ code generation.
 */
public class UtcInstantConverter extends AbstractConverter<LocalDateTime, Instant> {

	private static final long serialVersionUID = 1L;

	public UtcInstantConverter() {
		super(LocalDateTime.class, Instant.class);
	}

	@Override
	public Instant from(LocalDateTime databaseObject) {
		return databaseObject == null ? null : databaseObject.toInstant(ZoneOffset.UTC);
	}

	@Override
	public LocalDateTime to(Instant userObject) {
		return userObject == null ? null : LocalDateTime.ofInstant(userObject, ZoneOffset.UTC);
	}
}
