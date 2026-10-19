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
package net.netshot.netshot.inventory.device;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
public class NetworkAddressTest {

	@Nested
	@DisplayName("IPv4 Address tests")
	class Network4AddressTest {

		@Test
		@DisplayName("String IPv4 address with length")
		void stringAddressWithLength() throws UnknownHostException {
			Network4Address address = new Network4Address("192.0.2.1", 24);

			Assertions.assertEquals(address.getPrefix(), "192.0.2.1/24", "The prefixes don't match");
			Assertions.assertEquals(address.getInetAddress(), InetAddress.getByName("192.0.2.1"),
				"The InetAddresses don't match");
			Assertions.assertEquals(address.getIp(), "192.0.2.1", "The IPs don't match");
			Assertions.assertEquals(address.getPrefixLength(), 24, "The prefix lengths don't match");
			Assertions.assertTrue(address.isNormalUnicast(), "The prefix is not unicast");
			Assertions.assertFalse(address.isBroadcast(), "The prefix is seen as broadcast");
			Assertions.assertFalse(address.isMulticast(), "The prefix is seen as multicast");
		}

		@Test
		@DisplayName("String IPv4 address with mask")
		void stringAddressWithMask() throws UnknownHostException {
			Network4Address address = new Network4Address("10.1.1.16", "255.255.255.128");

			Assertions.assertEquals(address.getPrefix(), "10.1.1.16/25", "The prefixes don't match");
			Assertions.assertEquals(address.getInetAddress(), InetAddress.getByName("10.1.1.16"),
				"The InetAddresses don't match");
			Assertions.assertEquals(address.getIp(), "10.1.1.16", "The IPs don't match");
			Assertions.assertEquals(address.getPrefixLength(), 25, "The prefix lengths don't match");
			Assertions.assertTrue(address.isNormalUnicast(), "The prefix is not normal unicast");
		}

		@Test
		@DisplayName("Int IPv4 address with prefix length")
		void intAddress() throws UnknownHostException {
			Network4Address address = new Network4Address(0x0A0A1010, 16);

			Assertions.assertEquals(address.getPrefix(), "10.10.16.16/16", "The prefixes don't match");
			Assertions.assertEquals(address.getInetAddress(), InetAddress.getByName("10.10.16.16"),
				"The InetAddresses don't match");
			Assertions.assertEquals(address.getIp(), "10.10.16.16", "The IPs don't match");
			Assertions.assertEquals(address.getPrefixLength(), 16, "The prefix lengths don't match");
			Assertions.assertTrue(address.isNormalUnicast(), "The prefix is not normal unicast");
		}

		@Test
		@DisplayName("Multicast IPv4 Address")
		void multicastAddress() throws UnknownHostException {
			Network4Address address = new Network4Address("239.0.0.16");

			Assertions.assertEquals(address.getPrefix(), "239.0.0.16/32", "The prefixes don't match");
			Assertions.assertTrue(address.isMulticast(), "The prefix is not multicast");
			Assertions.assertFalse(address.isBroadcast(), "The prefix is seen as broadcast");
			Assertions.assertFalse(address.isNormalUnicast(), "The prefix is seen as normal unicast");
		}

		@Test
		@DisplayName("Broadcast IPv4 Address")
		void broadcastAddress() throws UnknownHostException {
			Network4Address address = new Network4Address("255.255.255.255");

			Assertions.assertTrue(address.isBroadcast(), "The prefix is not broadcast");
			Assertions.assertFalse(address.isMulticast(), "The prefix is seen as multicast");
			Assertions.assertFalse(address.isNormalUnicast(), "The prefix is seen as unicast");
		}

		@Test
		@DisplayName("Directed broadcast IPv4 Address")
		void directedBroadcastAddress() throws UnknownHostException {
			Network4Address address = new Network4Address("192.168.0.255", 24);

			Assertions.assertEquals(address.getPrefix(), "192.168.0.255/24", "The prefixes don't match");
			Assertions.assertTrue(address.isDirectedBroadcast(), "The prefix is not directed broadcast");
			Assertions.assertFalse(address.isBroadcast(), "The prefix is seen as broadcast");
			Assertions.assertFalse(address.isMulticast(), "The prefix is seen as multicast");
			Assertions.assertFalse(address.isNormalUnicast(), "The prefix is seen as normal unicast");
		}

		@Test
		@DisplayName("/32 IPv4 Address")
		void slash32Address() throws UnknownHostException {
			Network4Address address = new Network4Address("1.1.1.1", 32);

			Assertions.assertEquals(address.getPrefix(), "1.1.1.1/32", "The prefixes don't match");
			Assertions.assertTrue(address.isNormalUnicast(), "The prefix is not normal unicast");
			Assertions.assertFalse(address.isDirectedBroadcast(), "The prefix is seen as directed broadcast");
		}

		@Test
		@DisplayName("IPv4 Address comparison")
		void addressComparison() throws UnknownHostException {
			Network4Address address1 = new Network4Address("192.168.1.0", 24);
			Network4Address address2 = new Network4Address("192.168.1.0", 24);
			Network4Address address3 = new Network4Address("192.168.1.0", 25);
			Network4Address address4 = new Network4Address("192.168.1.129", 24);

			Assertions.assertEquals(address1, address2, "The IPs are not equal");
			Assertions.assertNotEquals(address1, address3, "The IPs are equal");
			Assertions.assertTrue(address1.contains(address2), "192.168.1.0/24 doesn't contain 192.168.1.0");
			Assertions.assertTrue(address1.contains(address4), "192.168.1.0/24 doesn't contain 192.168.1.129");
			Assertions.assertFalse(address3.contains(address4), "192.168.1.0/25 contains 192.168.1.129");
		}

		@Test
		@DisplayName("IPv4 subnet min/max")
		void ubnetMinMax() throws UnknownHostException {
			Network4Address address = new Network4Address("192.0.2.1", 24);

			Assertions.assertEquals(address.getPrefix(), "192.0.2.1/24", "The prefixes don't match");

			Network4Address min = new Network4Address(address.getSubnetMin(), address.getPrefixLength());
			Network4Address max = new Network4Address(address.getSubnetMax(), address.getPrefixLength());

			Assertions.assertEquals(min.getPrefix(), "192.0.2.0/24", "The min IPs don't match");
			Assertions.assertEquals(max.getPrefix(), "192.0.2.255/24", "The max IPs don't match");
		}

		@Test
		@DisplayName("IPv4 without mask length")
		void noMask() throws UnknownHostException {
			Network4Address address = new Network4Address("1.1.1.1");

			Assertions.assertTrue(address.isNormalUnicast(), "The prefix is not normal unicast");
		}
	}

	@Nested
	@DisplayName("IPv6 Address tests")
	class Network6AddressTest {

		@Test
		@DisplayName("String IPv6 address with length")
		void stringAddressWithLength() throws UnknownHostException {
			Network6Address address = new Network6Address("2016:1:2:3::16", 64);

			Assertions.assertEquals(address.getPrefix(), "2016:1:2:3:0:0:0:16/64", "The prefixes don't match");
			Assertions.assertEquals(address.getInetAddress(), InetAddress.getByName("2016:1:2:3::16"),
				"The InetAddresses don't match");
			Assertions.assertEquals(address.getIp(), "2016:1:2:3:0:0:0:16", "The IPs don't match");
			Assertions.assertEquals(address.getPrefixLength(), 64, "The prefix lengths don't match");
		}

		@Test
		@DisplayName("IPv6 Global Unicast")
		void globalUnicastAddress() throws UnknownHostException {
			Network6Address address = new Network6Address("2016:1:2:3::16", 64);

			Assertions.assertTrue(address.isGlobalUnicast(), "The address is not global unicast");
			Assertions.assertFalse(address.isMulticast(), "The address is multicast");
			Assertions.assertFalse(address.isLinkLocal(), "The address is link local");
		}

		@Test
		@DisplayName("IPv6 Multicast")
		void multicastAddress() throws UnknownHostException {
			Network6Address address = new Network6Address("ff01::1", 64);

			Assertions.assertTrue(address.isMulticast(), "The address is not multicast");
			Assertions.assertFalse(address.isGlobalUnicast(), "The address is global unicast");
			Assertions.assertFalse(address.isLinkLocal(), "The address is link local");
		}

		@Test
		@DisplayName("IPv6 Link Local")
		void linkLocalAddress() throws UnknownHostException {
			Network6Address address = new Network6Address("fe80::1", 128);

			Assertions.assertTrue(address.isLinkLocal(), "The address is not link local");
			Assertions.assertFalse(address.isMulticast(), "The address is multicast");
			Assertions.assertFalse(address.isGlobalUnicast(), "The address is global unicast");
		}

		@Test
		@DisplayName("IPv6 Other")
		void otherAddress() throws UnknownHostException {
			Network6Address address1 = new Network6Address("4001:1:2::", 64);
			Network6Address address2 = new Network6Address("600:1:2::", 64);
			Network6Address address3 = new Network6Address("fc01:1:2::", 64);

			Assertions.assertFalse(address1.isLinkLocal(), "The address 1 is link local");
			Assertions.assertFalse(address1.isMulticast(), "The address 1 is multicast");
			Assertions.assertFalse(address1.isGlobalUnicast(), "The address 1 is global unicast");
			Assertions.assertFalse(address2.isLinkLocal(), "The address 2 is link local");
			Assertions.assertFalse(address2.isMulticast(), "The address 2 is multicast");
			Assertions.assertFalse(address2.isGlobalUnicast(), "The address 2 is global unicast");
			Assertions.assertFalse(address3.isLinkLocal(), "The address 3 is link local");
			Assertions.assertFalse(address3.isMulticast(), "The address 3 is multicast");
			Assertions.assertFalse(address3.isGlobalUnicast(), "The address 3 is global unicast");
		}

		@Test
		@DisplayName("IPv6 Address comparison")
		void addressComparison() throws UnknownHostException {
			Network6Address address1 = new Network6Address("2016:1:2:3::", 64);
			Network6Address address2 = new Network6Address("2016:1:2:3::", 64);
			Network6Address address3 = new Network6Address("2016:1:2:3::", 96);
			Network6Address address4 = new Network6Address("2016:1:2:3:ffff:0:0:16", 64);
			Network6Address address5 = new Network6Address("2016:1:2:3:ffff::", 96);

			Assertions.assertEquals(address1, address2, "The IPs are not equal");
			Assertions.assertNotEquals(address1, address3, "The IPs are equal");
			Assertions.assertTrue(address1.contains(address2), "2016:1:2:3::/64 doesn't contain 2016:1:2:3::");
			Assertions.assertTrue(address1.contains(address4), "2016:1:2:3::/64 doesn't contain 2016:1:2:3::ffff:0:0:16");
			Assertions.assertFalse(address3.contains(address4), "2016:1:2:3::/96 contains 2016:1:2:3::ffff:0:0:16");
			Assertions.assertTrue(address5.contains(address4), "2016:1:2:3:ffff::/96 doesn't contain 2016:1:2:3::ffff:0:0:16");
		}

	}

}
