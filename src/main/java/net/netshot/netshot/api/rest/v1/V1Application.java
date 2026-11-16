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

import java.util.List;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import jakarta.inject.Singleton;

import net.netshot.netshot.api.rest.AuthenticationFilter;
import net.netshot.netshot.api.rest.LoggerFilter;
import net.netshot.netshot.api.rest.v1.apitoken.ApiTokenMapper;
import net.netshot.netshot.api.rest.v1.auth.AuthMapper;
import net.netshot.netshot.api.rest.v1.compliance.ComplianceRefs;
import net.netshot.netshot.api.rest.v1.compliance.HardwareRuleMapper;
import net.netshot.netshot.api.rest.v1.compliance.PolicyMapper;
import net.netshot.netshot.api.rest.v1.compliance.RuleMapper;
import net.netshot.netshot.api.rest.v1.compliance.SoftwareRuleMapper;
import net.netshot.netshot.api.rest.v1.config.ConfigMapper;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetMapper;
import net.netshot.netshot.api.rest.v1.device.DeviceInventoryMapper;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.device.DeviceRefs;
import net.netshot.netshot.api.rest.v1.diagnostic.DiagnosticMapper;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.api.rest.v1.export.CsvListWriter;
import net.netshot.netshot.api.rest.v1.export.ExportFilter;
import net.netshot.netshot.api.rest.v1.export.ExportNegotiation;
import net.netshot.netshot.api.rest.v1.export.XlsxListWriter;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.api.rest.v1.hook.HookMapper;
import net.netshot.netshot.api.rest.v1.script.ScriptMapper;
import net.netshot.netshot.api.rest.v1.system.SystemMapper;
import net.netshot.netshot.api.rest.v1.task.TaskCreationMapper;
import net.netshot.netshot.api.rest.v1.task.TaskMapper;
import net.netshot.netshot.api.rest.v1.task.TaskResponses;
import net.netshot.netshot.api.rest.v1.user.UserMapper;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceMapper;

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
	 * @param links the paths of the v1 objects (built with the path the API is served on)
	 */
	public V1Application(AbstractBinder binder, V1Links links) {
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
				bind(links).to(V1Links.class);
				bindAsContract(ListParams.class).in(Singleton.class);
				// The mappers and the response helpers of the resources
				for (Class<?> helper : List.of(
					ApiTokenMapper.class, AuthMapper.class, ComplianceRefs.class, ConfigMapper.class, CredentialSetMapper.class,
					DeviceInventoryMapper.class, DeviceMapper.class, DiagnosticMapper.class, DomainMapper.class,
					GroupMapper.class, HardwareRuleMapper.class, HookMapper.class, PolicyMapper.class, RuleMapper.class,
					ScriptMapper.class, SoftwareRuleMapper.class, SystemMapper.class, TaskCreationMapper.class, TaskMapper.class,
					TaskResponses.class, UserMapper.class, VaultInstanceMapper.class, DeviceRefs.class)) {
					bindAsContract(helper).in(Singleton.class);
				}
			}
		});
	}
}
