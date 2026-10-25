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
package net.netshot.netshot.cluster;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;
import net.netshot.netshot.cluster.messages.AssignTasksMessage;
import net.netshot.netshot.cluster.messages.AutoSnapshotMessage;
import net.netshot.netshot.cluster.messages.ClusterMessage;
import net.netshot.netshot.cluster.messages.HelloClusterMessage;
import net.netshot.netshot.cluster.messages.LoadTasksMessage;
import net.netshot.netshot.cluster.messages.ReloadDriversMessage;
import net.netshot.netshot.cluster.messages.VaultInstanceChangedMessage;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.http.HttpServerAddress;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.rest.RestViews.ClusteringView;
import net.netshot.netshot.task.AutoSnapshotScheduler;
import net.netshot.netshot.task.engine.ClusterCoordinator;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.engine.TaskManager.Mode;
import net.netshot.netshot.vault.VaultManager;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

/**
 * Cluster Manager - main component of clustering feature.
 */
@Slf4j
public class ClusterManager implements Service, ClusterCoordinator {

	/** Name of the PSQL notification channel. */
	private static final String NOTIFICATION_CHANNEL = "clustering";

	/** Netshot clustering version. */
	protected static final int CLUSTERING_VERSION = 1;

	/** Timers. */
	private static final int HELLO_INTERVAL = 6000;
	private static final int NEGOTIATING_HELLO_INTERVAL = 2000;
	private static final int HELLO_HOLDTIME = 30000;
	private static final int HELLO_DRIFTTIME = 30000;
	private static final int NEGOTIATION_DURATION = 25000;
	private static final int RECEIVE_TIMEOUT = 2000;
	private static final int ORPHAN_SWEEP_INTERVAL = 60000;
	private static final int TASK_LOCK_CHECK_INTERVAL = 10000;
	private static final int SLOW_WORKER_ACTION_WARNING = 10000;

	/** Actions run by the worker thread that can be coalesced (only one pending at a time). */
	private enum WorkerAction {
		LOAD_TASKS,
		ASSIGN_TASKS,
		RELOAD_DRIVERS,
		REASSIGN_ORPHANS,
	}

	/** Whether the clustering is enabled. */
	public static final Setting<Boolean> ENABLED = Setting.bool("netshot.cluster.enabled").defaultValue(false).restart();

	/** Priority of the local member in the master election. */
	static final Setting<Integer> MASTER_PRIORITY = Setting.integer("netshot.cluster.master.priority").defaultValue(100).restart();

	/** Priority of the local member to run the tasks. */
	static final Setting<Integer> RUNNER_PRIORITY = Setting.integer("netshot.cluster.runner.priority").defaultValue(100).restart();

	/** Weight of the local member to run the tasks. */
	static final Setting<Integer> RUNNER_WEIGHT = Setting.integer("netshot.cluster.runner.weight")
		.defaultValue(100).range(1, 1000).restart();

	/** ID of the local member (20 lowercase hex digits; generated if not set). */
	static final Setting<String> ID = Setting.text("netshot.cluster.id").restart();

	/** The loaded device drivers. */
	private final DriverRegistry driverRegistry;

	/** The scheduler of the automatic snapshots. */
	private final AutoSnapshotScheduler autoSnapshotScheduler;

	/** Whether a driver reload was just requested. */
	private boolean driverReloadRequested;

	/** Whether the task manager is requesting other servers to load and execute waiting tasks. */
	private boolean loadTasksRequested;

	/** Whether a member is requesting the cluster master to assign new tasks. */
	private boolean assignTasksRequested;

	/** IDs of devices to request auto snapshots for. */
	private Set<Long> autoSnapshotDeviceIds = new HashSet<>();

	/** IDs of Vault instances whose cache should be invalidated cluster-wide. */
	private Set<Long> vaultInstanceReloadIds = new HashSet<>();

	/** The task manager. */
	private final TaskManager taskManager;

	@Override
	public String localInstanceId() {
		ClusterMember local = this.localMember;
		return local == null ? null : local.getInstanceId();
	}

	/**
	 * Gets the list of cluster members for external use.
	 * @return the cluster members
	 */
	public List<ClusterMember> getClusterMembers() {
		List<ClusterMember> list = new ArrayList<>();
		for (ClusterMember member : this.members.values()) {
			try {
				list.add((ClusterMember) member.clone());
			}
			catch (CloneNotSupportedException e) {
				// Shouldn't happen
			}
		}
		return list;
	}

	/**
	 * Gets the active members (master, member or negotiating), as task runners.
	 * @return the runners
	 */
	private List<TaskManager.Runner> activeRunners() {
		List<TaskManager.Runner> runners = new ArrayList<>();
		for (ClusterMember member : this.members.values()) {
			if (MastershipStatus.MASTER.equals(member.getStatus())
				|| MastershipStatus.MEMBER.equals(member.getStatus())
				|| MastershipStatus.NEGOTIATING.equals(member.getStatus())) {
				runners.add(new TaskManager.Runner(member.getInstanceId(), member.getRunnerPriority(), member.getRunnerWeight()));
			}
		}
		return runners;
	}

	/**
	 * Request driver reload on all cluster members.
	 */
	public void requestDriverReload() {
		this.driverReloadRequested = true;
	}

	@Override
	public void requestTasksLoad() {
		this.loadTasksRequested = true;
	}

	@Override
	public void requestTasksAssignment() {
		this.assignTasksRequested = true;
	}

	@Override
	public void requestAutoSnapshot(long deviceId) {
		synchronized (this.autoSnapshotDeviceIds) {
			this.autoSnapshotDeviceIds.add(deviceId);
		}
	}

	/**
	 * Request all cluster members to invalidate their local Vault cache
	 * for the given Vault instance (e.g. after its connection settings changed).
	 * @param vaultInstanceId the ID of the Vault instance
	 */
	public void requestVaultInstanceReload(long vaultInstanceId) {
		synchronized (this.vaultInstanceReloadIds) {
			this.vaultInstanceReloadIds.add(vaultInstanceId);
		}
	}

	/** JSON reader. */
	private ObjectReader jsonReader;

	/** JSON writer. */
	private ObjectWriter jsonWriter;

	/** Local cluster member. */
	private ClusterMember localMember;

	/** Last master member. */
	private volatile ClusterMember master;

	/** All cluster members. */
	private Map<String, ClusterMember> members;

	/** Time (epoch) of last hello message sent. */
	private long lastSentHelloTime;

	/**
	 * Worker thread, executing the (potentially slow, database-bound) actions triggered by
	 * cluster messages, so that the main cluster thread (hello messages, member expiry, master
	 * election) never waits on the database connection pool.
	 */
	private final ScheduledExecutorService worker;

	/** Whether the service is running (the main loop runs until it's false). */
	private volatile boolean running;

	/** The number of sent cluster messages. */
	private final AtomicLong sentMessages = new AtomicLong();

	/** The number of received cluster messages (from the other members). */
	private final AtomicLong receivedMessages = new AtomicLong();

	/** The number of received notifications which aren't valid cluster messages. */
	private final AtomicLong invalidMessages = new AtomicLong();

	/** The number of times the local member became the master. */
	private final AtomicLong masterships = new AtomicLong();

	/** The number of (re)connections of the main loop to the database. */
	private final AtomicLong connections = new AtomicLong();

	/** Whether the main loop is connected to the database (listening to the cluster messages). */
	private volatile boolean connected;

	/** Runs the main loop (hello messages, member expiry, master election). */
	private ExecutorService mainLoop;

	/** Coalescable actions currently queued (not yet started) in the worker. */
	private final Set<WorkerAction> pendingActions = ConcurrentHashMap.newKeySet();

	/** The Vault client (its caches are invalidated on request of the other members). */
	private final VaultManager vaultManager;

	/**
	 * Constructor.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param autoSnapshotScheduler the scheduler of the automatic snapshots
	 * @param taskManager the task manager
	 * @param vaultManager the Vault client (its caches are invalidated on request of the other members)
	 */
	public ClusterManager(DriverRegistry driverRegistry, AutoSnapshotScheduler autoSnapshotScheduler, TaskManager taskManager, VaultManager vaultManager) {
		this.vaultManager = vaultManager;
		this.driverRegistry = driverRegistry;
		this.autoSnapshotScheduler = autoSnapshotScheduler;
		this.taskManager = taskManager;
		ObjectMapper jsonMapper = new ObjectMapper();
		this.jsonReader = jsonMapper.readerWithView(ClusteringView.class);
		this.jsonWriter = jsonMapper.writerWithView(ClusteringView.class);
		this.members = new ConcurrentHashMap<>();
		this.master = null;
		this.lastSentHelloTime = 0L;
		this.worker = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("NetshotClusterWorker"));
	}

	/**
	 * Builds the local member (from the configuration and the loaded drivers, when the service starts).
	 */
	private void initLocalMember() {
		String jvmVersion = System.getProperty("java.vm.version");
		Config config = Netshot.config().current();
		int masterPriority = config.get(MASTER_PRIORITY);
		int runnerPriority = config.get(RUNNER_PRIORITY);
		int runnerWeight = config.get(RUNNER_WEIGHT);

		String localId = config.get(ID);
		if (localId != null) {
			// First option: configured ID
			if (!localId.matches("^[0-9a-z]{20}$")) {
				log.error("Invalid netshot.cluster.id parameter (invalid format), will generate one");
				localId = null;
			}
		}
		if (localId == null) {
			// Second option: based on MAC address and HTTP port (read from the configuration,
			// the HTTP server isn't started yet)
			final int restPort = HttpServerAddress.fromConfig().port();
			try {
				InetAddress mainAddress = InetAddress.getLocalHost();
				NetworkInterface networkInterface = NetworkInterface.getByInetAddress(mainAddress);
				byte[] mainMac = networkInterface.getHardwareAddress();
				if (mainMac != null && mainMac.length == 6 && (mainMac[0] != 0 || mainMac[1] != 0
					|| mainMac[2] != 0 || mainMac[3] != 0 || mainMac[4] != 0 || mainMac[5] != 0)) {
					localId = String.format("01ff%02x%02x%02x%02x%02x%02x%04x",
						mainMac[0], mainMac[1], mainMac[2], mainMac[3], mainMac[4], mainMac[5], restPort);
					log.info("Cluster ID {} was automatically generated based on main local MAC address", localId);
				}
			}
			catch (Exception e) {
				log.debug("Error while generating cluster ID based on MAC address", e);
			}
		}
		if (localId == null) {
			// Last resort: random
			byte[] randomBase = new byte[6];
			new Random().nextBytes(randomBase);
			localId = String.format("09ff%02x%02x%02x%02x%02x%02x0000",
				randomBase[0], randomBase[1], randomBase[2], randomBase[3], randomBase[4], randomBase[5]);
			log.warn("Cluster ID {} was randomly generated - you should rather configure netshot.cluster.id",
				localId);
		}

		this.localMember = new ClusterMember(localId, Netshot.getHostname(), ClusterManager.CLUSTERING_VERSION,
			masterPriority, runnerPriority, runnerWeight, Netshot.VERSION, jvmVersion, this.driverRegistry.getAllDriverHash());
		this.members.put(localId, this.localMember);
	}

	@Override
	public String name() {
		return "cluster manager";
	}

	@Override
	public synchronized void start() {
		this.initLocalMember();
		this.taskManager.setMode(Mode.CLUSTER_MEMBER);
		this.running = true;
		this.mainLoop = Executors.newSingleThreadExecutor(new NamedThreadFactory("NetshotClusterManager"));
		this.mainLoop.execute(this::runLoop);
		this.startPeriodicActions();
	}

	@Override
	public void stop() throws InterruptedException {
		this.running = false;
		this.mainLoop.shutdownNow();
		this.worker.shutdownNow();
		this.mainLoop.awaitTermination(10, TimeUnit.SECONDS);
		this.worker.awaitTermination(10, TimeUnit.SECONDS);
	}

	@Override
	public boolean isHealthy() {
		return this.running && this.connected;
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("connected", this.connected);
		stats.put("localStatus", String.valueOf(this.localMember == null ? null : this.localMember.getStatus()));
		stats.put("members", this.members.size());
		ClusterMember currentMaster = this.master;
		stats.put("master", currentMaster == null ? null : currentMaster.getInstanceId());
		stats.put("sentMessages", this.sentMessages.get());
		stats.put("receivedMessages", this.receivedMessages.get());
		stats.put("invalidMessages", this.invalidMessages.get());
		stats.put("masterships", this.masterships.get());
		stats.put("connections", this.connections.get());
		return stats;
	}

	/**
	 * Schedule the periodic actions of the worker thread.
	 */
	private void startPeriodicActions() {
		// Periodic orphan task sweep (on master only): a dead member is only fully released
		// by the database some time after it went silent
		this.schedulePeriodic("ORPHAN_SWEEP", ORPHAN_SWEEP_INTERVAL, () -> {
			if (Mode.CLUSTER_MASTER.equals(this.taskManager.getMode())) {
				this.submitCoalesced(WorkerAction.REASSIGN_ORPHANS, this.taskManager::reassignOrphanTasks);
			}
		});
		// Periodic check of the task lock DB connection, so that a lost connection (and thus
		// lost locks) is noticed and recovered even when no task starts or ends
		this.schedulePeriodic("TASK_LOCK_CHECK", TASK_LOCK_CHECK_INTERVAL, () -> this.taskManager.lockManager().check());
	}

	/**
	 * Schedule a periodic action in the worker thread (an exception doesn't cancel next runs).
	 * @param name the name of the action (for logging)
	 * @param interval the interval between runs (ms)
	 * @param action the action
	 */
	private void schedulePeriodic(String name, long interval, Runnable action) {
		this.worker.scheduleWithFixedDelay(() -> {
			try {
				action.run();
			}
			catch (Exception e) {
				log.error("Error while executing periodic cluster action {}", name, e);
			}
		}, interval, interval, TimeUnit.MILLISECONDS);
	}

	/**
	 * Run an action in the worker thread.
	 * @param name the name of the action (for logging)
	 * @param action the action to run
	 * @return true if the action was queued
	 */
	private boolean submit(String name, Runnable action) {
		try {
			this.worker.execute(() -> {
				long startTime = System.currentTimeMillis();
				try {
					action.run();
				}
				catch (Exception e) {
					log.error("Error while executing cluster action {}", name, e);
				}
				long duration = System.currentTimeMillis() - startTime;
				if (duration > SLOW_WORKER_ACTION_WARNING) {
					log.warn("Cluster action {} took {} ms", name, duration);
				}
			});
			return true;
		}
		catch (RejectedExecutionException e) {
			log.error("Unable to queue cluster action {}", name, e);
			return false;
		}
	}

	/**
	 * Run an action in the worker thread, unless the same action is already queued
	 * (and not started yet).
	 * @param action the action type
	 * @param runnable the action to run
	 */
	private void submitCoalesced(WorkerAction action, Runnable runnable) {
		if (!this.pendingActions.add(action)) {
			log.trace("Cluster action {} already queued", action);
			return;
		}
		boolean queued = this.submit(action.name(), () -> {
			// Clear the flag before running, so that a request received while running queues another run
			this.pendingActions.remove(action);
			runnable.run();
		});
		if (!queued) {
			this.pendingActions.remove(action);
		}
	}

	/**
	 * Send a cluster message (through the PostgreSQL notification system).
	 * @param connection
	 * @param message
	 * @throws SQLException
	 */
	private void sendMessage(Connection connection, ClusterMessage message) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			String content = this.jsonWriter.forType(ClusterMessage.class).writeValueAsString(message);
			// Can't make it work with a prepared statement so escape the quotes
			content = content.replace("'", "''");
			if (content.length() > 7999) {
				log.error("Cluster message {} is too long, cannot send it", message.getClass().getSimpleName());
			}
			else {
				log.trace("Sending message to {}: {}", ClusterManager.NOTIFICATION_CHANNEL, content);
				statement.execute(String.format("NOTIFY %s, '%s'", ClusterManager.NOTIFICATION_CHANNEL, content));
				this.sentMessages.incrementAndGet();
			}
		}
		catch (JsonProcessingException e) {
			log.error("Can't serialize Hello cluster message");
		}
	}

	/**
	 * Receive cluster message(s) (from the PostgreSQL notification system).
	 * @param pgConnection = the PostgreSQL connection
	 * @return list of received messages
	 * @throws SQLException in case of problem
	 */
	private List<ClusterMessage> receiveMessages(PGConnection pgConnection) throws SQLException {
		List<ClusterMessage> messages = new ArrayList<>();
		// Process all pending notifications
		PGNotification[] notifications = pgConnection.getNotifications(RECEIVE_TIMEOUT);
		if (notifications != null) {
			for (PGNotification notification : notifications) {
				log.trace("Received notification (name {}): {}", notification.getName(), notification.getParameter());
				try {
					ClusterMessage message = this.jsonReader.forType(ClusterMessage.class)
						.readValue(notification.getParameter());
					if (message.getInstanceId().equals(this.localMember.getInstanceId())) {
						if (message instanceof HelloClusterMessage helloMessage) {
							String receivedHostname = helloMessage.getMemberInfo().getHostname();
							if (!this.localMember.getHostname().equals(receivedHostname)) {
								log.error(
									"Received hello message from our instance ID but with different hostname ({})... please check cluster member ID conflict",
									receivedHostname);
							}
						}
						// Ignore my own messages
						continue;
					}
					this.receivedMessages.incrementAndGet();
					messages.add(message);
				}
				catch (JsonProcessingException e) {
					this.invalidMessages.incrementAndGet();
					log.error("Error while parsing PGSQL notification as cluster message", e);
				}
			}
		}
		return messages;
	}


	/**
	 * Main loop of the cluster manager.
	 */
	private void runLoop() {
		// Reconnection with exponential backoff
		int bhFactor = 0;
		boolean memberUpdated = true;
		boolean runnerChanged = true;

		while (this.running) {
			// Reconnection loop
			try {
				Thread.sleep(500 * bhFactor);
			}
			catch (InterruptedException e) {
				// Stopping
				break;
			}
			try (Connection dbConnection = Database.getConnection(false)) {
				PGConnection pgConnection = dbConnection.unwrap(PGConnection.class);

				try (Statement listenStatement = dbConnection.createStatement()) {
					listenStatement.execute(String.format("LISTEN %s", ClusterManager.NOTIFICATION_CHANNEL));
				}
				this.connected = true;
				this.connections.incrementAndGet();

				while (this.running) {
					// Wait a bit
					Thread.sleep(1000);
					// Main loop
					long nextHelloTime = lastSentHelloTime + HELLO_INTERVAL;
					if (MastershipStatus.NEGOTIATING.equals(this.localMember.getStatus())) {
						nextHelloTime = lastSentHelloTime + NEGOTIATING_HELLO_INTERVAL;
					}
					if (System.currentTimeMillis() > nextHelloTime) {
						HelloClusterMessage helloMessage = new HelloClusterMessage(this.localMember);
						this.sendMessage(dbConnection, helloMessage);
						this.lastSentHelloTime = System.currentTimeMillis();
					}
					// Broadcast-to-everyone requests: sent regardless of local mastership status,
					// since any node can NOTIFY the shared channel and every peer is listening
					// (unlike the member->master requests below, which only non-master members send).
					if (this.driverReloadRequested) {
						ReloadDriversMessage reloadMessage = new ReloadDriversMessage(this.localMember);
						this.sendMessage(dbConnection, reloadMessage);
					}
					this.driverReloadRequested = false;
					if (this.loadTasksRequested) {
						LoadTasksMessage taskMessage = new LoadTasksMessage(this.localMember);
						this.sendMessage(dbConnection, taskMessage);
					}
					this.loadTasksRequested = false;
					VaultInstanceChangedMessage vaultReloadMessage = null;
					synchronized (this.vaultInstanceReloadIds) {
						if (this.vaultInstanceReloadIds.size() > 0) {
							vaultReloadMessage = new VaultInstanceChangedMessage(this.localMember);
							vaultReloadMessage.addVaultInstanceIds(this.vaultInstanceReloadIds);
						}
						this.vaultInstanceReloadIds.clear();
					}
					if (vaultReloadMessage != null) {
						this.sendMessage(dbConnection, vaultReloadMessage);
					}
					if (!MastershipStatus.MASTER.equals(this.localMember.getStatus())) {
						if (this.assignTasksRequested) {
							AssignTasksMessage taskMessage = new AssignTasksMessage(this.localMember);
							this.sendMessage(dbConnection, taskMessage);
						}
						this.assignTasksRequested = false;
						AutoSnapshotMessage snapshotMessage = null;
						synchronized (this.autoSnapshotDeviceIds) {
							if (this.autoSnapshotDeviceIds.size() > 0) {
								snapshotMessage = new AutoSnapshotMessage(this.localMember);
								snapshotMessage.addDeviceIds(this.autoSnapshotDeviceIds);
							}
							this.autoSnapshotDeviceIds.clear();
						}
						if (snapshotMessage != null) {
							this.sendMessage(dbConnection, snapshotMessage);
						}
					}
					try (
						Statement fakeStatement = dbConnection.createStatement();
						ResultSet fakeResultSet = fakeStatement.executeQuery("SELECT 1");
					) {
						fakeResultSet.next();
					}
					List<ClusterMessage> messages = this.receiveMessages(pgConnection);
					synchronized (this) {
						final long currentTime = System.currentTimeMillis();
						for (ClusterMessage message : messages) {
							log.trace("Clustering message received: {}", message);
							// Check time difference
							if (Math.abs(message.getCurrentTime() - currentTime) > HELLO_DRIFTTIME) {
								log.warn(
									"Seeing excessive time difference ({} vs {}) with instance {}, please check time synchronization",
									message.getCurrentTime(), currentTime, message.getInstanceId());
							}
							if (message instanceof HelloClusterMessage helloMessage) {
								ClusterMember member = helloMessage.getMemberInfo();
								member.setLastSeenTime(System.currentTimeMillis()); // Set last seen time
								// Check clustering version
								if (member.getClusteringVersion() != ClusterManager.CLUSTERING_VERSION) {
									log.error("Incompatible clustering version {} vs local {} message received from {} - ignoring message",
										member.getClusteringVersion(), ClusterManager.CLUSTERING_VERSION, message.getInstanceId());
									continue;
								}
								ClusterMember oldMember = this.members.get(message.getInstanceId());
								if (oldMember == null || oldMember.getRunnerPriority() != member.getRunnerPriority()
									|| oldMember.getRunnerWeight() != member.getRunnerWeight()) {
									runnerChanged = true;
								}
								if (oldMember == null) {
									this.members.put(message.getInstanceId(), member);
								}
								else {
									oldMember.setHostname(member.getHostname());
									oldMember.setClusteringVersion(member.getClusteringVersion());
									oldMember.setMasterPriority(member.getMasterPriority());
									oldMember.setRunnerPriority(member.getRunnerPriority());
									oldMember.setRunnerWeight(member.getRunnerWeight());
									oldMember.setAppVersion(member.getAppVersion());
									oldMember.setJvmVersion(member.getJvmVersion());
									oldMember.setDriverHash(member.getDriverHash());
									oldMember.setStatus(member.getStatus());
									oldMember.setLastSeenTime(member.getLastSeenTime());
								}
								memberUpdated = true;
							}
							// The other messages trigger potentially slow actions: run them in the worker
							else if (message instanceof ReloadDriversMessage) {
								this.submitCoalesced(WorkerAction.RELOAD_DRIVERS, () -> {
									try {
										this.driverRegistry.refresh();
									}
									catch (Exception e) {
										log.error("Error while refreshing drivers", e);
									}
								});
							}
							else if (message instanceof LoadTasksMessage) {
								this.submitCoalesced(WorkerAction.LOAD_TASKS, this.taskManager::scheduleLocalTasks);
							}
							else if (message instanceof AssignTasksMessage) {
								if (MastershipStatus.MASTER.equals(this.localMember.getStatus())) {
									this.submitCoalesced(WorkerAction.ASSIGN_TASKS, () -> {
										// Mastership may have been lost in the meantime
										if (Mode.CLUSTER_MASTER.equals(this.taskManager.getMode())) {
											this.taskManager.scheduleNewTasks();
										}
									});
								}
							}
							else if (message instanceof AutoSnapshotMessage snapshotMessage) {
								if (MastershipStatus.MASTER.equals(this.localMember.getStatus())) {
									final Set<Long> deviceIds = new HashSet<>(snapshotMessage.getDeviceIds());
									this.submit("AUTO_SNAPSHOT", () -> {
										if (Mode.CLUSTER_MASTER.equals(this.taskManager.getMode())) {
											for (long deviceId : deviceIds) {
												this.autoSnapshotScheduler.scheduleSnapshotIfNeeded(deviceId);
											}
										}
									});
								}
							}
							else if (message instanceof VaultInstanceChangedMessage vaultMessage) {
								final Set<Long> vaultInstanceIds = new HashSet<>(vaultMessage.getVaultInstanceIds());
								this.submit("VAULT_INVALIDATE", () -> {
									for (long vaultInstanceId : vaultInstanceIds) {
										this.vaultManager.invalidate(vaultInstanceId);
									}
								});
							}
							else {
								log.warn("Unknown clustering message type received");
							}
						}
						// Check expired members
						for (ClusterMember member : this.members.values()) {
							if (member.equals(this.localMember)) {
								continue;
							}
							if (member.getLastSeenTime() < currentTime - HELLO_HOLDTIME
								&& !MastershipStatus.EXPIRED.equals(member.getStatus())) {
								log.warn("Member {} not seen since {}, going into EXPIRED state",
									member.getInstanceId(), member.getLastSeenTime());
								member.setStatus(MastershipStatus.EXPIRED);
								memberUpdated = true;
								runnerChanged = true;
							}
						}
						if (memberUpdated) {
							// Master election
							ClusterMember newMaster = null;
							for (ClusterMember member : this.members.values()) {
								if (MastershipStatus.MASTER.equals(member.getStatus())) {
									if (newMaster == null || member.compareTo(newMaster) > 0) {
										newMaster = member;
									}
								}
							}
							if (newMaster != null) {
								if (!newMaster.equals(this.master)) {
									log.warn("Cluster member {} is now master", newMaster);
								}
							}
							this.master = newMaster;
							memberUpdated = false;
						}
						if (runnerChanged) {
							this.taskManager.setRunners(this.activeRunners());
							this.submitCoalesced(WorkerAction.REASSIGN_ORPHANS, this.taskManager::reassignOrphanTasks);
							runnerChanged = false;
						}
						if (this.master == null) {
							boolean eligible = true;
							for (ClusterMember member : this.members.values()) {
								if (member.equals(this.localMember) || member.getStatus().equals(MastershipStatus.EXPIRED)) {
									continue;
								}
								if (member.compareTo(this.localMember) > 0) {
									eligible = false;
									break;
								}
							}
							if (eligible) {
								// Eligible for mastership
								if (MastershipStatus.NEGOTIATING.equals(this.localMember.getStatus())) {
									// Already in negotiating status
									if (this.localMember.getLastStatusChangeTime() + NEGOTIATION_DURATION < currentTime) {
										// Negotiation done, upgrade to master
										log.warn("Local cluster member is switching to MASTER status");
										this.masterships.incrementAndGet();
										this.localMember.setStatus(MastershipStatus.MASTER);
										this.master = this.localMember;
										this.taskManager.setMode(Mode.CLUSTER_MASTER);
										this.submit("BECOME_MASTER", () -> {
											this.taskManager.rescheduleAll();
											this.taskManager.reassignOrphanTasks();
										});
									}
								}
								else {
									log.warn("Local cluster member is switching to NEGOTIATING status");
									this.localMember.setStatus(MastershipStatus.NEGOTIATING);
								}
							}
						}
						else {
							// Checks on the current master
							if (!MastershipStatus.MEMBER.equals(this.localMember.getStatus()) && !this.master.equals(this.localMember)) {
								// Master conflict - Downgrade to normal member
								log.warn("Local cluster member is switching to MEMBER status");
								this.localMember.setStatus(MastershipStatus.MEMBER);
								this.taskManager.setMode(Mode.CLUSTER_MEMBER);
							}
						}
					}
					if (bhFactor > 0) {
						bhFactor = 0;
						log.info("ClusterManager recovered from exception state");
					}
				}
			}
			catch (SQLException | InterruptedException e) {
				this.connected = false;
				if (!this.running) {
					break;
				}
				log.error("ClusterManager got exception", e);
				bhFactor *= 2;
				if (bhFactor < 1) {
					bhFactor = 1;
				}
				else if (bhFactor > 32) {
					bhFactor = 32;
				}
			}
		}
	}
}
