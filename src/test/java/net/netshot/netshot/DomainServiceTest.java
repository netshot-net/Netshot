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

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainDefinition;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.inventory.domain.DomainService;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Tests of the domain use cases, against a real database.
 */
@TestInstance(Lifecycle.PER_CLASS)
class DomainServiceTest extends WithDatabaseTest {

	private Transactions transactions;
	private DomainService service;
	private int counter;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("domainservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		DeviceDriver.refreshDrivers();
		Database.update();
		Wiring.init();
		this.transactions = new Transactions();
		this.service = new DomainService(this.transactions, new DomainRepository());
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private String nextName() {
		return "Domain " + (this.counter++);
	}

	private static DomainDefinition definition(String name, String address) throws Exception {
		return new DomainDefinition(name, "Some description", new Network4Address(address));
	}

	@Test
	@DisplayName("create() stores the domain, trimming the name and the description")
	void create() throws Exception {
		String name = this.nextName();
		Domain created = this.service.create(new DomainDefinition("  " + name + " ", "  desc  ", new Network4Address("10.255.0.1")));
		Assertions.assertTrue(created.getId() > 0);
		Domain stored = this.transactions.read(session -> session.get(Domain.class, created.getId()));
		Assertions.assertEquals(name, stored.getName());
		Assertions.assertEquals("desc", stored.getDescription());
		Assertions.assertEquals("10.255.0.1", stored.getServer4Address().getIp());
		Assertions.assertNotNull(stored.getServer6Address(), "the IPv6 address of the server is the unspecified one");
	}

	@Test
	@DisplayName("create() refuses a blank name, before looking at the address")
	void createBlankName() throws Exception {
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN_NAME, () -> this.service.create(new DomainDefinition("  ", "d", null)));
	}

	@Test
	@DisplayName("create() refuses an address which isn't a normal unicast one")
	void createInvalidAddress() throws Exception {
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.service.create(definition(this.nextName(), "127.0.0.1")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.service.create(definition(this.nextName(), "224.0.0.1")));
	}

	@Test
	@DisplayName("create() refuses a name which is already used, and stores nothing")
	void createDuplicate() throws Exception {
		String name = this.nextName();
		this.service.create(definition(name, "10.255.1.1"));
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_DOMAIN, () -> this.service.create(definition(name, "10.255.1.2")));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("list() returns the domains by ID, and honours the page")
	void list() throws Exception {
		Domain first = this.service.create(definition(this.nextName(), "10.255.2.1"));
		Domain second = this.service.create(definition(this.nextName(), "10.255.2.2"));
		List<Domain> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 2);
		for (int i = 1; i < all.size(); i++) {
			Assertions.assertTrue(all.get(i - 1).getId() < all.get(i).getId());
		}
		Assertions.assertEquals(all.get(0).getId(), this.service.list(new Page(0, 1)).get(0).getId());
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new Page(1, null)).size());
		Assertions.assertTrue(all.stream().anyMatch(d -> d.getId() == first.getId()));
		Assertions.assertTrue(all.stream().anyMatch(d -> d.getId() == second.getId()));
	}

	@Test
	@DisplayName("update() changes the domain")
	void update() throws Exception {
		Domain created = this.service.create(definition(this.nextName(), "10.255.3.1"));
		String name = this.nextName();
		Domain updated = this.service.update(created.getId(), new DomainDefinition(" " + name + " ", " new ", new Network4Address("10.255.3.9")));
		Assertions.assertEquals(created.getId(), updated.getId());
		Domain stored = this.transactions.read(session -> session.get(Domain.class, created.getId()));
		Assertions.assertEquals(name, stored.getName());
		Assertions.assertEquals("new", stored.getDescription());
		Assertions.assertEquals("10.255.3.9", stored.getServer4Address().getIp());
	}

	@Test
	@DisplayName("update() checks the name, then the address, before looking for the domain")
	void updateValidation() throws Exception {
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN_NAME, () -> this.service.update(99999999L, definition("", "10.255.4.1")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.service.update(99999999L, definition("name", "0.0.0.0")));
		assertError(ErrorCode.NETSHOT_DOMAIN_NOT_FOUND, () -> this.service.update(99999999L, definition("name", "10.255.4.1")));
	}

	@Test
	@DisplayName("update() refuses a name which is used by another domain, and leaves the domain unchanged")
	void updateDuplicate() throws Exception {
		String name = this.nextName();
		this.service.create(definition(name, "10.255.5.1"));
		Domain other = this.service.create(definition(this.nextName(), "10.255.5.2"));
		assertError(ErrorCode.NETSHOT_DUPLICATE_DOMAIN, () -> this.service.update(other.getId(), definition(name, "10.255.5.3")));
		Domain stored = this.transactions.read(session -> session.get(Domain.class, other.getId()));
		Assertions.assertNotEquals(name, stored.getName());
		Assertions.assertEquals("10.255.5.2", stored.getServer4Address().getIp());
	}

	@Test
	@DisplayName("delete() removes the domain, and reports an unknown one")
	void delete() throws Exception {
		Domain created = this.service.create(definition(this.nextName(), "10.255.6.1"));
		this.service.delete(created.getId());
		Assertions.assertNull(this.transactions.read(session -> session.get(Domain.class, created.getId())));
		assertError(ErrorCode.NETSHOT_DOMAIN_NOT_FOUND, () -> this.service.delete(created.getId()));
	}

	@Test
	@DisplayName("delete() refuses a domain used by a device or a credential set, and keeps it")
	void deleteInUse() throws Exception {
		Domain withDevice = this.service.create(definition(this.nextName(), "10.255.7.1"));
		this.transactions.run(session -> session.persist(new Device("CiscoIOS12", "10.99.7.1", session.get(Domain.class, withDevice.getId()), "tester")));
		assertError(ErrorCode.NETSHOT_DOMAIN_IN_USE, () -> this.service.delete(withDevice.getId()));
		Assertions.assertNotNull(this.transactions.read(session -> session.get(Domain.class, withDevice.getId())));

		Domain withCredentials = this.service.create(definition(this.nextName(), "10.255.7.2"));
		this.transactions.run(session -> {
			DeviceSnmpv2cCommunity community = new DeviceSnmpv2cCommunity("public", "Community of a domain");
			community.setMgmtDomain(session.get(Domain.class, withCredentials.getId()));
			session.persist(community);
		});
		assertError(ErrorCode.NETSHOT_DOMAIN_IN_USE, () -> this.service.delete(withCredentials.getId()));
		Assertions.assertNotNull(this.transactions.read(session -> session.get(Domain.class, withCredentials.getId())));
	}
}
