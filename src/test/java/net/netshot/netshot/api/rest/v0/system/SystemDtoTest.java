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
package net.netshot.netshot.api.rest.v0.system;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;
import net.netshot.netshot.cluster.ClusterService.MasterStatus;
import net.netshot.netshot.system.ServerInfo;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.contract.DtoContract;

/**
 * The representation of the cluster members, the master status and the server information in the v0 API
 * (the contract tests only cover a server without clustering, hence without any member).
 */
@UnitTest
@DisplayName("v0 cluster and server information DTOs")
class SystemDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(SystemDtoTest.class, System.currentTimeMillis());

	private static ClusterMember member(String id, boolean local, MastershipStatus status) {
		ClusterMember member = new ClusterMember(id, "host-" + id, 3, 10, 20, 30, "1.2.3", "21.0.1", "hash-" + id);
		member.setLocal(local);
		member.setStatus(status);
		member.setLastSeenTime(1_700_000_000_000L);
		return member;
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
}
