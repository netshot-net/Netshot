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
package net.netshot.netshot.api;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import net.netshot.netshot.api.RestService.RsErrorBean;

/**
 * Exception to be thrown when the user is authenticated but not
 * not allowed to perform an action.
 */
public class NetshotNotAuthorizedException extends ForbiddenException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -453816975689585686L;

	public NetshotNotAuthorizedException(String message, int errorCode) {
		super(Response.status(Response.Status.FORBIDDEN)
			.entity(new RsErrorBean(message, errorCode)).build());
	}
}
