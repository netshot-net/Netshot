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
package net.netshot.netshot.platform.rest;

/**
 * Defines the serialization views still in use: the ones of the cluster wire format (the messages exchanged
 * between the nodes, which embed a {@link net.netshot.netshot.cluster.ClusterMember}). The API channels
 * (REST and web hooks) do not use views.
 */
public class RestViews {
	// Default view
	public static class DefaultView {
	}

	// Properties of the cluster members which are not part of the cluster wire format
	public static class RestApiView extends DefaultView {
	}

	// Clustering view
	public static class ClusteringView extends DefaultView {
	}
}
