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
package net.netshot.netshot.testing;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.testing.api.NetshotApiClient;

/**
 * The REST server of an {@link ApiTest} or {@link ContractTest} class, which the test methods (including
 * {@code @BeforeAll} and {@code @BeforeEach}) can receive as a parameter.
 */
public final class TestServer {

	/** An ID which matches no object. */
	public static final long UNKNOWN_ID = 99999999;

	/** API tokens created before each test of an {@link ApiTest} class, by permission level. */
	public static final Map<Integer, String> API_TOKENS = Map.of(
		UiUser.LEVEL_READONLY, "mwAPEe0mQlBvKuUYYS5MiFsmnVRWZpca",
		UiUser.LEVEL_OPERATOR, "vMLKSPWMq9hFRtKkhXbvHhveXEn3Y4BZ",
		UiUser.LEVEL_READWRITE, "rCSrr3S3PGsLmi54HFw6a3PbZClpCivk",
		UiUser.LEVEL_EXECUTEREADWRITE, "PXlJ4r8VvTObn6hyknI1lwOoxEPJjCDO",
		UiUser.LEVEL_ADMIN, "hLXg3ABu1qWJPle3Z3Z22X0aLkx5T354"
	);

	private final String apiUrl;

	/**
	 * Creates the server description.
	 *
	 * @param apiUrl the URL of the REST API
	 */
	TestServer(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	/**
	 * Gets the URL of the REST API.
	 *
	 * @return the URL, such as {@code http://localhost:12345/api}
	 */
	public String apiUrl() {
		return this.apiUrl;
	}

	/**
	 * Gets the base URL of the server (the API URL without its path), as set in the configuration.
	 *
	 * @return the base URL
	 */
	String baseUrl() {
		return UriBuilder.fromUri(this.apiUrl).replacePath("/").build().toString();
	}

	/**
	 * Gets the URI of the API, to check that the server listens.
	 *
	 * @return the URI
	 */
	URI apiUri() {
		return URI.create(this.apiUrl);
	}

	/**
	 * Creates an API client authenticated with the admin token.
	 *
	 * @return the client
	 * @throws URISyntaxException never, the URL being valid
	 */
	public NetshotApiClient client() throws URISyntaxException {
		return this.client(UiUser.LEVEL_ADMIN);
	}

	/**
	 * Creates an API client authenticated with the token of a permission level.
	 *
	 * @param level the permission level ({@code UiUser.LEVEL_*})
	 * @return the client
	 * @throws URISyntaxException never, the URL being valid
	 */
	public NetshotApiClient client(int level) throws URISyntaxException {
		return new NetshotApiClient(this.apiUrl, API_TOKENS.get(level));
	}
}
