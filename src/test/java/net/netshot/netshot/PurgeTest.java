package net.netshot.netshot;

import java.util.Calendar;
import java.util.Properties;

import org.hibernate.Session;

import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.device.Module;
import net.netshot.netshot.device.StaticDeviceGroup;
import net.netshot.netshot.device.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.work.Task;
import net.netshot.netshot.work.tasks.PurgeDatabaseTask;
import net.netshot.netshot.work.tasks.TakeSnapshotTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

public class PurgeTest extends WithDatabaseTest {
	static final int DEVICES = 10;
	static final int CONFIGS_PER_DAY = 4;
	static final int DAYS = 30;
	static final String GROUP1_NAME = "group1";
	static final int DEVICES_IN_GROUP1 = 3;
	static final int DEVICES_WITH_BIG_CONFIG = 2;

	protected static Properties getNetshotConfig() {
		Properties config = getDatabaseConfig("purgetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		return config;
	}

	@BeforeAll
	protected static void initNetshot() throws Exception {
		Netshot.initConfig(PurgeTest.getNetshotConfig());
		Database.update();
		Database.init();
		Thread.sleep(1000);
	}

	private StaticDeviceGroup group1;

	@BeforeEach
	void createData() throws Exception {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			final Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			session.persist(domain);
			group1 = new StaticDeviceGroup(GROUP1_NAME);
			// Let's create devices with configurations every n hours
			Calendar now = Calendar.getInstance();
			for (int i = 0; i < DEVICES; i++) {
				Device device = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, i);
				device.getConfigs().clear();
				for (int h = 0; h < CONFIGS_PER_DAY * DAYS; h++) {
					Calendar configDate = (Calendar) now.clone();
					configDate.add(Calendar.HOUR_OF_DAY, -(24 / CONFIGS_PER_DAY) * h);
					// New configuration
					Config config = FakeDeviceFactory.getFakeCiscoIosConfig(device);
					if (i >= DEVICES - DEVICES_WITH_BIG_CONFIG) {
						// For last devices, increase the size of the config
						ConfigLongTextAttribute runningConfig = (ConfigLongTextAttribute) config.getAttribute("runningConfig");
						runningConfig.getLongText().setText(
							runningConfig.getLongText().getText() + "\n"
								+ runningConfig.getLongText().getText() + "\n"
								+ runningConfig.getLongText().getText() + "\n"
								+ runningConfig.getLongText().getText());
					}
					config.setChangeDate(configDate.getTime());
					device.getConfigs().add(config);
					if (h == 0) {
						device.setLastConfig(config);
					}
					// Snapshot task
					Task task = new TakeSnapshotTask(device, "Test", "faker", false, true, true);
					Calendar taskDate = (Calendar) configDate.clone();
					taskDate.add(Calendar.SECOND, -20);
					task.setCreationDate(taskDate.getTime());
					taskDate.add(Calendar.SECOND, 18);
					task.setExecutionDate(taskDate.getTime());
					task.setChangeDate(taskDate.getTime());
					task.setStatus(Task.Status.SUCCESS);
					session.persist(task);
				}
				// Add old modules
				{
					Module oldModule = new Module("chassis", "OLDPART1", "9999999TEST99", device);
					oldModule.setRemoved(true);
					Calendar firstSeenDate = (Calendar) now.clone();
					firstSeenDate.add(Calendar.DATE, -300);
					oldModule.setFirstSeenDate(firstSeenDate.getTime());
					Calendar lastSeenDate = (Calendar) now.clone();
					lastSeenDate.add(Calendar.DATE, -25);
					oldModule.setLastSeenDate(lastSeenDate.getTime());
					device.getModules().add(oldModule);
				}
				{
					Module oldModule = new Module("chassis", "OLDPART2", "9999999TEST09", device);
					oldModule.setRemoved(true);
					Calendar firstSeenDate = (Calendar) now.clone();
					firstSeenDate.add(Calendar.DATE, -400);
					oldModule.setFirstSeenDate(firstSeenDate.getTime());
					Calendar lastSeenDate = (Calendar) now.clone();
					lastSeenDate.add(Calendar.DATE, -5);
					oldModule.setLastSeenDate(lastSeenDate.getTime());
					device.getModules().add(oldModule);
				}

				session.persist(device);
				if (i < DEVICES_IN_GROUP1) {
					// Add the two first devices only to the group
					group1.addDevice(device);
				}
			}
			session.persist(group1);
			session.getTransaction().commit();
		}
	}

	@AfterEach
	void cleanUpData() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from Config")
				.executeUpdate();
			session
				.createMutationQuery("delete from LongTextConfiguration")
				.executeUpdate();
			session
				.createMutationQuery("delete from Module")
				.executeUpdate();
			session
				.createMutationQuery("delete from Device")
				.executeUpdate();
			session
				.createMutationQuery("delete from DeviceGroup")
				.executeUpdate();
			session
				.createMutationQuery("delete from Domain")
				.executeUpdate();
			session
				.createMutationQuery("delete from Task")
				.executeUpdate();
			session.getTransaction().commit();
		}
	}

	private void assertDeviceCount(Session session, long expected) {
		Assertions.assertEquals(
			expected,
			session.createNativeQuery("select count(1) from device d", Long.class)
				.getSingleResultOrNull(),
			"Unexpected number of device entries");
	}

	private void assertConfigCount(Session session, long expected) {
		Assertions.assertEquals(
			expected,
			session.createNativeQuery("select count(1) from config c", Long.class)
				.getSingleResultOrNull(),
			"Unexpected number of config entries");
		Assertions.assertEquals(
			expected * 3, // three attributes per config are created by FakeDeviceFactory
			session.createNativeQuery("select count(1) from config_attribute ca", Long.class)
				.getSingleResultOrNull(),
			"Unexpected number of config attribute entries");
		Assertions.assertEquals(
			expected * 1, // one attribute is long text configuration
			session.createNativeQuery("select count(1) from long_text_configuration ltc", Long.class)
				.getSingleResultOrNull(),
			"Unexpected number of long text configuration entries");
	}

	private void assertTaskCount(Session session, long expected) {
		Assertions.assertEquals(
			expected,
			session.createNativeQuery("select count(1) from task t", Long.class)
				.getSingleResultOrNull(),
			"Unexpected number of task entries");
		Assertions.assertEquals(
			expected,
			session.createNativeQuery("select count(1) from task t where t.dtype = 'TakeSnapshotTask'", Long.class)
				.getSingleResultOrNull(),
			"Unexpected number of snapshot task entries");
	}

	private void assertModuleCount(Session session, long expected) {
		Assertions.assertEquals(
			expected,
			session.createNativeQuery("select count(1) from Module m", Long.class)
				.getSingleResultOrNull(),
			"Unexpected number of module entries");
	}


	@Test
	@DisplayName("Initial count test")
	@ResourceLock("DB")
	public void initialCountTest() {
		try (Session session = Database.getSession()) {
			// Check number of items before purge task
			this.assertDeviceCount(session, DEVICES);
			this.assertConfigCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertModuleCount(session, DEVICES * 4);
			Assertions.assertEquals(DEVICES_IN_GROUP1, session
					.createSelectionQuery("select d from Device d join d.groupMemberships gm where gm.key.group = :group", Device.class)
					.setParameter("group", group1)
					.getResultCount(),
				"The number of devices in group1 is not correct");
		}
	}

	@Test
	@DisplayName("Task purge test")
	@ResourceLock("DB")
	public void taskPurgeTest() {
		final int PURGE_DAYS = 18;
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", PURGE_DAYS, 0, 0, 0, 0, (DeviceGroup) null);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			this.assertConfigCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * PURGE_DAYS);
			this.assertModuleCount(session, DEVICES * 4);
		}
	}

	@Test
	@DisplayName("Per-group task purge test")
	@ResourceLock("DB")
	public void perGroupTaskPurgeTest() {
		final int PURGE_DAYS = 18;
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", PURGE_DAYS, 0, 0, 0, 0, group1);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			this.assertConfigCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertTaskCount(session,
				(DEVICES - DEVICES_IN_GROUP1) * CONFIGS_PER_DAY * DAYS
					+ DEVICES_IN_GROUP1 * CONFIGS_PER_DAY * PURGE_DAYS);
			this.assertModuleCount(session, DEVICES * 4);
		}
	}

	@Test
	@DisplayName("Config purge test")
	@ResourceLock("DB")
	public void configPurgeTest() {
		final int PURGE_DAYS = 18;
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, PURGE_DAYS, 0, 0, 0, (DeviceGroup) null);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			this.assertConfigCount(session, DEVICES * CONFIGS_PER_DAY * PURGE_DAYS);
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertModuleCount(session, DEVICES * 4);
		}
	}

	@Test
	@DisplayName("Per-group config purge test")
	@ResourceLock("DB")
	public void perGroupConfigPurgeTest() {
		final int PURGE_DAYS = 18;
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, PURGE_DAYS, 0, 0, 0, group1);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			this.assertConfigCount(session,
				(DEVICES - DEVICES_IN_GROUP1) * CONFIGS_PER_DAY * DAYS
					+ DEVICES_IN_GROUP1 * CONFIGS_PER_DAY * PURGE_DAYS);
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertModuleCount(session, DEVICES * 4);
		}
	}

	@Test
	@DisplayName("Oversized config purge test")
	@ResourceLock("DB")
	public void oversizedConfigPurgeTest() {
		final int PURGE_DAYS = 18;
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, PURGE_DAYS, 2, 0, 0, (DeviceGroup) null);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			// Only device #1 has configs bigger than 2KB
			this.assertConfigCount(session,
				(DEVICES - DEVICES_WITH_BIG_CONFIG) * CONFIGS_PER_DAY * DAYS
					+ DEVICES_WITH_BIG_CONFIG * CONFIGS_PER_DAY * PURGE_DAYS);
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertModuleCount(session, DEVICES * 4);
		}
	}

	@Test
	@DisplayName("Sparse config purge test")
	@ResourceLock("DB")
	public void sparseConfigPurgeTest() {
		final int PURGE_DAYS = 18;
		final int KEEP_DAYS = 2; // Keep one config every two days
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, PURGE_DAYS, 0, KEEP_DAYS, 0, (DeviceGroup) null);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			// Only device #1 has configs bigger than 2KB
			this.assertConfigCount(session,
				DEVICES * CONFIGS_PER_DAY * PURGE_DAYS +     // All configs during PURGE_DAYS
					DEVICES * (DAYS - PURGE_DAYS) / KEEP_DAYS);  // One config per device every two days for the rest of time
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertModuleCount(session, DEVICES * 4);
		}
	}

	@Test
	@DisplayName("Module purge test")
	@ResourceLock("DB")
	public void modulePurgeTest() {
		final int PURGE_DAYS = 18;
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, 0, 0, 0, PURGE_DAYS, (DeviceGroup) null);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			this.assertConfigCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertModuleCount(session, DEVICES * 3);
		}
	}

	@Test
	@DisplayName("Per-group module purge test")
	@ResourceLock("DB")
	public void perGroupModulePurgeTest() {
		final int PURGE_DAYS = 18;
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, 0, 0, 0, PURGE_DAYS, group1);
		task.run();

		try (Session session = Database.getSession()) {
			this.assertDeviceCount(session, DEVICES);
			this.assertConfigCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertTaskCount(session, DEVICES * CONFIGS_PER_DAY * DAYS);
			this.assertModuleCount(session,
				(DEVICES - DEVICES_IN_GROUP1) * 4
					+ DEVICES_IN_GROUP1 * 3);
		}
	}
}
