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
package net.netshot.netshot.access.collector;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.UsmUser;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("SNMP trap receiver")
public class SnmpTrapReceiverTest {

	@Test
	@DisplayName("No SNMPv3 user without netshot.snmptrap.user")
	void noUser() {
		Assertions.assertEquals(List.of(), SnmpTrapReceiver.parseUsers(null));
		Assertions.assertEquals(List.of(), SnmpTrapReceiver.parseUsers(""));
		Assertions.assertEquals(List.of(), SnmpTrapReceiver.parseUsers("  "));
	}

	@Test
	@DisplayName("SNMPv3 users with their protocols and keys")
	void users() {
		List<UsmUser> users = SnmpTrapReceiver.parseUsers(" user1 AES128|SHA authpass1 privpass1 user2 SHA authpass2 ");
		Assertions.assertEquals(2, users.size());
		Assertions.assertEquals("user1", users.get(0).getSecurityName().toString());
		Assertions.assertEquals(AuthSHA.ID, users.get(0).getAuthenticationProtocol());
		Assertions.assertEquals(PrivAES128.ID, users.get(0).getPrivacyProtocol());
		Assertions.assertEquals("user2", users.get(1).getSecurityName().toString());
		Assertions.assertNull(users.get(1).getPrivacyProtocol());
	}
}
