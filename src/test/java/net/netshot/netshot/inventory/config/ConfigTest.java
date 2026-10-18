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
package net.netshot.netshot.inventory.config;

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Device config tests")
public class ConfigTest {

	@Test
	@DisplayName("Config line parents")
	void configLineParents() {
		String[] configLines = new String[] {
			/* 00 */ "version 16",
			/* 01 */ "!",
			/* 02 */ "service timestamps debug datetime msec",
			/* 03 */ "no service password-encryption",
			/* 04 */ "",
			/* 05 */ "vrf definition VRF1",
			/* 06 */ " rd 16:16",
			/* 07 */ " address-family ipv4",
			/* 08 */ " exit-address-family",
			/* 09 */ "!",
			/* 10 */ "!",
			/* 11 */ "ip cef",
			/* 12 */ "no ipv6 cef",
			/* 13 */ "!",
			/* 14 */ "!",
			/* 15 */ "spanning-tree mode rapid-pvst",
			/* 16 */ "!",
			/* 17 */ "interface GigabitEthernet0/1",
			/* 18 */ " description Some description",
			/* 19 */ " ip address 192.168.1.254 255.255.255.0",
			/* 20 */ " bandwidth 100000",
			/* 21 */ " negotiation auto",
			/* 22 */ "!",
			/* 23 */ "interface GigabitEthernet0/2",
			/* 24 */ " description Some description",
			/* 25 */ " ip address 192.168.2.254 255.255.255.0",
			/* 26 */ " ip ospf network point-to-point",
			/* 27 */ " negotiation auto",
			/* 28 */ " shutdown",
			/* 29 */ "!",
			/* 30 */ "router bgp 16",
			/* 31 */ " bgp log-neighbor-changes",
			/* 32 */ " no bgp default ipv4-unicast",
			/* 33 */ " neighbor 192.168.255.1 remote-as 17",
			/* 34 */ " !",
			/* 35 */ " address-family ipv4",
			/* 36 */ "  neigbor 192.168.255.1 activate",
			/* 37 */ " exit-address-family",
			/* 38 */ " !",
			/* 39 */ " address-family ipv4 vrf VRF1",
			/* 40 */ "  neighbor 192.168.254.3 remote-as 18",
			/* 41 */ "  neighbor 192.168.254.3 activate",
			/* 42 */ " exit-address-family",
			/* 43 */ "!",
			/* 44 */ "ip forward-protocol nd",
			/* 45 */ "!",
			/* 46 */ "no ip http server",
			/* 47 */ "end",
			/* 48 */ ""
		};
		int[] parents = new int[] {
			-1, -1, -1, -1, -1, -1,
			5, 5, 5,
			-1, -1, -1, -1, -1, -1, -1, -1, -1,
			17, 17, 17, 17,
			-1, -1,
			23, 23, 23, 23, 23,
			-1, -1,
			30, 30, 30, 30, 30,
			35,
			30, 30, 30,
			39, 39,
			30,
			-1, -1, -1, -1, -1, -1,
		};
		int[] computedParents = Config.getLineParents(Arrays.asList(configLines));
		Assertions.assertArrayEquals(parents, computedParents,
			"Parent line indices do not match the expected ones");
	}

}
