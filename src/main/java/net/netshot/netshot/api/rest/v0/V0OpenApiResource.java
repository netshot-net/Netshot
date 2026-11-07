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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.netshot.netshot.api.rest.ApiTokenAuthenticator;
import net.netshot.netshot.api.rest.RestServer;

/**
 * The OpenAPI document of the legacy (v0) API ({@code /api/openapi.json} and {@code .yaml}, or
 * {@code /api/openapi} in the format of the {@code Accept} header), built once from the v0 resources. It is
 * public.
 */
@Path("/")
@Hidden
@Singleton
public class V0OpenApiResource {

	/** The YAML media type. */
	private static final String APPLICATION_YAML = "application/yaml";

	/** The document, built on the first request. */
	private volatile OpenAPI document;

	@Context
	private Application application;

	private OpenAPI document() {
		OpenAPI current = this.document;
		if (current == null) {
			synchronized (this) {
				current = this.document;
				if (current == null) {
					current = build(this.application);
					this.document = current;
				}
			}
		}
		return current;
	}

	/**
	 * Builds the document.
	 *
	 * @param application the v0 application
	 * @return the document
	 */
	static OpenAPI build(Application application) {
		OpenAPI base = new OpenAPI()
			.info(new Info()
				.title("Netshot API")
				.version("v0")
				.description("Network Infrastructure Configuration and Compliance Management Software")
				.contact(new Contact().email("contact@netshot.net"))
				.license(new License().name("GPLv3").url("https://www.gnu.org/licenses/gpl-3.0.txt")))
			.servers(List.of(new Server().url(RestServer.apiPath())))
			.components(new Components()
				.addSecuritySchemes("ApiTokenAuth", new SecurityScheme()
					.name(ApiTokenAuthenticator.HTTP_API_TOKEN_HEADER)
					.type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER))
				.addSecuritySchemes("BearerAuth", new SecurityScheme()
					.type(SecurityScheme.Type.HTTP).scheme("bearer"))
				.addSecuritySchemes("CookieAuth", new SecurityScheme()
					.name(RestServer.sessionCookieName())
					.type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)))
			.addSecurityItem(new SecurityRequirement().addList("ApiTokenAuth"))
			.addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
			.addSecurityItem(new SecurityRequirement().addList("CookieAuth"));
		Set<Class<?>> resources = application.getClasses().stream()
			.filter(c -> c.isAnnotationPresent(Path.class) && !c.isAnnotationPresent(Hidden.class))
			.collect(Collectors.toSet());
		Reader reader = new Reader(new SwaggerConfiguration().openAPI(base).prettyPrint(true));
		return reader.read(resources);
	}

	/**
	 * Gets the OpenAPI document in JSON or YAML.
	 *
	 * @param type {@code json} or {@code yaml}
	 * @return the document
	 * @throws Exception if the document can't be written
	 */
	@GET
	@Path("/openapi.{type:json|yaml}")
	@Produces({MediaType.APPLICATION_JSON, APPLICATION_YAML})
	public Response getDocument(@PathParam("type") String type) throws Exception {
		if ("yaml".equals(type)) {
			return Response.ok(Yaml.pretty().writeValueAsString(this.document()), APPLICATION_YAML).build();
		}
		return Response.ok(Json.pretty().writeValueAsString(this.document()), MediaType.APPLICATION_JSON).build();
	}

	/**
	 * Gets the OpenAPI document in the format of the {@code Accept} header (JSON by default).
	 *
	 * @param headers the request headers
	 * @return the document
	 * @throws Exception if the document can't be written
	 */
	@GET
	@Path("/openapi")
	@Produces({MediaType.APPLICATION_JSON, APPLICATION_YAML})
	public Response getDocumentByAccept(@Context HttpHeaders headers) throws Exception {
		boolean yaml = headers.getAcceptableMediaTypes().stream()
			.anyMatch(t -> t.isCompatible(MediaType.valueOf(APPLICATION_YAML)) && !t.isWildcardType());
		return this.getDocument(yaml ? "yaml" : "json");
	}
}
