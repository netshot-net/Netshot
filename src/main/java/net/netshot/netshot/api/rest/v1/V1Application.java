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
package net.netshot.netshot.api.rest.v1;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import jakarta.inject.Singleton;

import net.netshot.netshot.api.rest.AuthenticationFilter;
import net.netshot.netshot.api.rest.LoggerFilter;
import net.netshot.netshot.api.rest.v1.device.DeviceRefs;
import net.netshot.netshot.api.rest.v1.export.CsvListWriter;
import net.netshot.netshot.api.rest.v1.export.ExportFilter;
import net.netshot.netshot.api.rest.v1.export.ExportNegotiation;
import net.netshot.netshot.api.rest.v1.export.XlsxListWriter;

/**
 * The Jersey application of the v1 REST API, served on {@code <api path>/v1} beside the legacy (v0) one. It
 * shares the authentication, the session and the services with v0, and has its own body provider
 * ({@link V1EntityProvider}: JSON, XML and YAML with the v1 conventions), error rendering
 * ({@link V1ExceptionMapper}: problem details), response hints ({@link V1Response}) and OpenAPI document
 * ({@link V1OpenApiResource}).
 */
public class V1Application extends ResourceConfig {

	/**
	 * Instantiates the web application.
	 *
	 * @param binder the dependency injection binder exposing the services to the REST resources
	 */
	public V1Application(AbstractBinder binder) {
		packages(V1Application.class.getPackageName());
		register(AuthenticationFilter.class);
		register(V1ExceptionMapper.class);
		register(RolesAllowedDynamicFeature.class);
		register(LoggerFilter.class);
		register(V1Response.class);
		register(V1EntityProvider.class);
		register(ExportNegotiation.class);
		register(ExportFilter.class);
		register(CsvListWriter.class);
		register(XlsxListWriter.class);
		property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
		property(ServerProperties.APPLICATION_NAME, "Netshot API v1");
		property(ServerProperties.WADL_FEATURE_DISABLE, true);
		property(ServerProperties.LOCATION_HEADER_RELATIVE_URI_RESOLUTION_DISABLED, true);
		// Only the v1 provider reads and writes the bodies (no auto-discovered Jackson/JSON-B provider)
		property(ServerProperties.METAINF_SERVICES_LOOKUP_DISABLE, true);
		property(ServerProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
		property(ServerProperties.JSON_PROCESSING_FEATURE_DISABLE, true);
		property(ServerProperties.MOXY_JSON_FEATURE_DISABLE, true);
		register(binder);
		register(new AbstractBinder() {
			@Override
			protected void configure() {
				bindAsContract(DeviceRefs.class).in(Singleton.class);
			}
		});
	}
}
