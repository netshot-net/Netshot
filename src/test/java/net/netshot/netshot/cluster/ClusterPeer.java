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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.platform.database.Database;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

/**
 * A fake cluster member, talking to the cluster manager under test through the PostgreSQL channel of the
 * clustering, with messages written as raw JSON (the wire format the other Netshot versions rely on).
 */
final class ClusterPeer implements AutoCloseable {

	/** The channel of the cluster messages. */
	static final String CHANNEL = "clustering";

	/** Interval between the hello messages (ms). */
	private static final int HELLO_INTERVAL = 200;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** The instance ID of the peer. */
	final String instanceId;

	/** The master priority of the peer. */
	private final int masterPriority;

	/** The connection listening to the channel. */
	private final Connection listenConnection;

	/** The connection sending the messages. */
	private final Connection sendConnection;

	/** Polls the messages and sends the hello messages. */
	private final ScheduledExecutorService executor;

	/** The messages received from the other members. */
	private final List<JsonNode> received = new CopyOnWriteArrayList<>();

	/** The ID of the last message sent. */
	private final AtomicLong lastMessageId = new AtomicLong();

	/** The status announced in the hello messages (none sent when null). */
	private volatile String status;

	/**
	 * Connects a peer to the database of the current configuration and starts listening.
	 * @param instanceId the instance ID
	 * @param masterPriority the master priority
	 * @throws SQLException on error
	 */
	ClusterPeer(String instanceId, int masterPriority) throws SQLException {
		this.instanceId = instanceId;
		this.masterPriority = masterPriority;
		this.listenConnection = Database.openUnpooledConnection();
		this.sendConnection = Database.openUnpooledConnection();
		try (Statement statement = this.listenConnection.createStatement()) {
			statement.execute("LISTEN " + CHANNEL);
		}
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.executor.scheduleWithFixedDelay(this::poll, 0, 20, TimeUnit.MILLISECONDS);
		this.executor.scheduleWithFixedDelay(() -> {
			String currentStatus = this.status;
			if (currentStatus != null) {
				try {
					this.send(this.hello(currentStatus));
				}
				catch (IllegalStateException e) {
					// Closing
				}
			}
		}, 0, HELLO_INTERVAL, TimeUnit.MILLISECONDS);
	}

	/**
	 * Starts sending hello messages with the given status, or stops sending them (null).
	 * @param newStatus the mastership status (MEMBER, MASTER...)
	 */
	void announce(String newStatus) {
		this.status = newStatus;
	}

	/**
	 * Builds a message of the peer.
	 * @param type the type of message (Hello, LoadTasks...)
	 * @return the message
	 */
	ObjectNode message(String type) {
		ObjectNode message = MAPPER.createObjectNode();
		message.put("type", type);
		message.put("instanceId", this.instanceId);
		message.put("currentTime", System.currentTimeMillis());
		message.put("upTime", 1000L);
		message.put("messageId", this.lastMessageId.incrementAndGet());
		return message;
	}

	/**
	 * Builds a hello message of the peer.
	 * @param memberStatus the mastership status
	 * @return the message
	 */
	ObjectNode hello(String memberStatus) {
		ObjectNode message = this.message("Hello");
		ObjectNode member = message.putObject("memberInfo");
		member.put("instanceId", this.instanceId);
		member.put("hostname", "peer-" + this.instanceId);
		member.put("clusteringVersion", ClusterManager.CLUSTERING_VERSION);
		member.put("masterPriority", this.masterPriority);
		member.put("runnerPriority", 100);
		member.put("runnerWeight", 100);
		member.put("appVersion", "test");
		member.put("jvmVersion", "test");
		member.put("driverHash", "test-driver-hash");
		member.put("status", memberStatus);
		return message;
	}

	/**
	 * Sends a message on the channel.
	 * @param message the message
	 */
	void send(JsonNode message) {
		this.sendRaw(message.toString());
	}

	/**
	 * Sends a text on the channel, as is.
	 * @param payload the text
	 */
	synchronized void sendRaw(String payload) {
		try (PreparedStatement statement = this.sendConnection.prepareStatement("SELECT pg_notify(?, ?)")) {
			statement.setString(1, CHANNEL);
			statement.setString(2, payload);
			statement.execute();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to send the cluster message", e);
		}
	}

	/**
	 * Ends the other connections to the database (those of the cluster manager under test), from the server side.
	 * @return the number of ended connections
	 * @throws SQLException on error
	 */
	synchronized int terminateOtherConnections() throws SQLException {
		int listenPid = this.listenConnection.unwrap(PGConnection.class).getBackendPID();
		try (PreparedStatement statement = this.sendConnection.prepareStatement(
			"SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity "
				+ "WHERE datname = current_database() AND pid <> pg_backend_pid() AND pid <> ?")) {
			statement.setInt(1, listenPid);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getInt(1);
			}
		}
	}

	private void poll() {
		try {
			PGNotification[] notifications = this.listenConnection.unwrap(PGConnection.class).getNotifications(10);
			if (notifications == null) {
				return;
			}
			for (PGNotification notification : notifications) {
				JsonNode message = MAPPER.readTree(notification.getParameter());
				if (!this.instanceId.equals(message.path("instanceId").asText())) {
					this.received.add(message);
				}
			}
		}
		catch (Exception e) {
			// Closing
		}
	}

	/**
	 * Gets the messages received from the other members, of a type.
	 * @param type the type of message
	 * @return the messages, in the order they were received
	 */
	List<JsonNode> received(String type) {
		return this.received.stream().filter(m -> type.equals(m.path("type").asText())).toList();
	}

	/**
	 * Waits for a message received from the other members.
	 * @param type the type of message
	 * @param condition the condition the message meets
	 * @return the first matching message
	 * @throws InterruptedException if interrupted
	 */
	JsonNode await(String type, Predicate<JsonNode> condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			for (JsonNode message : this.received(type)) {
				if (condition.test(message)) {
					return message;
				}
			}
			Thread.sleep(20);
		}
		throw new AssertionError("No " + type + " message received");
	}

	@Override
	public void close() throws Exception {
		this.executor.shutdownNow();
		this.executor.awaitTermination(5, TimeUnit.SECONDS);
		this.listenConnection.close();
		this.sendConnection.close();
	}
}
