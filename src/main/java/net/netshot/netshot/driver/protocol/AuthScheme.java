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
package net.netshot.netshot.driver.protocol;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Declarative HTTP authentication scheme for an access, inspired by
 * OpenAPI's {@code securitySchemes} object.
 */
public final class AuthScheme {
	/** "http", "apiKey", "cookie", "oauth2" or "openIdConnect". */
	@Getter
	@Setter
	private String type;

	/** For type "http": "basic" or "bearer". */
	@Getter
	@Setter
	private String scheme;

	/** For type "apiKey": "header", "query" or "cookie". */
	@Getter
	@Setter
	private String in;

	/** For type "apiKey": the header/query/cookie name. */
	@Getter
	@Setter
	private String name;

	/** For type "cookie": the HTTP method used to log in ("POST" or "PUT"). */
	@Getter
	@Setter
	private String method;

	/** For type "cookie": the login request path (relative to the access's base path). */
	@Getter
	@Setter
	private String path;

	/** For type "cookie": the login request body, with {@code $$NetshotUsername$$}/
	 * {@code $$NetshotPassword$$} placeholders substituted from the resolved credential. */
	@Getter
	@Setter
	private Map<String, Object> data;

	/** For type "cookie": "json" or "form" - how {@link #data} is encoded in the login request body. */
	@Getter
	@Setter
	private String contentType;

	public AuthScheme() {
	}
}
