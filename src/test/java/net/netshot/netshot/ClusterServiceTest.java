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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.api.rest.v0.system.MasterStatusDto;
import net.netshot.netshot.api.rest.v0.system.SystemMapper;
import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;
import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.cluster.ClusterService.MasterStatus;
import net.netshot.netshot.system.ServerInfo;
import net.netshot.netshot.system.ServerInfoService;

/**
 * Tests of the cluster and server information use cases and of their representation (the
 * contract tests only cover a server without clustering, hence without any member).
 */
class ClusterServiceTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(ClusterServiceTest.class, System.currentTimeMillis());

	private static ClusterMember member(String id, boolean local, MastershipStatus status) {
		ClusterMember member = new ClusterMember(id, "host-" + id, 3, 10, 20, 30, "1.2.3", "21.0.1", "hash-" + id);
		member.setLocal(local);
		member.setStatus(status);
		member.setLastSeenTime(1_700_000_000_000L);
		return member;
	}

	private static ClusterService service(ClusterMember... members) {
		return new ClusterService(() -> List.of(members));
	}

	@Test
	@DisplayName("Without clustering: no member, not enabled, the local node is master")
	void noCluster() {
		ClusterService service = service();
		Assertions.assertEquals(List.of(), service.members());
		Assertions.assertEquals(new MasterStatus(false, true, null), service.masterStatus());
	}

	@Test
	@DisplayName("The local node is the master")
	void localMaster() {
		ClusterService service = service(member("a", true, MastershipStatus.MASTER), member("b", false, MastershipStatus.MEMBER));
		Assertions.assertEquals(2, service.members().size());
		Assertions.assertEquals(new MasterStatus(true, true, "a"), service.masterStatus());
	}

	@Test
	@DisplayName("Another node is the master")
	void remoteMaster() {
		ClusterService service = service(member("a", true, MastershipStatus.MEMBER), member("b", false, MastershipStatus.MASTER));
		Assertions.assertEquals(new MasterStatus(true, false, "b"), service.masterStatus());
	}

	@Test
	@DisplayName("Nobody is master yet: the local node is not the master and there is no current master")
	void noMaster() {
		ClusterService service = service(member("a", true, MastershipStatus.NEGOTIATING), member("b", false, MastershipStatus.EXPIRED));
		Assertions.assertEquals(new MasterStatus(true, false, null), service.masterStatus());
	}

	@Test
	@DisplayName("A cluster member is written as the cluster member class was")
	void memberRepresentation() throws Exception {
		List<ClusterMember> members = new ArrayList<>(List.of(member("a", true, MastershipStatus.MASTER), member("b", false, MastershipStatus.EXPIRED)));
		Object actual = SystemMapper.toMemberDtos(members);
		this.contract.checkBoth(JSON.writer().writeValueAsBytes(actual),
			XML.writer().writeValueAsBytes(actual));
		String json = new String(JSON.writer().writeValueAsBytes(actual));
		Assertions.assertTrue(json.contains("\"status\":\"MASTER\""), json);
		Assertions.assertTrue(json.contains("\"local\":true"), json);
		Assertions.assertTrue(json.contains("\"lastSeenTime\":1700000000000"), json);
	}

	@Test
	@DisplayName("The master status and the server information are published under the name of the former classes")
	void statusRepresentation() throws Exception {
		MasterStatusDto status = SystemMapper.toDto(new MasterStatus(true, false, "b"));
		String xml = new String(XML.writer().writeValueAsBytes(status));
		Assertions.assertTrue(xml.startsWith("<RsClusterMasterCheck>"), xml);
		Assertions.assertTrue(xml.contains("<master>false</master>") && xml.contains("<currentMasterId>b</currentMasterId>"), xml);
		Assertions.assertTrue(new String(XML.writer().writeValueAsBytes(SystemMapper.toDto(new ServerInfo("1.0", 1800))))
			.startsWith("<RsServerInfo>"));
		String json = new String(JSON.writer().writeValueAsBytes(SystemMapper.toDto(new ServerInfo("1.0", 1800))));
		Assertions.assertTrue(json.contains("\"serverVersion\":\"1.0\""), json);
		Assertions.assertTrue(json.contains("\"maxIdleTimeout\":1800"), json);
	}

	@Test
	@DisplayName("The server information is read when asked, from the version and the loaded settings")
	void serverInfo() {
		ServerInfo info = new ServerInfoService().get();
		Assertions.assertEquals(Netshot.VERSION, info.serverVersion());
		Assertions.assertEquals(UiUser.SETTINGS.getMaxIdleTime(), info.maxIdleTimeout());
	}
}
