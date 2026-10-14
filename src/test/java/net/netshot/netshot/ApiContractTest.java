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
package net.netshot.netshot;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.ApiContractCase.Reply;
import net.netshot.netshot.ApiContractGolden.Format;
import net.netshot.netshot.api.rest.v0.RestService;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.engine.TaskManager;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

/**
 * Golden file contract tests of the REST API: the wire format (JSON, XML,
 * YAML, plain text...) and HTTP status of every endpoint are recorded under
 * {@code src/test/resources/api-contract/} and must not change when the
 * implementation is refactored.
 *
 * <p>The server is booted against a fresh PostgreSQL schema, which is then
 * filled with a deterministic dataset (see {@link ApiContractSeed}). The cases
 * ({@link ApiContractCases}) run in order: first the read-only ones, then the
 * write ones, which change the dataset as they go. Run with
 * {@code -Dnetshot.contract.update=true} to (re)write the golden files instead of
 * asserting.</p>
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiContractTest extends WithDatabaseTest {

	private static final String API_URL = "http://localhost:8889/api";

	private final long startTime = System.currentTimeMillis();
	private TimeZone previousTimeZone;
	private Path binaryFolder;
	private HttpClient httpClient;
	private ApiContractSeed seed;
	/** Statuses recorded (update mode) or to check, by golden file name. */
	private final Map<String, String> recordedStatuses = new TreeMap<>();

	@BeforeAll
	void bootAndSeed() throws Exception {
		// Some outputs (e.g. download file names) format dates in the JVM time zone
		this.previousTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Properties config = getFreshDatabaseConfig("apicontracttest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.ssl.enabled", "false");
		URI uri = UriBuilder.fromUri(API_URL).replacePath("/").build();
		config.setProperty("netshot.http.baseurl", uri.toString());
		this.binaryFolder = Files.createTempDirectory("netshot-contract-");
		config.setProperty("netshot.snapshots.binary.path", this.binaryFolder.toString());
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		net.netshot.netshot.driver.DeviceDriver.refreshDrivers();
		Database.update();
		Database.init();
		TaskManager.init();
		// Tasks must be stored but never run: keep the schedulers in standby, and drop
		// any job left by another test class sharing the JVM.
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.standby();
			scheduler.clear();
		}
		RestService.init();
		Thread.sleep(1000);
		this.httpClient = HttpClient.newHttpClient();
		this.seed = new ApiContractSeed(API_URL, this.httpClient);
		this.seed.seed();
	}

	@AfterAll
	void shutdownSchedulers() throws Exception {
		TimeZone.setDefault(this.previousTimeZone);
		if (this.binaryFolder != null) {
			try (Stream<Path> files = Files.walk(this.binaryFolder)) {
				files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			}
		}
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.clear();
			// Other test classes may rely on a running scheduler.
			scheduler.start();
		}
	}

	@TestFactory
	List<DynamicTest> contractCases() {
		ApiContractGolden golden = new ApiContractGolden(this.startTime);
		boolean update = ApiContractGolden.isUpdateMode();
		java.util.Properties expectedStatuses = ApiContractGolden.readStatuses();
		List<DynamicTest> tests = new ArrayList<>();
		List<ApiContractCase> cases = ApiContractCases.all(this.seed);
		for (ApiContractCase contractCase : cases) {
			String fileName = contractCase.fileName();
			tests.add(DynamicTest.dynamicTest(fileName, () -> {
				Reply reply = contractCase.send(this.httpClient, API_URL, ApiContractSeed.ADMIN_TOKEN);
				String status = reply.status() + ";" + contentTypeBase(reply.contentType());
				Format format = contractCase.format();
				String actual = golden.canonicalize(format, reply.body(), contractCase.transform, contractCase.sortRoot);
				if (reply.contentDisposition() != null) {
					actual = "# Content-Disposition: "
						+ ApiContractGolden.normalizeHeader(reply.contentDisposition()) + "\n" + actual;
				}
				if (update) {
					ApiContractGolden.writeGolden(fileName, actual);
					this.recordedStatuses.put(fileName, status);
					return;
				}
				String expected = ApiContractGolden.readGolden(fileName);
				Assertions.assertNotNull(expected, "No golden file for case " + fileName
					+ " (run with -D" + ApiContractGolden.UPDATE_PROPERTY + "=true to create it)");
				Assertions.assertEquals(expectedStatuses.getProperty(fileName), status,
					"HTTP status/content type changed for case " + fileName);
				if (!expected.equals(actual)) {
					Assertions.fail("API contract broken for case " + fileName + ":\n"
						+ ApiContractGolden.diff(expected, actual, fileName));
				}
			}));
		}
		if (update) {
			// Persist the statuses once all cases have run.
			tests.add(DynamicTest.dynamicTest("(write statuses)", () -> {
				ApiContractGolden.writeStatuses(this.recordedStatuses);
			}));
		}
		return tests;
	}

	private static String contentTypeBase(String contentType) {
		int idx = contentType.indexOf(';');
		return (idx < 0 ? contentType : contentType.substring(0, idx)).trim();
	}
}
