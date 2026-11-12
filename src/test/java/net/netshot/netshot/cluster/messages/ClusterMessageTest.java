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
package net.netshot.netshot.cluster.messages;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;
import net.netshot.netshot.platform.rest.RestViews.ClusteringView;
import net.netshot.netshot.testing.UnitTest;

/**
 * Tests of the cluster messages.
 */
@UnitTest
@DisplayName("Cluster messages")
class ClusterMessageTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final ClusterMember MEMBER = new ClusterMember("00000000000000000001", "host1", 1, 10, 20, 30, "1.0", "25", "hash");

	private static ClusterMessage roundTrip(ClusterMessage message) throws Exception {
		String json = MAPPER.writerWithView(ClusteringView.class).forType(ClusterMessage.class).writeValueAsString(message);
		return MAPPER.readerWithView(ClusteringView.class).forType(ClusterMessage.class).readValue(json);
	}

	@Test
	@DisplayName("Each message type goes through JSON, with its type and content")
	void roundTrips() throws Exception {
		MEMBER.setStatus(MastershipStatus.MASTER);
		HelloClusterMessage hello = (HelloClusterMessage) roundTrip(new HelloClusterMessage(MEMBER));
		Assertions.assertEquals(MEMBER.getInstanceId(), hello.getInstanceId());
		Assertions.assertEquals("host1", hello.getMemberInfo().getHostname());
		Assertions.assertEquals(10, hello.getMemberInfo().getMasterPriority());
		Assertions.assertEquals(MastershipStatus.MASTER, hello.getMemberInfo().getStatus());
		Assertions.assertFalse(hello.getMemberInfo().isLocal(), "The local flag isn't sent");

		Assertions.assertInstanceOf(ReloadDriversMessage.class, roundTrip(new ReloadDriversMessage(MEMBER)));
		Assertions.assertInstanceOf(LoadTasksMessage.class, roundTrip(new LoadTasksMessage(MEMBER)));
		Assertions.assertInstanceOf(AssignTasksMessage.class, roundTrip(new AssignTasksMessage(MEMBER)));

		AutoSnapshotMessage snapshot = new AutoSnapshotMessage(MEMBER);
		snapshot.addDeviceIds(List.of(1L, 2L));
		Assertions.assertEquals(Set.of(1L, 2L), ((AutoSnapshotMessage) roundTrip(snapshot)).getDeviceIds());

		VaultInstanceChangedMessage vault = new VaultInstanceChangedMessage(MEMBER);
		vault.addVaultInstanceIds(List.of(3L));
		Assertions.assertEquals(Set.of(3L), ((VaultInstanceChangedMessage) roundTrip(vault)).getVaultInstanceIds());
	}

	@Test
	@DisplayName("A message with items splits into two halves with the same header")
	void split() {
		AutoSnapshotMessage message = new AutoSnapshotMessage(MEMBER);
		message.addDeviceIds(List.of(1L, 2L, 3L, 4L, 5L));
		List<ClusterMessage> parts = message.split();
		Assertions.assertEquals(2, parts.size());
		Set<Long> ids = new HashSet<>();
		for (ClusterMessage part : parts) {
			AutoSnapshotMessage snapshot = (AutoSnapshotMessage) part;
			Assertions.assertTrue(snapshot.getDeviceIds().size() >= 2);
			Assertions.assertEquals(message.getInstanceId(), part.getInstanceId());
			Assertions.assertEquals(message.getCurrentTime(), part.getCurrentTime());
			Assertions.assertEquals(message.getUpTime(), part.getUpTime());
			ids.addAll(snapshot.getDeviceIds());
		}
		Assertions.assertEquals(message.getDeviceIds(), ids);
	}

	@Test
	@DisplayName("A message without items to share can't be split")
	void noSplit() {
		VaultInstanceChangedMessage vault = new VaultInstanceChangedMessage(MEMBER);
		vault.addVaultInstanceIds(List.of(3L));
		Assertions.assertEquals(List.of(), vault.split());
		Assertions.assertEquals(List.of(), new HelloClusterMessage(MEMBER).split());
	}
}
