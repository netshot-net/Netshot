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
package net.netshot.netshot.task.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.handler.PurgeDatabaseHandler;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDatabase;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestWiring;

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
class OrphanBinaryFilePurgeTest {

	Path storagePath;

	@BeforeAll
	void initDb(TestDatabase database) throws Exception {
		storagePath = database.binaryFolder();
	}

	@AfterEach
	void cleanUpData() {
		TestDevices.deleteAll();
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
		Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		attrA = ConfigBinaryFileAttribute.create("attrA", "fileA.bin");
		attrB = ConfigBinaryFileAttribute.create("attrB", "fileB.bin");
		device = TestDevices.add(Device.create("CiscoIOS12", null, domain, "test"),
			Config.builder().author("").attributes(List.of(attrA, attrB)).build());
		long configId = device.lastConfigId();

		// attrA: fully finalized, real referenced file - must survive.
		Files.write(TestWiring.get().core().repositories().binaryFiles().pendingFilePath(attrA, device.id()), "real-data-a".getBytes());
		TestWiring.get().core().repositories().binaryFiles().finalizeStorage(attrA, device.id(), configId);
		Path attrAFinalPath = TestWiring.get().core().repositories().binaryFiles().filePath(attrA, device.id(), configId);

		// attrB: real, referenced, but stuck at its pending name and old -
		// simulates a crash between commit() and finalizeStorage(). Must
		// survive: its uid is known, so it's not an orphan.
		Path attrBPendingPath = TestWiring.get().core().repositories().binaryFiles().pendingFilePath(attrB, device.id());
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

		PurgeDatabaseTask spec = PurgeDatabaseTask.builder().configDays(0).moduleDays(0).removeOrphanFiles(true).build();
		Task task = Task.of(spec, "Test", "Global", "tester");
		TaskRun run = new TaskRun(task);
		new PurgeDatabaseHandler(TestWiring.get().core().transactions(), TestWiring.get().core().repositories().task(),
			TestWiring.get().core().repositories().binaryFiles()).run(task, spec, run);

		Assertions.assertEquals(Status.SUCCESS, run.getStatus());
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

	/**
	 * Runs a purge with the given specification.
	 *
	 * @param spec the purge to run
	 * @return the run of the task
	 */
	private TaskRun purge(PurgeDatabaseTask spec) {
		Task task = Task.of(spec, "Test", "Global", "tester");
		TaskRun run = new TaskRun(task);
		new PurgeDatabaseHandler(TestWiring.get().core().transactions(), TestWiring.get().core().repositories().task(),
			TestWiring.get().core().repositories().binaryFiles()).run(task, spec, run);
		return run;
	}

	@Test
	@DisplayName("The orphan sweep leaves the files which aren't binary attribute files")
	void removeOrphanFilesSkipsOtherFiles() throws Exception {
		Path notData = storagePath.resolve("%s.txt".formatted(UUID.randomUUID()));
		Files.write(notData, "notes".getBytes());
		Path notUuid = storagePath.resolve("999_cfg1_attr_not-a-uid.data");
		Files.write(notUuid, "other".getBytes());
		this.setOldMtime(notUuid);

		TaskRun run = this.purge(PurgeDatabaseTask.builder().configDays(0).moduleDays(0).removeOrphanFiles(true).build());

		Assertions.assertEquals(Status.SUCCESS, run.getStatus());
		Assertions.assertTrue(Files.exists(notData), "A file without the .data extension is kept");
		Assertions.assertTrue(Files.exists(notUuid), "A .data file not named after an attribute UID is kept");
		Files.delete(notData);
		Files.delete(notUuid);
	}

	@Test
	@DisplayName("Purging an old configuration deletes its binary files, the last configuration keeps its own")
	void configPurgeDeletesBinaryFiles() throws Exception {
		Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		ConfigBinaryFileAttribute oldAttribute = ConfigBinaryFileAttribute.create("backup", "old.tgz");
		ConfigBinaryFileAttribute lastAttribute = ConfigBinaryFileAttribute.create("backup", "last.tgz");
		Device device = TestDevices.add(Device.create("CiscoIOS12", null, domain, "test"),
			Config.builder().author("").attributes(List.of(lastAttribute)).build());
		long lastConfigId = device.lastConfigId();
		long oldConfigId = TestDevices.addOldConfig(device.id(), Config.builder().author("").attributes(List.of(oldAttribute))
			.changeDate(Instant.now().minus(40, ChronoUnit.DAYS)).build());
		var binaryFiles = TestWiring.get().core().repositories().binaryFiles();
		Files.write(binaryFiles.pendingFilePath(oldAttribute, device.id()), "old".getBytes());
		binaryFiles.finalizeStorage(oldAttribute, device.id(), oldConfigId);
		Files.write(binaryFiles.pendingFilePath(lastAttribute, device.id()), "last".getBytes());
		binaryFiles.finalizeStorage(lastAttribute, device.id(), lastConfigId);
		Path oldFile = binaryFiles.filePath(oldAttribute, device.id(), oldConfigId);
		Path lastFile = binaryFiles.filePath(lastAttribute, device.id(), lastConfigId);
		Assertions.assertTrue(Files.exists(oldFile) && Files.exists(lastFile));

		TaskRun run = this.purge(PurgeDatabaseTask.builder().configDays(30).moduleDays(0).build());

		Assertions.assertEquals(Status.SUCCESS, run.getStatus());
		Assertions.assertFalse(Files.exists(oldFile), "The file of the purged configuration is deleted");
		Assertions.assertTrue(Files.exists(lastFile), "The file of the last configuration is kept");
		Assertions.assertTrue(run.getLog().contains("Cleaning up done on configurations, 1 entries affected."), run.getLog());
	}
}
