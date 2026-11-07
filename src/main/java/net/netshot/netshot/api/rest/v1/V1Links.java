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
package net.netshot.netshot.api.rest.v1;

import java.util.Arrays;
import java.util.stream.Collectors;

import net.netshot.netshot.api.rest.RestServer;

/**
 * The paths of the objects of the v1 API ({@code /api/v1/domains/3}), as written in the {@code url} property of
 * the representations and in the {@code Location} headers: absolute paths without scheme and host, so that they
 * hold behind a reverse proxy.
 */
public final class V1Links {

	/** The path of the v1 API under the API path. */
	public static final String VERSION_PATH = "/v1";

	private V1Links() {
	}

	/**
	 * Gets the base path of the v1 API.
	 *
	 * @return the path, e.g. {@code /api/v1}
	 */
	public static String base() {
		String apiPath = RestServer.apiPath();
		return (apiPath.endsWith("/") ? apiPath.substring(0, apiPath.length() - 1) : apiPath) + VERSION_PATH;
	}

	/**
	 * Builds the path of a resource.
	 *
	 * @param segments the segments of the path, e.g. {@code "domains", 3}
	 * @return the path, e.g. {@code /api/v1/domains/3}
	 */
	public static String of(Object... segments) {
		return base() + "/" + Arrays.stream(segments).map(String::valueOf).collect(Collectors.joining("/"));
	}
}
