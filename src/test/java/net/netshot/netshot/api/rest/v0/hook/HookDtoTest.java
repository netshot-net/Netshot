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
package net.netshot.netshot.api.rest.v0.hook;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.DtoContract;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger.TriggerType;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Checks that the hook DTOs and their mapper are a faithful replacement of the entities the REST
 * API used to read and write: same representation in JSON and XML (the URL of a hook is written as
 * it is, the hooks have no secret of their own), requests read like they used to be (defaults for
 * absent properties, absent or null triggers).
 */
class HookDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(HookDtoTest.class);

	private static HookTrigger trigger(String item) {
		HookTrigger trigger = new HookTrigger();
		trigger.setType(TriggerType.POST_TASK);
		trigger.setItem(item);
		return trigger;
	}

	private static WebHook webHook(long id, String name, String... items) {
		WebHook hook = new WebHook();
		hook.setId(id);
		hook.setName(name);
		hook.setEnabled(id % 2 == 0);
		hook.setAction(Action.POST_YAML);
		hook.setUrl("https://user:pass@example.net/hook?token=abc");
		hook.setHttpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA);
		hook.setHttpsCustomCaCertificate("-----BEGIN CERTIFICATE-----");
		Set<HookTrigger> triggers = new HashSet<>();
		for (String item : items) {
			triggers.add(trigger(item));
		}
		hook.setTriggers(triggers);
		return hook;
	}

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(Object dto) throws Exception {
		this.contract.checkBoth(json(dto), xml(dto));
	}

	@Test
	@DisplayName("A hook has the same JSON and XML representation as the entity, alone and in a list")
	void representation() throws Exception {
		WebHook full = webHook(2, "Full", "TakeSnapshotTask", "CheckComplianceTask");
		WebHook none = webHook(3, "No trigger");
		WebHook nullTriggers = webHook(4, "Null triggers");
		nullTriggers.setTriggers(null);
		WebHook bare = new WebHook();
		bare.setName("Bare");
		for (WebHook hook : List.of(full, none, nullTriggers, bare)) {
			this.assertRepresentation(HookMapper.toDto(hook));
		}
		// Make sure the check is meaningful
		String text = new String(json(HookMapper.toDto(full)));
		Assertions.assertTrue(text.contains("\"type\":\"Web\""), text);
		Assertions.assertTrue(text.contains("\"item\":\"TakeSnapshotTask\""), text);
		Assertions.assertTrue(text.contains("\"url\":\"https://user:pass@example.net/hook?token=abc\""), text);
		Assertions.assertTrue(new String(xml(HookMapper.toDto(full))).startsWith("<WebHook type=\"Web\">"));

		List<Hook> entities = new ArrayList<>(List.of(full, none));
		TypeReference<List<HookDto>> dtoType = new TypeReference<>() { };
		this.contract.checkBoth(
			JSON.writerFor(dtoType).writeValueAsBytes(HookMapper.toDtos(entities)),
			XML.writerFor(dtoType).writeValueAsBytes(HookMapper.toDtos(entities)));
		Assertions.assertEquals(ArrayList.class, HookMapper.toDtos(entities).getClass());
	}

	@Test
	@DisplayName("A sub list of hooks (a paginated query with the triggers fetched) is mapped to a sub list, which XML publishes as such, like it was")
	void subListQuirk() throws Exception {
		List<Hook> all = new ArrayList<>(List.of(webHook(2, "One", "A"), webHook(3, "Two", "B")));
		List<Hook> sub = all.subList(0, 2);
		List<HookDto> dtos = HookMapper.toDtos(sub);
		Assertions.assertEquals(sub.getClass(), dtos.getClass());
		Assertions.assertNotEquals(ArrayList.class, dtos.getClass());
		Assertions.assertEquals(2, dtos.size());
		Assertions.assertEquals("SubList", XML.writer().writeValueAsString(dtos).replaceAll("(?s)^<(\\w+).*$", "$1"));
		Assertions.assertEquals("SubList", XML.writer().writeValueAsString(sub).replaceAll("(?s)^<(\\w+).*$", "$1"));
	}

	// ------------------------------------------------------------------
	// Requests
	// ------------------------------------------------------------------

	private static Map<String, Object> describe(Object bean) throws IntrospectionException, ReflectiveOperationException {
		Map<String, Object> description = new TreeMap<>();
		for (PropertyDescriptor property : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
			if (property.getReadMethod() == null || "class".equals(property.getName())) {
				continue;
			}
			Object value;
			try {
				value = property.getReadMethod().invoke(bean);
			}
			catch (InvocationTargetException e) {
				continue;
			}
			if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
					|| value instanceof Enum || value instanceof Date) {
				description.put(property.getName(), value);
			}
			else if (value instanceof Set<?> set) {
				// The triggers (null if the set is null, a null element is described as such)
				Set<String> triggers = new TreeSet<>();
				for (Object element : set) {
					triggers.add(element instanceof HookTrigger t ? t.getType() + ":" + t.getItem() : String.valueOf(element));
				}
				description.put(property.getName(), triggers);
			}
		}
		return description;
	}

	private static final String BASE = "\"type\": \"Web\", \"name\": \"n\", \"action\": \"POST_JSON\", \"url\": \"https://example.net/h\"";

	private static final List<String> BODIES = List.of(
		"{\"type\": \"Web\", \"id\": 5, \"name\": \"full\", \"enabled\": false, \"action\": \"POST_XML\", \"url\": \"https://u\","
			+ " \"httpsCaTrustMode\": \"CUSTOM_CA\", \"httpsCustomCaCertificate\": \"PEM\","
			+ " \"triggers\": [{\"type\": \"POST_TASK\", \"item\": \"A\"}, {\"type\": \"POST_TASK\", \"item\": \"B\"}, {\"type\": \"POST_TASK\", \"item\": \"A\"}]}",
		"{\"type\": \"Web\", \"name\": \"min\"}",
		"{" + BASE + "}",
		// Defaults when absent, but not when null
		"{" + BASE + ", \"enabled\": null}",
		"{" + BASE + ", \"enabled\": false}",
		"{" + BASE + ", \"triggers\": null}",
		"{" + BASE + ", \"triggers\": []}",
		"{" + BASE + ", \"httpsCaTrustMode\": null}",
		"{" + BASE + ", \"action\": null, \"url\": null}",
		"{" + BASE + ", \"triggers\": [null]}",
		"{" + BASE + ", \"triggers\": [{}]}",
		"{" + BASE + ", \"triggers\": [{\"type\": \"POST_TASK\"}, {\"item\": \"A\"}]}",
		// Known properties of the entities which are ignored
		"{" + BASE + ", \"parsedUrl\": \"x\"}",
		"{" + BASE + ", \"triggers\": [{\"type\": \"POST_TASK\", \"item\": \"A\", \"hook\": {\"id\": 1}}]}",
		// Refused
		"{" + BASE + ", \"unknown\": 1}",
		"{" + BASE + ", \"triggers\": [{\"type\": \"POST_TASK\", \"item\": \"A\", \"unknown\": 1}]}",
		"{" + BASE + ", \"triggers\": [{\"type\": \"NOPE\", \"item\": \"A\"}]}",
		"{" + BASE + ", \"triggers\": {}}",
		"{\"type\": \"Web\", \"name\": \"n\", \"action\": \"NOPE\"}",
		"{" + BASE + ", \"enabled\": \"maybe\"}",
		"{\"type\": \"Other\", \"name\": \"n\"}",
		"{\"name\": \"n\"}",
		"[]",
		"\"text\"");

	@Test
	@DisplayName("The requests are read like the entities used to be (values, defaults, triggers, ignored and refused properties)")
	void requestsAreReadLikeTheEntity() throws Exception {
		int refused = 0;
		int index = 0;
		for (String body : BODIES) {
			index++;
			Hook actual = null;
			try {
				actual = HookMapper.toEntity(JSON.readerFor(HookRequest.class).readValue(body));
			}
			catch (JsonProcessingException e) {
				// Refused
			}
			this.contract.checkText("request-" + index, DtoContract.render(actual, actual == null ? null : describe(actual)));
			if (actual == null) {
				refused++;
			}
		}
		Assertions.assertTrue(refused >= 8, "the bodies which must be refused are refused");
	}

	@Test
	@DisplayName("A request has the defaults of the entity, and keeps the triggers (deduplicated) of the request")
	void requestDefaults() throws Exception {
		WebHook minimal = (WebHook) HookMapper.toEntity(
			JSON.readerFor(HookRequest.class).readValue("{\"type\": \"Web\", \"name\": \"n\"}"));
		Assertions.assertTrue(minimal.isEnabled());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, minimal.getHttpsCaTrustMode());
		Assertions.assertNotNull(minimal.getTriggers());
		Assertions.assertTrue(minimal.getTriggers().isEmpty());
		WebHook nullTriggers = (WebHook) HookMapper.toEntity(JSON.readerFor(HookRequest.class)
			.readValue("{\"type\": \"Web\", \"name\": \"n\", \"triggers\": null}"));
		Assertions.assertNull(nullTriggers.getTriggers());
		WebHook triggers = (WebHook) HookMapper.toEntity(JSON.readerFor(HookRequest.class).readValue(BODIES.get(0)));
		Assertions.assertEquals(2, triggers.getTriggers().size());
		Assertions.assertEquals(5L, triggers.getId());
	}
}
