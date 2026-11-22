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
package net.netshot.netshot.access;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.vault.SecretResolver;

@UnitTest
@DisplayName("SNMP access to a device which doesn't answer")
class SnmpNoAnswerTest {

	/** The walked subtree (ifIndex column). */
	private static final String IF_INDEX = "1.3.6.1.2.1.2.2.1.1";

	private static Snmp open(int port) throws IOException {
		return new Snmp(NetworkAddress.getNetworkAddress(InetAddress.getLoopbackAddress()), port,
			DeviceSnmpv2cCommunity.builder().name("c").community(Secret.local("public")).build(), new Snmp.Settings(200, 0), SecretResolver.LOCAL);
	}

	@Test
	@DisplayName("A GET fails when the device doesn't answer")
	void getWithoutAnswer() throws Exception {
		try (DatagramSocket silent = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			Snmp snmp = open(silent.getLocalPort());
			try {
				IOException error = Assertions.assertThrows(IOException.class, () -> snmp.getAsString("1.3.6.1.2.1.1.5.0"));
				Assertions.assertEquals("No SNMP response.", error.getMessage());
			}
			finally {
				snmp.stop();
			}
		}
	}

	@Test
	@DisplayName("A walk fails when the device doesn't answer, instead of returning nothing")
	void walkWithoutAnswer() throws Exception {
		try (DatagramSocket silent = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			Snmp snmp = open(silent.getLocalPort());
			try {
				IOException error = Assertions.assertThrows(IOException.class, () -> snmp.walkAsString(IF_INDEX));
				Assertions.assertEquals("SNMP walk of " + IF_INDEX + " failed: Request timed out.", error.getMessage());
			}
			finally {
				snmp.stop();
			}
		}
	}

	@Test
	@DisplayName("A walk fails when the device stops answering in the middle, instead of returning a part of the subtree")
	void walkInterrupted() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		// An agent which answers the first GETBULK with two rows, then stops answering
		CommandResponder responder = new CommandResponder() {
			@Override
			public <A extends org.snmp4j.smi.Address> void processPdu(CommandResponderEvent<A> event) {
				event.setProcessed(true);
				if (requests.incrementAndGet() > 1) {
					return;
				}
				PDU response = new PDU();
				response.setType(PDU.RESPONSE);
				response.setRequestID(event.getPDU().getRequestID());
				response.add(new VariableBinding(new OID(IF_INDEX + ".1"), new Integer32(1)));
				response.add(new VariableBinding(new OID(IF_INDEX + ".2"), new Integer32(2)));
				try {
					event.getMessageDispatcher().returnResponsePdu(event.getMessageProcessingModel(), event.getSecurityModel(),
						event.getSecurityName(), event.getSecurityLevel(), response, event.getMaxSizeResponsePDU(), event.getStateReference(),
						new StatusInformation());
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
		};
		DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping(new UdpAddress(InetAddress.getLoopbackAddress(), 0));
		MessageDispatcherImpl dispatcher = new MessageDispatcherImpl();
		dispatcher.addMessageProcessingModel(new MPv2c());
		org.snmp4j.Snmp agent = new org.snmp4j.Snmp(dispatcher, transport);
		agent.addCommandResponder(responder);
		agent.listen();
		try {
			Snmp snmp = open(transport.getListenAddress().getPort());
			try {
				IOException error = Assertions.assertThrows(IOException.class, () -> snmp.walkAsString(IF_INDEX));
				Assertions.assertEquals("SNMP walk of " + IF_INDEX + " failed: Request timed out.", error.getMessage());
				Assertions.assertTrue(requests.get() >= 2, "The walk didn't go on after the first answer");
			}
			finally {
				snmp.stop();
			}
		}
		finally {
			agent.close();
		}
	}
}
