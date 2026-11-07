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
package net.netshot.netshot.api.rest.v0;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonXmlBindJsonProvider;
import com.fasterxml.jackson.jakarta.rs.xml.JacksonXmlBindXMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.JacksonXmlBindYAMLProvider;

import net.netshot.netshot.api.rest.AuthenticationFilter;
import net.netshot.netshot.api.rest.LoggerFilter;

/**
 * The Jersey application of the legacy (v0) REST API, served on the API path ({@code /api}), beside the v1 one
 * ({@code net.netshot.netshot.api.rest.v1.V1Application}). It can be disabled ({@code netshot.http.api.v0.enabled}).
 * Its OpenAPI document is served by {@link V0OpenApiResource}.
 */
public class V0Application extends ResourceConfig {

	/**
	 * Instantiates the web application.
	 *
	 * @param binder the dependency injection binder exposing the services (transactions, feature services)
	 *        to the REST resources; given by the composition root ({@code boot.Wiring}), so that the
	 *        REST channel does not depend on {@code boot}
	 */
	public V0Application(AbstractBinder binder) {
		// Resources (@Path classes) are discovered by scanning the package of the legacy API (v0) and its
		// sub-packages. Filters, providers and the exception mapper are not
		// annotated with @Provider and thus registered explicitly below, so that they cannot be
		// registered twice and their registration is not subject to the scanning.
		packages(V0Application.class.getPackageName());
		register(AuthenticationFilter.class);
		register(NetshotExceptionMapper.class);
		register(RolesAllowedDynamicFeature.class);
		property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
		property(ServerProperties.APPLICATION_NAME, "Netshot");
		property(ServerProperties.WADL_FEATURE_DISABLE, true);
		register(LoggerFilter.class);
		register(ResponseCodeFilter.class);
		// property(ServerProperties.TRACING, "ALL");

		// Dependency injection: the objects of the composition root, injectable into the resources
		register(binder);

		// JSON
		JacksonXmlBindJsonProvider jsonProvider = new JacksonXmlBindJsonProvider();
		jsonProvider.setMapper(JsonMapper.builder()
			.build());
		register(jsonProvider);

		// XML
		JacksonXmlBindXMLProvider xmlProvider = new JacksonXmlBindXMLProvider();
		xmlProvider.setMapper(XmlMapper.builder()
			.build());
		register(xmlProvider);

		// YAML
		JacksonXmlBindYAMLProvider yamlProvider = new JacksonXmlBindYAMLProvider();
		yamlProvider.setMapper(YAMLMapper.builder()
			.build());
		register(yamlProvider);
	}
}
