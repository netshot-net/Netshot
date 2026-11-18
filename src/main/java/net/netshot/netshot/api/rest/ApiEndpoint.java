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
package net.netshot.netshot.api.rest;

/**
 * Where and how the REST API is served: the path the API versions are served under, and the name of the session
 * cookie. Given by the {@link RestServer} to the web applications (their OpenAPI documents describe it).
 *
 * @param path the path the REST API is served on, e.g. {@code /api}
 * @param sessionCookieName the name of the session cookie
 */
public record ApiEndpoint(String path, String sessionCookieName) {
}
