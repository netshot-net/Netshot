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
package net.netshot.netshot.task.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.Session;

import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.handler.PurgeDatabaseHandler;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Tests for {@link PurgeDatabaseTask}'s "remove orphan binary files"
 * option: a filesystem sweep of the binary attribute storage folder that
 * deletes files no longer (or never) matching a database record, while
 * never touching a file that does - even one still sitting at its
 * pending (not-yet-finalized) name.
 */
@DatabaseTest
@DisplayName("Remove orphan binary files (PurgeDatabaseTask, DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class OrphanBinaryFilePurgeTest {

	Path storagePath;

	@BeforeAll
	void initDb(TestDatabase database) throws Exception {
		storagePath = database.binaryFolder();
	}

	@AfterEach
	void cleanUpData() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.createMutationQuery("delete from Config").executeUpdate();
			session.createMutationQuery("delete from LongTextConfiguration").executeUpdate();
			session.createMutationQuery("delete from Device").executeUpdate();
			session.createMutationQuery("delete from Domain").executeUpdate();
			session.getTransaction().commit();
		}
	}

	private void setOldMtime(Path path) throws IOException {
		Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(48, ChronoUnit.HOURS)));
	}

	@Test
	@DisplayName("Orphan binary file sweep deletes only files with no matching database record, sparing recent or referenced ones")
	void removeOrphanFilesDeletesOnlyTrueOrphans() throws Exception {
		Device device;
		ConfigBinaryFileAttribute attrA;
		ConfigBinaryFileAttribute attrB;
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			session.persist(domain);
			device = new Device("CiscoIOS12", null, domain, "test");
			Config config = new Config(device);
			attrA = new ConfigBinaryFileAttribute(config, "attrA", "fileA.bin");
			attrB = new ConfigBinaryFileAttribute(config, "attrB", "fileB.bin");
			config.addAttribute(attrA);
			config.addAttribute(attrB);
			device.getConfigs().add(config);
			device.setLastConfig(config);
			session.persist(device);
			session.getTransaction().commit();
		}

		// attrA: fully finalized, real referenced file - must survive.
		Files.write(attrA.getPendingFilePath(), "real-data-a".getBytes());
		attrA.finalizeStorage();
		Path attrAFinalPath = attrA.getFilePath();

		// attrB: real, referenced, but stuck at its pending name and old -
		// simulates a crash between commit() and finalizeStorage(). Must
		// survive: its uid is known, so it's not an orphan.
		Path attrBPendingPath = attrB.getPendingFilePath();
		Files.write(attrBPendingPath, "real-data-b".getBytes());
		this.setOldMtime(attrBPendingPath);

		// True orphans: no database row references these uids at all.
		Path finalFormatOrphan = storagePath.resolve(
			"999_cfg1_orphanattr_%s.data".formatted(UUID.randomUUID()));
		Files.write(finalFormatOrphan, "orphan-final".getBytes());

		Path legacyFormatOrphan = storagePath.resolve("%s.data".formatted(UUID.randomUUID()));
		Files.write(legacyFormatOrphan, "orphan-legacy".getBytes());

		Path recentPendingOrphan = storagePath.resolve(
			".tmp.999_cfg0_orphanattr_%s.data".formatted(UUID.randomUUID()));
		Files.write(recentPendingOrphan, "orphan-recent-pending".getBytes());

		Path oldPendingOrphan = storagePath.resolve(
			".tmp.999_cfg0_orphanattr_%s.data".formatted(UUID.randomUUID()));
		Files.write(oldPendingOrphan, "orphan-old-pending".getBytes());
		this.setOldMtime(oldPendingOrphan);

		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, 0, 0, 0, 0, (DeviceGroup) null);
		task.setRemoveOrphanFiles(true);
		new PurgeDatabaseHandler().run(task);

		Assertions.assertEquals(Status.SUCCESS, task.getStatus());
		Assertions.assertTrue(Files.exists(attrAFinalPath), "The real, finalized attribute file must survive");
		Assertions.assertTrue(Files.exists(attrBPendingPath),
			"A real, referenced attribute file must survive even if still pending and old");
		Assertions.assertFalse(Files.exists(finalFormatOrphan), "A final-format orphan file must be deleted");
		Assertions.assertFalse(Files.exists(legacyFormatOrphan), "A legacy-format orphan file must be deleted");
		Assertions.assertTrue(Files.exists(recentPendingOrphan),
			"A recent pending file with no DB row could still be an in-progress snapshot and must survive");
		Assertions.assertFalse(Files.exists(oldPendingOrphan),
			"An old pending file with no DB row at all is a genuine orphan and must be deleted");
	}
}
