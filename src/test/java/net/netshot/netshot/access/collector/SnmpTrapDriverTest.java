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
package net.netshot.netshot.access.collector;

import java.net.InetAddress;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.ScopedPDU;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import net.netshot.netshot.access.SnmpSecurity;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestNetwork;

/**
 * Tests the SNMP traps received by the trap receiver, given to the drivers: the communities and users checked, and
 * the snapshot requested when a driver recognizes a configuration change (the Cisco IOS one here).
 */
@ScriptEngineTest
@DisplayName("SNMP traps given to the drivers")
class SnmpTrapDriverTest {

	/** Trap OID of a Cisco configuration change (ccmCLIRunningConfigChanged). */
	private static final OID CISCO_CONFIG_CHANGE = new OID("1.3.6.1.4.1.9.9.43.2.0.2");

	/** Trap OID of a link down, which no driver takes for a configuration change. */
	private static final OID LINK_DOWN = new OID("1.3.6.1.6.3.1.1.5.3");

	/**
	 * A requested snapshot.
	 *
	 * @param drivers the drivers which recognized the trap
	 * @param source the device which sent it
	 */
	private record Request(List<String> drivers, Network4Address source) {
	}

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private SnmpTrapReceiver receiver;

	private int port;

	@BeforeEach
	void start() {
		this.port = TestNetwork.freePort();
		Properties config = new Properties();
		config.setProperty("netshot.snmptrap.port", Integer.toString(this.port));
		config.setProperty("netshot.snmptrap.listenaddress", "127.0.0.1");
		config.setProperty("netshot.snmptrap.community", "netshot-traps");
		config.setProperty("netshot.snmptrap.user", "trapuser SHA trapauth1");
		config.setProperty("netshot.snmptrap.engineid", "80:00:1f:88:80:01:02:03:05");
		TestConfig.init(config);
		this.receiver = new SnmpTrapReceiver(TestDrivers.registry(), (drivers, source) -> this.requests.add(new Request(drivers, source)),
			TestConfig.store());
		this.receiver.start();
	}

	@AfterEach
	void stop() throws Exception {
		this.receiver.stop();
		TestConfig.init(new Properties());
	}

	private long stat(String name) {
		return (Long) this.receiver.stats().get(name);
	}

	private static void await(BooleanSupplier condition, String message) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				Assertions.fail("Timeout: " + message);
			}
			Thread.sleep(20);
		}
	}

	/**
	 * Sends an SNMPv2c (or SNMPv1) trap with the given community and trap OID.
	 *
	 * @param version the SNMP version
	 * @param community the community
	 * @param trapOid the trap OID (SNMPv2c only)
	 */
	private void sendCommunityTrap(int version, String community, OID trapOid) throws Exception {
		try (org.snmp4j.Snmp sender = new org.snmp4j.Snmp(new DefaultUdpTransportMapping())) {
			sender.listen();
			CommunityTarget<UdpAddress> target = new CommunityTarget<>(new UdpAddress(InetAddress.getLoopbackAddress(), this.port),
				new OctetString(community));
			target.setVersion(version);
			PDU pdu;
			if (version == SnmpConstants.version1) {
				PDUv1 v1 = new PDUv1();
				v1.setType(PDU.V1TRAP);
				v1.setEnterprise(new OID("1.3.6.1.4.1.9"));
				v1.setAgentAddress(new IpAddress("127.0.0.1"));
				v1.setGenericTrap(PDUv1.LINKDOWN);
				pdu = v1;
			}
			else {
				pdu = new PDU();
				pdu.setType(PDU.TRAP);
				pdu.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1000)));
				pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, trapOid));
			}
			sender.send(pdu, target);
		}
	}

	/**
	 * Sends an SNMPv3 trap (authentication, no privacy) of a Cisco configuration change.
	 *
	 * @param user the SNMPv3 user
	 * @param authKey the authentication key
	 */
	private void sendV3Trap(String user, String authKey) throws Exception {
		USM usm = SnmpSecurity.usm(new OctetString(MPv3.createLocalEngineID()));
		usm.addUser(new UsmUser(new OctetString(user), AuthSHA.ID, new OctetString(authKey), null, null));
		try (org.snmp4j.Snmp sender = new org.snmp4j.Snmp(SnmpSecurity.dispatcher(usm), new DefaultUdpTransportMapping())) {
			sender.listen();
			UserTarget<UdpAddress> target = new UserTarget<>();
			target.setAddress(new UdpAddress(InetAddress.getLoopbackAddress(), this.port));
			target.setVersion(SnmpConstants.version3);
			target.setSecurityLevel(SecurityLevel.AUTH_NOPRIV);
			target.setSecurityName(new OctetString(user));
			ScopedPDU pdu = new ScopedPDU();
			pdu.setType(PDU.TRAP);
			pdu.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1000)));
			pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, CISCO_CONFIG_CHANGE));
			sender.send(pdu, target);
		}
	}

	@Test
	@DisplayName("A configuration change trap with a valid community requests a snapshot for the drivers recognizing it")
	void configChange() throws Exception {
		this.sendCommunityTrap(SnmpConstants.version2c, "netshot-traps", CISCO_CONFIG_CHANGE);
		await(() -> this.stat("matched") == 1L, "the trap is matched");

		Assertions.assertEquals(1L, this.stat("received"));
		Assertions.assertEquals(1, this.requests.size());
		Assertions.assertTrue(this.requests.get(0).drivers().contains("CiscoIOS12"), this.requests.get(0).drivers().toString());
		Assertions.assertEquals("127.0.0.1", this.requests.get(0).source().getIp());
	}

	@Test
	@DisplayName("Another trap, SNMPv2c or SNMPv1, is received but requests nothing")
	void otherTraps() throws Exception {
		this.sendCommunityTrap(SnmpConstants.version2c, "netshot-traps", LINK_DOWN);
		this.sendCommunityTrap(SnmpConstants.version1, "netshot-traps", null);
		await(() -> this.stat("received") == 2L, "the traps are received");

		Assertions.assertEquals(0L, this.stat("matched"));
		Assertions.assertEquals(List.of(), this.requests);
	}

	@Test
	@DisplayName("A trap with an unknown community is rejected")
	void wrongCommunity() throws Exception {
		this.sendCommunityTrap(SnmpConstants.version2c, "public", CISCO_CONFIG_CHANGE);
		await(() -> this.stat("rejected") == 1L, "the trap is rejected");

		Assertions.assertEquals(0L, this.stat("received"));
		Assertions.assertEquals(List.of(), this.requests);
	}

	@Test
	@DisplayName("An SNMPv3 trap of a configured user is given to the drivers, with a wrong key it is rejected")
	void v3Traps() throws Exception {
		this.sendV3Trap("trapuser", "trapauth1");
		await(() -> this.stat("matched") == 1L, "the trap is matched");
		this.sendV3Trap("trapuser", "wrongkey1");
		await(() -> this.stat("rejected") == 1L, "the trap with a wrong key is rejected");

		Assertions.assertEquals(1, this.requests.size());
		Assertions.assertTrue(this.requests.get(0).drivers().contains("CiscoIOS12"));
	}
}
