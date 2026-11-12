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
package net.netshot.netshot.cluster;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;
import net.netshot.netshot.task.engine.TaskManager.Mode;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DatabaseTest.Boot;
import net.netshot.netshot.testing.TestDatabase;

/**
 * Tests of the cluster manager: a real cluster manager, with short timers and a recording local node, talks to
 * fake members which the tests drive through the PostgreSQL channel of the clustering (and, for the election
 * and the failover, to a second real cluster manager).
 */
@DatabaseTest(boot = Boot.MIGRATED)
@Timeout(60)
@DisplayName("Cluster manager")
class ClusterManagerTest {

	/** Short timers: hello every 200 ms, expiry after 1.5 s, negotiation of 600 ms. */
	private static final ClusterManager.Timers TIMERS = new ClusterManager.Timers(200, 100, 1500, 30000, 600, 100, 50, 60000, 60000);

	/** Instance IDs, in increasing order. */
	private static final String ID_1 = "00000000000000000001";
	private static final String ID_2 = "00000000000000000002";
	private static final String ID_3 = "00000000000000000003";

	private Properties savedConfig;

	private Properties databaseConfig;

	private final List<ClusterManager> managers = new ArrayList<>();

	private final List<ClusterPeer> peers = new ArrayList<>();

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private static void await(BooleanSupplier condition, String message) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				Assertions.fail("Timeout: " + message);
			}
			Thread.sleep(20);
		}
	}

	private static MastershipStatus status(ClusterManager manager) {
		return MastershipStatus.valueOf((String) manager.stats().get("localStatus"));
	}

	private static Map<String, MastershipStatus> members(ClusterManager manager) {
		return manager.getClusterMembers().stream()
			.collect(Collectors.toMap(ClusterMember::getInstanceId, ClusterMember::getStatus));
	}

	@BeforeEach
	void setUp(TestDatabase database) {
		this.savedConfig = TestConfig.properties();
		this.databaseConfig = database.netshotConfig();
		this.logs.start();
		((Logger) LoggerFactory.getLogger(ClusterManager.class)).addAppender(this.logs);
	}

	@AfterEach
	void tearDown() throws Exception {
		for (ClusterManager manager : this.managers) {
			manager.stop();
		}
		for (ClusterPeer peer : this.peers) {
			peer.close();
		}
		((Logger) LoggerFactory.getLogger(ClusterManager.class)).detachAppender(this.logs);
		TestConfig.init(this.savedConfig);
	}

	/**
	 * Starts a cluster manager (its configuration is read when it starts).
	 * @param id the instance ID
	 * @param masterPriority the master priority
	 * @param node the local node
	 * @return the started cluster manager
	 */
	private ClusterManager start(String id, int masterPriority, RecordingNode node) {
		Properties config = new Properties();
		config.putAll(this.databaseConfig);
		config.setProperty("netshot.cluster.id", id);
		config.setProperty("netshot.cluster.master.priority", Integer.toString(masterPriority));
		TestConfig.init(config);
		ClusterManager manager = new ClusterManager(node, TIMERS);
		manager.start();
		this.managers.add(manager);
		return manager;
	}

	private ClusterPeer peer(String id, int masterPriority) throws SQLException {
		TestConfig.init(this.databaseConfig);
		ClusterPeer peer = new ClusterPeer(id, masterPriority);
		this.peers.add(peer);
		return peer;
	}

	/**
	 * Waits until the node sees the peer as a member.
	 * @param manager the cluster manager
	 * @param peer the peer
	 */
	private static void awaitMember(ClusterManager manager, ClusterPeer peer) throws InterruptedException {
		await(() -> members(manager).containsKey(peer.instanceId), "the node sees the peer");
	}

	/**
	 * Waits for the given time, checking that the node never becomes master.
	 * @param manager the cluster manager
	 * @param peer a peer, which receives the hello messages of the node
	 * @param duration the time to wait (ms)
	 */
	private static void assertNeverMaster(ClusterManager manager, ClusterPeer peer, long duration) throws InterruptedException {
		long end = System.currentTimeMillis() + duration;
		while (System.currentTimeMillis() < end) {
			Assertions.assertNotEquals(MastershipStatus.MASTER, status(manager));
			Thread.sleep(20);
		}
		Assertions.assertTrue(peer.received("Hello").stream()
			.noneMatch(h -> "MASTER".equals(h.path("memberInfo").path("status").asText())),
			"The node never announced itself as master");
	}

	@Nested
	@DisplayName("Election")
	class Election {

		@Test
		@DisplayName("A node alone negotiates, then becomes master and takes over the tasks")
		void aloneBecomesMaster() throws Exception {
			ClusterPeer observer = peer(ID_3, 100);
			RecordingNode node = new RecordingNode();
			ClusterManager manager = start(ID_2, 100, node);
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			Assertions.assertEquals(Mode.CLUSTER_MASTER, node.mode);
			await(() -> node.rescheduleAll.get() == 1, "all the tasks are rescheduled");
			Assertions.assertTrue(node.reassignOrphanTasks.get() >= 1, "The orphan tasks are reassigned");
			Assertions.assertEquals(List.of(ID_2), node.runners);
			Assertions.assertEquals(1L, manager.stats().get("masterships"));
			Assertions.assertEquals(ID_2, manager.stats().get("master"));
			observer.await("Hello", h -> "MASTER".equals(h.path("memberInfo").path("status").asText()));
			List<String> announced = observer.received("Hello").stream()
				.map(h -> h.path("memberInfo").path("status").asText())
				.distinct()
				.toList();
			Assertions.assertEquals(List.of("MEMBER", "NEGOTIATING", "MASTER"), announced);
		}

		@Test
		@DisplayName("No preemption: a node with a higher priority stays member of an existing master")
		void noPreemption() throws Exception {
			ClusterPeer master = peer(ID_1, 10);
			master.announce("MASTER");
			RecordingNode node = new RecordingNode();
			ClusterManager manager = start(ID_2, 200, node);
			awaitMember(manager, master);
			assertNeverMaster(manager, master, 2000);
			Assertions.assertEquals(MastershipStatus.MEMBER, status(manager));
			Assertions.assertEquals(Mode.CLUSTER_MEMBER, node.mode);
			Assertions.assertEquals(ID_1, manager.stats().get("master"));
			Assertions.assertEquals(List.of(ID_1, ID_2), node.runners);
			Assertions.assertEquals(0, node.rescheduleAll.get());
		}

		@Test
		@DisplayName("A member with a higher priority is elected rather than the node")
		void higherPriorityWins() throws Exception {
			ClusterPeer peer = peer(ID_1, 200);
			peer.announce("MEMBER");
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			awaitMember(manager, peer);
			assertNeverMaster(manager, peer, 2000);
		}

		@Test
		@DisplayName("On equal priorities, the member with the higher ID is elected rather than the node")
		void higherIdWins() throws Exception {
			ClusterPeer peer = peer(ID_3, 100);
			peer.announce("MEMBER");
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			awaitMember(manager, peer);
			assertNeverMaster(manager, peer, 2000);
		}

		@Test
		@DisplayName("On equal priorities, the node is elected rather than a member with a lower ID")
		void lowerIdLoses() throws Exception {
			ClusterPeer peer = peer(ID_1, 100);
			peer.announce("MEMBER");
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			Assertions.assertEquals(MastershipStatus.MEMBER, members(manager).get(ID_1));
		}

		@Test
		@DisplayName("A node announcing itself master after the node makes it step down")
		void stepsDown() throws Exception {
			ClusterPeer peer = peer(ID_3, 100);
			RecordingNode node = new RecordingNode();
			ClusterManager manager = start(ID_2, 100, node);
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			peer.announce("MASTER");
			await(() -> status(manager) == MastershipStatus.MEMBER, "the node steps down");
			Assertions.assertEquals(Mode.CLUSTER_MEMBER, node.mode);
			Assertions.assertEquals(ID_3, manager.stats().get("master"));
		}
	}

	@Nested
	@DisplayName("Failover")
	class Failover {

		@Test
		@DisplayName("A silent master expires, then the node negotiates, becomes master and takes over its tasks")
		void masterExpires() throws Exception {
			ClusterPeer master = peer(ID_3, 100);
			master.announce("MASTER");
			RecordingNode node = new RecordingNode();
			ClusterManager manager = start(ID_2, 100, node);
			await(() -> node.runners.equals(List.of(ID_2, ID_3)) && status(manager) == MastershipStatus.MEMBER,
				"the node is member, both members run the tasks");
			int reassigned = node.reassignOrphanTasks.get();

			master.announce(null);
			await(() -> members(manager).get(ID_3) == MastershipStatus.EXPIRED, "the master expires");
			await(() -> node.runners.equals(List.of(ID_2)), "the expired master no longer runs tasks");
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			Assertions.assertEquals(Mode.CLUSTER_MASTER, node.mode);
			await(() -> node.rescheduleAll.get() == 1, "all the tasks are rescheduled");
			Assertions.assertTrue(node.reassignOrphanTasks.get() > reassigned, "The tasks of the expired master are reassigned");
		}

		@Test
		@DisplayName("An expired member coming back is a member again")
		void expiredComesBack() throws Exception {
			ClusterPeer peer = peer(ID_1, 100);
			peer.announce("MEMBER");
			RecordingNode node = new RecordingNode();
			ClusterManager manager = start(ID_2, 100, node);
			await(() -> node.runners.equals(List.of(ID_1, ID_2)), "both members run the tasks");
			peer.announce(null);
			await(() -> members(manager).get(ID_1) == MastershipStatus.EXPIRED, "the member expires");
			peer.announce("MEMBER");
			await(() -> members(manager).get(ID_1) == MastershipStatus.MEMBER, "the member is back");
			await(() -> node.runners.equals(List.of(ID_1, ID_2)), "both members run the tasks again");
		}
	}

	@Nested
	@DisplayName("Received messages")
	class Received {

		private RecordingNode node;

		private ClusterManager manager;

		private ClusterPeer peer;

		/**
		 * Starts the node, member of the peer (master) or master itself (the peer is a member).
		 * @param nodeIsMaster whether the node is master
		 */
		private void join(boolean nodeIsMaster) throws Exception {
			this.peer = peer(ID_1, 100);
			this.peer.announce(nodeIsMaster ? "MEMBER" : "MASTER");
			this.node = new RecordingNode();
			this.manager = start(ID_2, 100, this.node);
			MastershipStatus expected = nodeIsMaster ? MastershipStatus.MASTER : MastershipStatus.MEMBER;
			await(() -> status(this.manager) == expected && members(this.manager).containsKey(ID_1), "the node joins as " + expected);
		}

		/**
		 * Sends a message, then a driver reload, and waits for the reload (the worker runs them in order).
		 * @param message the message
		 */
		private void sendThenSync(JsonNode message) throws InterruptedException {
			int reloads = this.node.reloadDrivers.get();
			this.peer.send(message);
			this.peer.send(this.peer.message("ReloadDrivers"));
			await(() -> this.node.reloadDrivers.get() > reloads, "the node reloads the drivers");
		}

		@Test
		@DisplayName("The drivers are reloaded")
		void reloadDrivers() throws Exception {
			this.join(false);
			this.peer.send(this.peer.message("ReloadDrivers"));
			await(() -> this.node.reloadDrivers.get() == 1, "the node reloads the drivers");
		}

		@Test
		@DisplayName("The local tasks are loaded")
		void loadTasks() throws Exception {
			this.join(false);
			this.peer.send(this.peer.message("LoadTasks"));
			await(() -> this.node.scheduleLocalTasks.get() == 1, "the node loads its tasks");
		}

		@Test
		@DisplayName("The Vault caches are invalidated, by any member")
		void vaultInstanceChanged() throws Exception {
			this.join(false);
			ObjectNode message = this.peer.message("VaultInstanceChanged");
			message.putArray("vaultInstanceIds").add(4).add(5);
			this.peer.send(message);
			await(() -> this.node.invalidatedVaults.equals(Set.of(4L, 5L)), "the node invalidates the Vault caches");
		}

		@Test
		@DisplayName("The master assigns the new tasks")
		void assignTasksOnMaster() throws Exception {
			this.join(true);
			this.peer.send(this.peer.message("AssignTasks"));
			await(() -> this.node.scheduleNewTasks.get() == 1, "the master assigns the new tasks");
		}

		@Test
		@DisplayName("The master schedules the automatic snapshots")
		void autoSnapshotOnMaster() throws Exception {
			this.join(true);
			ObjectNode message = this.peer.message("AutoSnapshot");
			message.putArray("deviceIds").add(7).add(8);
			this.peer.send(message);
			await(() -> this.node.autoSnapshots.equals(Set.of(7L, 8L)), "the master schedules the snapshots");
		}

		@Test
		@DisplayName("A member ignores the task assignment and automatic snapshot requests")
		void masterRequestsIgnoredByMember() throws Exception {
			this.join(false);
			this.sendThenSync(this.peer.message("AssignTasks"));
			ObjectNode snapshot = this.peer.message("AutoSnapshot");
			snapshot.putArray("deviceIds").add(7);
			this.sendThenSync(snapshot);
			Assertions.assertEquals(0, this.node.scheduleNewTasks.get());
			Assertions.assertEquals(Set.of(), this.node.autoSnapshots);
		}
	}

	@Nested
	@DisplayName("Sent messages")
	class Sent {

		@Test
		@DisplayName("The hello messages hold the member information, and only that")
		void helloFormat() throws Exception {
			ClusterPeer peer = peer(ID_1, 100);
			start(ID_2, 150, new RecordingNode());
			JsonNode hello = peer.await("Hello", _ -> true);
			Assertions.assertEquals(Set.of("type", "instanceId", "currentTime", "upTime", "messageId", "memberInfo"), fieldNames(hello));
			JsonNode member = hello.path("memberInfo");
			Assertions.assertEquals(Set.of("instanceId", "hostname", "clusteringVersion", "masterPriority", "runnerPriority",
				"runnerWeight", "appVersion", "jvmVersion", "driverHash", "status"), fieldNames(member));
			Assertions.assertEquals(ID_2, hello.path("instanceId").asText());
			Assertions.assertEquals(ID_2, member.path("instanceId").asText());
			Assertions.assertEquals(ClusterManager.CLUSTERING_VERSION, member.path("clusteringVersion").asInt());
			Assertions.assertEquals(150, member.path("masterPriority").asInt());
			Assertions.assertEquals("test-driver-hash", member.path("driverHash").asText());
		}

		private static Set<String> fieldNames(JsonNode node) {
			Set<String> names = new HashSet<>();
			node.fieldNames().forEachRemaining(names::add);
			return names;
		}

		@Test
		@DisplayName("The driver reload, task load and Vault requests are sent to all members")
		void broadcastRequests() throws Exception {
			ClusterPeer peer = peer(ID_1, 100);
			peer.announce("MEMBER");
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			manager.requestDriverReload();
			manager.requestTasksLoad();
			manager.requestVaultInstanceReload(3);
			manager.requestVaultInstanceReload(4);
			peer.await("ReloadDrivers", _ -> true);
			peer.await("LoadTasks", _ -> true);
			JsonNode vault = peer.await("VaultInstanceChanged", _ -> true);
			Assertions.assertEquals(Set.of(3L, 4L), ids(vault.path("vaultInstanceIds")));
		}

		@Test
		@DisplayName("A member sends the task assignment and automatic snapshot requests to the master")
		void requestsToMaster() throws Exception {
			ClusterPeer master = peer(ID_3, 100);
			master.announce("MASTER");
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			awaitMember(manager, master);
			manager.requestTasksAssignment();
			manager.requestAutoSnapshot(11);
			manager.requestAutoSnapshot(12);
			master.await("AssignTasks", _ -> true);
			JsonNode snapshot = master.await("AutoSnapshot", _ -> true);
			Assertions.assertEquals(Set.of(11L, 12L), ids(snapshot.path("deviceIds")));
		}

		@Test
		@DisplayName("The master runs the task assignment and automatic snapshot requests itself")
		void masterKeepsRequests() throws Exception {
			ClusterPeer peer = peer(ID_1, 100);
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			manager.requestTasksAssignment();
			manager.requestAutoSnapshot(11);
			manager.requestDriverReload();
			peer.await("ReloadDrivers", _ -> true);
			Assertions.assertEquals(List.of(), peer.received("AssignTasks"));
			Assertions.assertEquals(List.of(), peer.received("AutoSnapshot"));
		}

		private static Set<Long> ids(JsonNode array) {
			Set<Long> ids = new HashSet<>();
			array.forEach(id -> ids.add(id.asLong()));
			return ids;
		}
	}

	@Nested
	@DisplayName("Robustness")
	class Robustness {

		@Test
		@DisplayName("A member with another clustering version is ignored")
		void otherClusteringVersion() throws Exception {
			ClusterPeer peer = peer(ID_3, 100);
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			await(() -> (Boolean) manager.stats().get("connected"), "the node is connected");
			ObjectNode hello = peer.hello("MASTER");
			((ObjectNode) hello.path("memberInfo")).put("clusteringVersion", ClusterManager.CLUSTERING_VERSION + 1);
			peer.send(hello);
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			await(() -> this.errors().stream().anyMatch(m -> m.startsWith("Incompatible clustering version")), "the message is reported");
			Assertions.assertFalse(members(manager).containsKey(ID_3));
		}

		@Test
		@DisplayName("Invalid messages are counted and ignored")
		void invalidMessages() throws Exception {
			ClusterPeer peer = peer(ID_1, 100);
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			await(() -> (Boolean) manager.stats().get("connected"), "the node is connected");
			peer.sendRaw("not JSON");
			peer.sendRaw("{\"type\":\"Unknown\",\"instanceId\":\"" + ID_1 + "\"}");
			await(() -> (Long) manager.stats().get("invalidMessages") == 2L, "the invalid messages are counted");
			Assertions.assertEquals(0L, manager.stats().get("receivedMessages"));
			Assertions.assertTrue(manager.isHealthy());
		}

		@Test
		@DisplayName("A member using the ID of the node with another hostname is reported, and ignored")
		void idConflict() throws Exception {
			ClusterPeer peer = peer(ID_2, 300);
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			await(() -> (Boolean) manager.stats().get("connected"), "the node is connected");
			peer.send(peer.hello("MASTER"));
			await(() -> this.errors().stream().anyMatch(m -> m.contains("please check cluster member ID conflict")), "the conflict is reported");
			Assertions.assertEquals(Set.of(ID_2), members(manager).keySet());
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
		}

		@Test
		@DisplayName("After losing its database connection, the node reconnects and keeps its role")
		void reconnects() throws Exception {
			ClusterPeer peer = peer(ID_1, 100);
			peer.announce("MEMBER");
			ClusterManager manager = start(ID_2, 100, new RecordingNode());
			await(() -> status(manager) == MastershipStatus.MASTER, "the node becomes master");
			Assertions.assertEquals(1L, manager.stats().get("connections"));
			Assertions.assertTrue(peer.terminateOtherConnections() >= 1, "The connection of the node was ended");
			await(() -> (Long) manager.stats().get("connections") == 2L && manager.isHealthy(), "the node reconnects");
			int hellos = peer.received("Hello").size();
			await(() -> peer.received("Hello").size() > hellos, "the node sends hello messages again");
			Assertions.assertEquals(MastershipStatus.MASTER, status(manager));
			Assertions.assertEquals(1L, manager.stats().get("masterships"));
		}

		private List<String> errors() {
			return ClusterManagerTest.this.logs.list.stream()
				.filter(e -> e.getLevel() == Level.ERROR)
				.map(ILoggingEvent::getFormattedMessage)
				.toList();
		}
	}

	@Nested
	@DisplayName("Two nodes")
	class TwoNodes {

		@Test
		@DisplayName("Started together, two nodes elect one master, and the other one takes over when it stops")
		void electionAndFailover() throws Exception {
			List<ClusterManager> started = List.of(start(ID_1, 100, new RecordingNode()), start(ID_2, 100, new RecordingNode()));
			await(() -> started.stream().filter(m -> status(m) == MastershipStatus.MASTER).count() == 1
				&& started.stream().filter(m -> status(m) == MastershipStatus.MEMBER).count() == 1,
				"one node is master, the other one member");
			ClusterManager master = started.stream().filter(m -> status(m) == MastershipStatus.MASTER).findFirst().orElseThrow();
			ClusterManager other = started.stream().filter(m -> m != master).findFirst().orElseThrow();
			String masterId = (String) master.stats().get("master");
			await(() -> masterId.equals(other.stats().get("master")), "both nodes agree on the master");

			master.stop();
			ClusterManagerTest.this.managers.remove(master);
			await(() -> status(other) == MastershipStatus.MASTER, "the other node takes over");
			Assertions.assertEquals(MastershipStatus.EXPIRED, members(other).get(masterId));
		}
	}
}
