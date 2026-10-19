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
package net.netshot.netshot.api.hook;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.sun.net.httpserver.HttpServer;

import net.netshot.netshot.ApiContractGolden.Format;
import net.netshot.netshot.ApiContractGolden;
import net.netshot.netshot.HookSeededTest;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.task.event.TaskFinished;

/**
 * Golden file tests of the web hook payloads: the body posted for each task type that can trigger
 * a hook (the only trigger is POST_TASK, and the payload is the task), in JSON, XML and YAML.
 * Golden files are stored under {@code src/test/resources/hook-contract/} and must not change when
 * the implementation is refactored.
 *
 * <p>The dataset is the one of {@link HookSeededTest}. A real web hook of each format, pointing
 * at a local HTTP receiver, is triggered by each task type; the task is then announced the way
 * {@code TaskJob} does ({@link TaskFinished}), and the body each hook posted is normalized like
 * the REST API contract responses. Run with {@code -Dnetshot.hook.contract.update=true} to
 * (re)write the golden files.</p>
 */
public class WebHookContractTest extends HookSeededTest {

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

	private HttpServer receiver;
	private final List<Received> received = new ArrayList<>();

	@Override
	protected String databaseName() {
		return "webhookcontracttest";
	}

	@BeforeAll
	void startReceiver() throws Exception {
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
		for (Hook hook : Wiring.get().hookService().list(Page.ALL)) {
			Wiring.get().hookService().delete(hook.getId());
		}
		for (WebHook.Action action : FORMATS.keySet()) {
			WebHook hook = new WebHook();
			hook.setName("test-" + action);
			hook.setAction(action);
			hook.setUrl("http://127.0.0.1:" + this.receiver.getAddress().getPort() + "/" + action);
			for (String type : this.taskIds.keySet().stream().map(HookSeededTest::taskType).distinct().toList()) {
				HookTrigger trigger = new HookTrigger();
				trigger.setType(HookTrigger.TriggerType.POST_TASK);
				trigger.setItem(type);
				hook.getTriggers().add(trigger);
			}
			Wiring.get().hookService().create(hook);
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
		Wiring.get().domainEvents().publish(new TaskFinished(taskId, taskType(caseName)));
		Map<Format, String> payloads = new LinkedHashMap<>();
		synchronized (this.received) {
			Assertions.assertEquals(FORMATS.size(), this.received.size(), "Number of hook requests for " + caseName);
			for (Map.Entry<WebHook.Action, Format> action : FORMATS.entrySet()) {
				Received request = this.received.stream().filter(r -> r.path().equals("/" + action.getKey())).findFirst().orElseThrow();
				String canonical = new ApiContractGolden(this.startTime)
					.canonicalize(action.getValue(), request.body(), ApiContractGolden.Transform.NONE, false);
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
						ApiContractGolden.writeGolden(GOLDEN_DIR, fileName, actual);
						return;
					}
					String expected = ApiContractGolden.readGolden(GOLDEN_DIR, fileName);
					Assertions.assertNotNull(expected, "No golden file " + fileName
						+ " (run with -D" + UPDATE_PROPERTY + "=true to create it)");
					if (!expected.equals(actual)) {
						Assertions.fail("Hook payload changed for " + fileName + ":\n"
							+ ApiContractGolden.diff(expected, actual, fileName));
					}
				}));
			}
		}
		return tests;
	}
}
