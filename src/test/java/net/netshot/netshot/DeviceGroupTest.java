package net.netshot.netshot;

import java.util.List;
import java.util.Properties;

import org.hibernate.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.device.DynamicDeviceGroup;
import net.netshot.netshot.device.StaticDeviceGroup;

public class DeviceGroupTest extends WithDatabaseTest {

	protected static Properties getNetshotConfig() {
		Properties config = getDatabaseConfig("devicegrouptest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		return config;
	}

	@BeforeAll
	protected static void initNetshot() throws Exception {
		Netshot.initConfig(DeviceGroupTest.getNetshotConfig());
		Database.update();
		Database.init();
	}

	private static List<Long> members(long groupId) {
		try (Session session = Database.getSession()) {
			return session
				.createQuery("select m.key.device.id from DeviceGroupMembership m where m.key.group.id = :group "
					+ "order by m.key.device.id", Long.class)
				.setParameter("group", groupId)
				.list();
		}
	}

	@Test
	@DisplayName("Refreshing the groups of one device keeps its static memberships and recomputes the dynamic ones (#413)")
	void refreshGroupsOfOneDeviceKeepsStaticMemberships() throws Exception {
		Device routerA;
		Device routerB;
		StaticDeviceGroup fixed;
		DynamicDeviceGroup matching;
		DynamicDeviceGroup notMatching;
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			session.persist(domain);
			routerA = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
			routerA.setName("router-a");
			session.persist(routerA);
			routerB = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2);
			routerB.setName("router-b");
			session.persist(routerB);
			fixed = new StaticDeviceGroup("fixed");
			fixed.addDevice(routerA);
			fixed.addDevice(routerB);
			session.persist(fixed);
			matching = new DynamicDeviceGroup("matching", null, "[Name] is \"router-a\"");
			session.persist(matching);
			notMatching = new DynamicDeviceGroup("not matching", null, "[Name] is \"router-b\"");
			session.persist(notMatching);
			matching.refreshCache(session);
			notMatching.refreshCache(session);
			session.getTransaction().commit();
		}
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			// Stale membership, which the refresh must remove
			session
				.createMutationQuery("insert into DeviceGroupMembership(key.device, key.group, position) "
					+ "select d, g, 0 from Device d, DynamicDeviceGroup g where d.id = :device and g.id = :group")
				.setParameter("device", routerA.getId())
				.setParameter("group", notMatching.getId())
				.executeUpdate();
			DynamicDeviceGroup.refreshAllGroupsOfOneDevice(session, routerA.getId());
			session.getTransaction().commit();
		}
		Assertions.assertEquals(List.of(routerA.getId(), routerB.getId()), members(fixed.getId()));
		Assertions.assertEquals(List.of(routerA.getId()), members(matching.getId()));
		Assertions.assertEquals(List.of(routerB.getId()), members(notMatching.getId()));
	}
}
