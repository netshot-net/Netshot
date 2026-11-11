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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonXmlBindJsonProvider;
import com.fasterxml.jackson.jakarta.rs.xml.JacksonXmlBindXMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.JacksonXmlBindYAMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.YAMLMediaTypes;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import net.netshot.netshot.api.hook.payload.TaskPayload;
import net.netshot.netshot.api.hook.payload.TaskPayloads;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.ContractTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.contract.Golden;
import net.netshot.netshot.testing.contract.Golden.Format;
import net.netshot.netshot.testing.contract.DtoContract;

/**
 * Tests of the web hook payloads: the payload DTOs ({@link TaskPayload}) are written exactly like
 * the task entity was with the hook view, which is what the web hooks used to post, for each task
 * type and each format. The expected files were generated from the entity, and the payloads are
 * written by the same providers the web hook sender uses.
 */
@ContractTest
@TestInstance(Lifecycle.PER_CLASS)
class HookPayloadTest {

	/** When the test started (the dates near it are normalized in the outputs). */
	private final long startTime = System.currentTimeMillis();

	/** Task IDs by case name. */
	private Map<String, Long> taskIds;

	private final DtoContract contract = new DtoContract(HookPayloadTest.class, this.startTime);

	@BeforeAll
	void seed() throws Exception {
		this.taskIds = HookTaskSeed.seed();
	}

	/** Writes an object like the web hook sender does. */
	private interface BodyWriter {
		byte[] write(Object value) throws IOException;
	}

	private static BodyWriter writerFor(Format format) {
		return switch (format) {
			case JSON -> {
				JacksonXmlBindJsonProvider provider = new JacksonXmlBindJsonProvider();
				provider.setMapper(JsonMapper.builder().build());
				yield value -> write(provider, value, MediaType.APPLICATION_JSON_TYPE);
			}
			case XML -> {
				JacksonXmlBindXMLProvider provider = new JacksonXmlBindXMLProvider();
				provider.setMapper(XmlMapper.builder().build());
				yield value -> write(provider, value, MediaType.APPLICATION_XML_TYPE);
			}
			case YAML -> {
				JacksonXmlBindYAMLProvider provider = new JacksonXmlBindYAMLProvider();
				provider.setMapper(WebHookSender.yamlMapper());
				yield value -> write(provider, value, YAMLMediaTypes.APPLICATION_JACKSON_YAML_TYPE);
			}
			default -> throw new IllegalArgumentException(format.name());
		};
	}

	private static byte[] write(com.fasterxml.jackson.jakarta.rs.base.ProviderBase<?, ?, ?, ?> provider, Object value, MediaType type)
		throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		provider.writeTo(value, value.getClass(), value.getClass(), new Annotation[0], type, new MultivaluedHashMap<>(), out);
		return out.toByteArray();
	}

	private String canonical(Format format, byte[] body) {
		return new Golden(this.startTime).canonicalize(format, body, Golden.Transform.NONE, false);
	}

	private static final Format[] FORMATS = { Format.JSON, Format.XML, Format.YAML };

	/**
	 * Every task of the dataset (all the task types, and a few variants) is posted as the stored file says (what the task entity used to write).
	 * @return one test per task
	 */
	@TestFactory
	List<DynamicTest> payloadsAreWrittenAsExpected() {
		List<DynamicTest> tests = new ArrayList<>();
		for (Map.Entry<String, Long> task : this.taskIds.entrySet()) {
			for (Format format : FORMATS) {
				tests.add(DynamicTest.dynamicTest(task.getKey() + "." + format.extension, () -> {
					BodyWriter writer = writerFor(format);
					TaskPayload payload = TestWiring.get().taskService().mapTask(task.getValue(),
						(mapped, devices) -> TaskPayloads.toPayload(TestDrivers.registry(), mapped, devices));
					this.contract.checkCanonical(task.getKey(), format, this.canonical(format, writer.write(payload)));
				}));
			}
		}
		return tests;
	}

	/** Every concrete type of task has a payload (the sealed family follows the task types). */
	@Test
	void everyTaskTypeHasAPayload() {
		List<String> covered = this.taskIds.keySet().stream().map(HookTaskSeed::taskType).distinct().sorted().toList();
		List<String> payloads = new ArrayList<>();
		for (Class<?> payload : TaskPayload.class.getPermittedSubclasses()) {
			payloads.add(payload.getSimpleName() + "Task");
		}
		Assertions.assertEquals(covered, payloads.stream().sorted().toList());
	}

	/**
	 * The device nested in a payload, with every kind of credential set (and the secrets they hold),
	 * a management domain, and an end of life module: written as the entity was, without the secrets.
	 */
	@Test
	void devicesWithEveryKindOfCredentialSet() throws Exception {
		Domain domain = Domain.builder().name("lab").description("The lab").server4Address(new Network4Address("10.0.0.1"))
			.server6Address(new Network6Address("2001:db8::1")).build();
		List<DeviceCredentialSet> credentialSets = new ArrayList<>();
		credentialSets.add(DeviceSnmpv1Community.builder().community(Secret.local("zsecret1")).name("v1").build());
		credentialSets.add(DeviceSnmpv2cCommunity.builder().community(Secret.local("zsecret2")).name("v2c").build());
		credentialSets.add(DeviceSnmpv3Community.builder().community(Secret.local("zsecret3")).name("v3").username(Secret.local("user"))
			.authType(DeviceSnmpv3Community.AuthProtocol.SHA).authKey(Secret.local("zauthsecret"))
			.privType(DeviceSnmpv3Community.PrivProtocol.AES128).privKey(Secret.local("zprivsecret")).build());
		credentialSets.add(DeviceSshAccount.builder().username(Secret.local("user")).password(Secret.local("zpassword")).superPassword(Secret.local("zsuper")).name("ssh").build());
		credentialSets.add(DeviceSshKeyAccount.builder().username(Secret.local("user")).privateKey(Secret.local("zkey")).password(Secret.local("zpassphrase"))
			.superPassword(Secret.local("zsuper")).name("sshkey").build());
		credentialSets.add(DeviceTelnetAccount.builder().username(Secret.local("user")).password(Secret.local("zpassword")).superPassword(Secret.local("zsuper")).name("telnet").build());
		credentialSets.add(DeviceHttpAccount.builder().username(Secret.local("user")).password(Secret.local("zpassword")).name("http").build());
		List<DeviceAccess> accesses = new ArrayList<>();
		int index = 0;
		for (DeviceCredentialSet credentialSet : credentialSets) {
			DeviceCredentialSet inDomain = credentialSet.withMgmtDomain(index % 2 == 0 ? domain : null);
			DeviceAccess.DeviceAccessBuilder access = DeviceAccess.of("access" + index++).toBuilder();
			accesses.add(access.address("192.0.2." + index).port(1000 + index).globalCredentialSet(inDomain).build());
		}
		Device device = Device.create("CiscoIOS12", "192.0.2.50", domain, "tester").toBuilder()
			.name("lab-router")
			.eolDate(Instant.ofEpochMilli(1000))
			.eolModule(Module.builder().slot("1").partNumber("PART").serialNumber("SERIAL").build())
			.eosModule(Module.builder().slot("2").partNumber("PART2").serialNumber("SERIAL2").build())
			.accesses(accesses)
			.build();
		Task task = TakeSnapshotTask.of(device, "Snapshot", "tester", true, false, true);
		String secrets = "zsecret1 zsecret2 zsecret3 zauthsecret zprivsecret zpassword zsuper zkey zpassphrase";
		for (Format format : FORMATS) {
			BodyWriter writer = writerFor(format);
			String actual = this.canonical(format, writer.write(TaskPayloads.toPayload(TestDrivers.registry(), task, _ -> device)));
			// What the entity used to write, with the secrets masked in it
			this.contract.checkCanonical("devicesWithEveryKindOfCredentialSet", format, actual);
			for (String secret : secrets.split(" ")) {
				Assertions.assertFalse(actual.contains(secret), "Secret " + secret + " in " + format);
			}
		}
	}
}
