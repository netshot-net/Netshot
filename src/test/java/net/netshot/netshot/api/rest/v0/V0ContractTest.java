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

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import net.netshot.netshot.testing.ContractTest;
import net.netshot.netshot.testing.api.v0.V0Enabled;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.v0.V0ContractCase.Reply;
import net.netshot.netshot.testing.api.v0.V0ContractCase;
import net.netshot.netshot.testing.api.v0.V0ContractCases;
import net.netshot.netshot.testing.api.v0.V0ContractGolden;
import net.netshot.netshot.testing.contract.Golden.Format;
import net.netshot.netshot.testing.contract.Golden;
import net.netshot.netshot.testing.contract.ContractSeed;

/**
 * Golden file contract tests of the REST API: the wire format (JSON, XML,
 * YAML, plain text...) and HTTP status of every endpoint are recorded under
 * {@code src/test/resources/v0-contract/} and must not change when the
 * implementation is refactored.
 *
 * <p>The server is booted against a fresh PostgreSQL schema, which is then
 * filled with a deterministic dataset (see {@link ContractSeed}). The cases
 * ({@link V0ContractCases}) run in order: first the read-only ones, then the
 * write ones, which change the dataset as they go. Run with
 * {@code -Dnetshot.contract.update=true} to (re)write the golden files instead of
 * asserting.</p>
 */
@ContractTest
@V0Enabled
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("v0 contract (golden files)")
class V0ContractTest {

	private String apiUrl;
	private final long startTime = System.currentTimeMillis();
	private HttpClient httpClient;
	private ContractSeed seed;
	/** Statuses recorded (update mode) or to check, by golden file name. */
	private final Map<String, String> recordedStatuses = new TreeMap<>();

	@BeforeAll
	void bootAndSeed(TestServer server) throws Exception {
		this.apiUrl = server.apiUrl();
		this.httpClient = HttpClient.newHttpClient();
		this.seed = new ContractSeed();
		this.seed.seed();
	}

	@TestFactory
	List<DynamicTest> contractCases() {
		Golden golden = new Golden(this.startTime);
		boolean update = Golden.isUpdateMode();
		java.util.Properties expectedStatuses = V0ContractGolden.readStatuses();
		List<DynamicTest> tests = new ArrayList<>();
		List<V0ContractCase> cases = V0ContractCases.all(this.seed);
		for (V0ContractCase contractCase : cases) {
			String fileName = contractCase.fileName();
			tests.add(DynamicTest.dynamicTest(fileName, () -> {
				Reply reply = contractCase.send(this.httpClient, this.apiUrl, ContractSeed.ADMIN_TOKEN);
				String status = reply.status() + ";" + contentTypeBase(reply.contentType());
				Format format = contractCase.format();
				String actual = golden.canonicalize(format, reply.body(), contractCase.transform, contractCase.sortRoot);
				if (reply.contentDisposition() != null) {
					actual = "# Content-Disposition: "
						+ Golden.normalizeHeader(reply.contentDisposition()) + "\n" + actual;
				}
				if (update) {
					V0ContractGolden.write(fileName, actual);
					this.recordedStatuses.put(fileName, status);
					return;
				}
				String expected = V0ContractGolden.read(fileName);
				Assertions.assertNotNull(expected, "No golden file for case " + fileName
					+ " (run with -D" + Golden.UPDATE_PROPERTY + "=true to create it)");
				Assertions.assertEquals(expectedStatuses.getProperty(fileName), status,
					"HTTP status/content type changed for case " + fileName);
				if (!expected.equals(actual)) {
					Assertions.fail("API contract broken for case " + fileName + ":\n"
						+ Golden.diff(expected, actual, fileName));
				}
			}));
		}
		if (update) {
			// Persist the statuses once all cases have run.
			tests.add(DynamicTest.dynamicTest("(write statuses)", () -> {
				V0ContractGolden.writeStatuses(this.recordedStatuses);
			}));
		}
		return tests;
	}

	private static String contentTypeBase(String contentType) {
		int idx = contentType.indexOf(';');
		return (idx < 0 ? contentType : contentType.substring(0, idx)).trim();
	}
}
