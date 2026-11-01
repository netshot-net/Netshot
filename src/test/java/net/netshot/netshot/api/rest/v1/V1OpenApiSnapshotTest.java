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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.swagger.v3.core.util.Json;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.contract.ApiContractGolden;

/**
 * Keeps the snapshot of the OpenAPI document of the v1 API, which the web UI generates its API types from
 * ({@code npm run api:types}), equal to the document the server builds. Run with
 * {@code -Dnetshot.contract.update=true} to update the snapshot, then regenerate the types.
 */
@UnitTest
@DisplayName("v1 OpenAPI snapshot of the web UI")
public class V1OpenApiSnapshotTest {

	/** The snapshot, in the web UI sources. */
	static final Path SNAPSHOT = Path.of("src/main/resources/webui/src/api/openapi.json");

	@Test
	@DisplayName("The snapshot used by the web UI is the current OpenAPI document")
	void snapshotIsCurrent() throws Exception {
		V1Application application = new V1Application(new AbstractBinder() {
			@Override
			protected void configure() {
				// No service: the document is built from the resource classes only
			}
		});
		String document = Json.pretty().writeValueAsString(V1OpenApiResource.build(application)) + "\n";
		if (ApiContractGolden.isUpdateMode()) {
			Files.createDirectories(SNAPSHOT.getParent());
			Files.writeString(SNAPSHOT, document, StandardCharsets.UTF_8);
		}
		Assertions.assertTrue(Files.exists(SNAPSHOT), "No OpenAPI snapshot at " + SNAPSHOT
			+ " (run with -D" + ApiContractGolden.UPDATE_PROPERTY + "=true)");
		Assertions.assertEquals(Files.readString(SNAPSHOT, StandardCharsets.UTF_8), document,
			"The OpenAPI snapshot of the web UI is outdated: run with -D" + ApiContractGolden.UPDATE_PROPERTY
				+ "=true, then npm run api:types in src/main/resources/webui");
	}
}
