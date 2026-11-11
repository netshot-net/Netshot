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
package net.netshot.netshot.api.hook;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestInstance;

import com.sun.net.httpserver.HttpServer;

import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.task.event.TaskFinished;
import net.netshot.netshot.testing.ContractTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.contract.Golden;
import net.netshot.netshot.testing.contract.Golden.Format;

/**
 * Golden file tests of the web hook payloads: the body posted for each task type that can trigger
 * a hook (the only trigger is POST_TASK, and the payload is the task), in JSON, XML and YAML.
 * Golden files are stored under {@code src/test/resources/hook-contract/} and must not change when
 * the implementation is refactored.
 *
 * <p>The dataset is the one of {@link HookTaskSeed}. A real web hook of each format, pointing
 * at a local HTTP receiver, is triggered by each task type; the task is then announced the way
 * {@code TaskJob} does ({@link TaskFinished}), and the body each hook posted is normalized like
 * the REST API contract responses. Run with {@code -Dnetshot.hook.contract.update=true} to
 * (re)write the golden files.</p>
 */
@ContractTest
@TestInstance(Lifecycle.PER_CLASS)
public class WebHookContractTest {

	/** System property, set to true to rewrite the golden files. */
	static final String UPDATE_PROPERTY = "netshot.hook.contract.update";

	/** Directory holding the golden files. */
	static final java.nio.file.Path GOLDEN_DIR = java.nio.file.Path.of("src", "test", "resources", "hook-contract");

	private static final Map<WebHook.Action, Format> FORMATS = new LinkedHashMap<>();

	static {
		FORMATS.put(WebHook.Action.POST_JSON, Format.JSON);
		FORMATS.put(WebHook.Action.POST_XML, Format.XML);
		FORMATS.put(WebHook.Action.POST_YAML, Format.YAML);
	}

	/**
	 * A request got by the receiver.
	 * @param path the request path
	 * @param contentType the Content-Type header
	 * @param body the raw body
	 */
	private record Received(String path, String contentType, byte[] body) {
	}

	/** When the test started (the dates near it are normalized in the outputs). */
	private final long startTime = System.currentTimeMillis();

	/** Task IDs by case name. */
	private Map<String, Long> taskIds;

	private HttpServer receiver;
	private final List<Received> received = new ArrayList<>();

	@BeforeAll
	void seedAndStartReceiver(TestServer server) throws Exception {
		this.taskIds = HookTaskSeed.seed(server.apiUrl());
		this.receiver = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.receiver.createContext("/", exchange -> {
			Received request = new Received(exchange.getRequestURI().getPath(),
				exchange.getRequestHeaders().getFirst("Content-Type"), exchange.getRequestBody().readAllBytes());
			synchronized (this.received) {
				this.received.add(request);
			}
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		this.receiver.start();

		// Only the hooks of this test: the hooks of the seed point to the outside
		for (Hook hook : TestWiring.get().hookService().list(Page.ALL)) {
			TestWiring.get().hookService().delete(hook.id());
		}
		for (WebHook.Action action : FORMATS.keySet()) {
			Set<HookTrigger> triggers = new HashSet<>();
			for (String type : this.taskIds.keySet().stream().map(HookTaskSeed::taskType).distinct().toList()) {
				triggers.add(new HookTrigger(HookTrigger.TriggerType.POST_TASK, type));
			}
			TestWiring.get().hookService().create(new WebHook(0, 0, null, "test-" + action, true, triggers, action,
				"http://127.0.0.1:" + this.receiver.getAddress().getPort() + "/" + action, HttpsCaTrustMode.SYSTEM_TRUSTSTORE, null));
		}
	}

	@AfterAll
	void stopReceiver() {
		if (this.receiver != null) {
			this.receiver.stop(0);
		}
	}

	/**
	 * Announces that a task has finished, as {@code TaskJob} does.
	 * @param caseName the name of the case, which gives the task type
	 * @param taskId the ID of the finished task
	 * @return the canonical payload posted by each hook, by format
	 */
	private Map<Format, String> payloads(String caseName, long taskId) {
		synchronized (this.received) {
			this.received.clear();
		}
		TestWiring.get().domainEvents().publish(new TaskFinished(taskId, HookTaskSeed.taskType(caseName)));
		Map<Format, String> payloads = new LinkedHashMap<>();
		synchronized (this.received) {
			Assertions.assertEquals(FORMATS.size(), this.received.size(), "Number of hook requests for " + caseName);
			for (Map.Entry<WebHook.Action, Format> action : FORMATS.entrySet()) {
				Received request = this.received.stream().filter(r -> r.path().equals("/" + action.getKey())).findFirst().orElseThrow();
				String canonical = new Golden(this.startTime)
					.canonicalize(action.getValue(), request.body(), Golden.Transform.NONE, false);
				payloads.put(action.getValue(), "# Content-Type: " + request.contentType() + "\n" + canonical);
			}
		}
		return payloads;
	}

	@TestFactory
	List<DynamicTest> hookPayloads() {
		boolean update = Boolean.parseBoolean(System.getProperty(UPDATE_PROPERTY, "false"));
		List<DynamicTest> tests = new ArrayList<>();
		for (Map.Entry<String, Long> task : this.taskIds.entrySet()) {
			// The hooks of the three formats are triggered at once
			AtomicReference<Map<Format, String>> cache = new AtomicReference<>();
			for (Format format : FORMATS.values()) {
				String fileName = "POST_TASK-" + task.getKey() + "." + format.extension;
				tests.add(DynamicTest.dynamicTest(fileName, () -> {
					Map<Format, String> payloads = cache.updateAndGet(known -> known != null ? known : this.payloads(task.getKey(), task.getValue()));
					String actual = payloads.get(format);
					if (update) {
						Golden.writeGolden(GOLDEN_DIR, fileName, actual);
						return;
					}
					String expected = Golden.readGolden(GOLDEN_DIR, fileName);
					Assertions.assertNotNull(expected, "No golden file " + fileName
						+ " (run with -D" + UPDATE_PROPERTY + "=true to create it)");
					if (!expected.equals(actual)) {
						Assertions.fail("Hook payload changed for " + fileName + ":\n"
							+ Golden.diff(expected, actual, fileName));
					}
				}));
			}
		}
		return tests;
	}
}
