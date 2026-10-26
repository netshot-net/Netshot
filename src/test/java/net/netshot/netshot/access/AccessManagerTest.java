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
package net.netshot.netshot.access;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.access.AccessManager.Resolution;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver.AccessDefinition;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * Unit tests for {@link AccessManager}, the generalized lazy connect +
 * credential-set fallback engine that replaced the triplicated SSH/Telnet/SNMP
 * loops previously duplicated in {@code DeviceScript.connectRun}.
 */
@DatabaseTest
@DisplayName("AccessManager tests")
public class AccessManagerTest {

	/** A minimal fake {@link Client} whose connect() behavior is scripted by the test. */
	private final class FakeClient implements Client {
		private final AtomicInteger connectCount;
		private final IOException failure;

		FakeClient(AtomicInteger connectCount, IOException failure) {
			this.connectCount = connectCount;
			this.failure = failure;
		}

		@Override
		public void connect() throws IOException {
			this.connectCount.incrementAndGet();
			if (this.failure != null) {
				throw this.failure;
			}
		}

		@Override
		public void disconnect() {
		}
	}

	private AccessDefinition sshAccess(String name) {
		return new AccessDefinition(name, DriverProtocol.SSH, null, null, null, null, 22);
	}

	private AccessDefinition telnetAccess(String name) {
		return new AccessDefinition(name, DriverProtocol.TELNET, null, null, null, null, 23);
	}

	private DeviceDraft fakeDevice() {
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		return new DeviceDraft(Device.create("CiscoIOS12", null, domain, "test"));
	}

	// The auto-try pool of a stored device, read from the database
	private static Supplier<List<DeviceCredentialSet>> autoPool(DeviceDraft device) {
		return () -> TestWiring.get().transactions().read(dsl -> new CredentialSetRepository().findUsableInDomain(dsl, device.getMgmtDomain().id()));
	}

	@Test
	@DisplayName("Resolution is lazy: no connect() attempt happens before first use")
	void resolutionIsLazy() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet cred = DeviceSshAccount.builder().username("admin").password("admin").superPassword(null).name("cred1").build();
		DeviceAccess sshAccess = DeviceAccess.of("ssh");
		sshAccess = sshAccess.toBuilder().globalCredentialSet(cred).build();
		device.putDeviceAccess(sshAccess);

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
			(_, _) -> new FakeClient(connectCount, null));

		Assertions.assertEquals(0, connectCount.get(), "No connection attempt should happen before first use");
		Assertions.assertNull(resolution.getCurrentClient());

		resolution.ensureResolved(true);
		Assertions.assertEquals(1, connectCount.get(), "Exactly one connection attempt should happen on first use");
	}

	@Test
	@DisplayName("Auto-try cycles through the supplied credentials in order until one succeeds")
	void autoTryCyclesScopedCredentials() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet credA = DeviceSshAccount.builder().username("admin").password("wrong").superPassword(null).name("credA").build();
		DeviceCredentialSet credB = DeviceSshAccount.builder().username("admin").password("right").superPassword(null).name("credB").build();

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(),
			new LinkedHashSet<>(List.of(credA, credB)));
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (_, credentialSet) -> {
			if (credentialSet == credA) {
				return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
			}
			return new FakeClient(connectCount, null);
		});

		Client client = resolution.ensureResolved(true);
		Assertions.assertNotNull(client);
		Assertions.assertEquals(credB, resolution.getCurrentCredentialSet(), "Should have connected using credB");
		Assertions.assertEquals(2, connectCount.get(), "Both credentials should have been attempted (credA then credB)");
	}

	@Test
	@DisplayName("A non-auth IOException aborts remaining candidates of that access, but a different access is still tried")
	void connectFailureAbortsOnlyThatAccess() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet sshCred = DeviceSshAccount.builder().username("admin").password("admin").superPassword(null).name("sshCred").build();
		DeviceCredentialSet telnetCred = DeviceTelnetAccount.builder().username("admin").password("admin").superPassword(null).name("telnetCred").build();
		DeviceAccess sshAccess = DeviceAccess.of("ssh");
		sshAccess = sshAccess.toBuilder().globalCredentialSet(sshCred).build();
		device.putDeviceAccess(sshAccess);
		DeviceAccess telnetAccess = DeviceAccess.of("telnet");
		telnetAccess = telnetAccess.toBuilder().globalCredentialSet(telnetCred).build();
		device.putDeviceAccess(telnetAccess);

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh"), telnetAccess("telnet")),
			(accessDef, _) -> {
				if ("ssh".equals(accessDef.getName())) {
					return new FakeClient(connectCount, new IOException("connection refused"));
				}
				return new FakeClient(connectCount, null);
			});

		Client client = resolution.ensureResolved(true);
		Assertions.assertNotNull(client);
		Assertions.assertEquals("telnet", resolution.getCurrentAccessDef().getName(),
			"Should have fallen back to the Telnet access after SSH was unreachable");
		Assertions.assertEquals(telnetCred, resolution.getCurrentCredentialSet());
	}

	@Test
	@DisplayName("Manual mode (autoTryCredentials=false) surfaces an AccessAuthenticationException instead of auto-cycling")
	void manualModeSurfacesAuthFailure() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet credA = DeviceSshAccount.builder().username("admin").password("wrong").superPassword(null).name("credA").build();
		DeviceCredentialSet credB = DeviceSshAccount.builder().username("admin").password("right").superPassword(null).name("credB").build();

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(),
			new LinkedHashSet<>(List.of(credA, credB)));
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (_, credentialSet) -> {
			if (credentialSet == credA) {
				return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
			}
			return new FakeClient(connectCount, null);
		});

		Assertions.assertThrows(AccessAuthenticationException.class, () -> resolution.ensureResolved(false),
			"The first (failing) candidate should surface as a catchable authentication error, not auto-cycle");
		Assertions.assertEquals(1, connectCount.get(), "Only the first candidate should have been tried so far");
		Assertions.assertNull(resolution.getCurrentClient());

		boolean advanced = resolution.tryNextCredentials();
		Assertions.assertTrue(advanced, "tryNextCredentials() should move to credB and succeed");
		Assertions.assertEquals(credB, resolution.getCurrentCredentialSet());
		Assertions.assertEquals(2, connectCount.get());
	}

	@Test
	@DisplayName("Exhausting all candidates without any credential configured throws immediately")
	void noCredentialsConfigured() {
		DeviceDraft device = fakeDevice();
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
			(_, _) -> new FakeClient(new AtomicInteger(), null));

		Assertions.assertThrows(InvalidCredentialsException.class, () -> resolution.ensureResolved(true));
	}

	@Test
	@DisplayName("A per-access global credential pin is used exclusively")
	void globalCredentialPinResolution() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet pinnedCred = DeviceSshAccount.builder().username("admin").password("pinned").superPassword(null).name("pinnedCred").build();

		DeviceAccess access = DeviceAccess.of("ssh");
		access = access.toBuilder().globalCredentialSet(pinnedCred).build();
		device.putDeviceAccess(access);

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
			(_, _) -> new FakeClient(connectCount, null));

		Client client = resolution.ensureResolved(true);
		Assertions.assertNotNull(client);
		Assertions.assertEquals(pinnedCred, resolution.getCurrentCredentialSet(),
			"The pinned global credential set should be used");
		Assertions.assertEquals(1, connectCount.get(), "Only the pinned candidate should have been tried");
	}

	@Test
	@DisplayName("A per-access owned specific credential pin is used exclusively")
	void specificCredentialPinResolution() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet specificCred = DeviceSshAccount.builder().username("admin").password("specific").superPassword(null).name("DEVICESPECIFIC-test").build();

		DeviceAccess access = DeviceAccess.of("ssh");
		access = access.toBuilder().specificCredentialSet(specificCred).build();
		device.putDeviceAccess(access);

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
			(_, _) -> new FakeClient(connectCount, null));

		Client client = resolution.ensureResolved(true);
		Assertions.assertNotNull(client);
		Assertions.assertEquals(specificCred, resolution.getCurrentCredentialSet(),
			"The pinned specific credential set should be used");
		Assertions.assertEquals(1, connectCount.get(), "Only the pinned candidate should have been tried");
	}

	@Test
	@DisplayName("A pinned credential that fails auth does NOT fall back to auto-try")
	void pinnedCredentialFailureDoesNotFallBackToAutoTry() {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet pinnedCred = DeviceSshAccount.builder().username("admin").password("wrong").superPassword(null).name("pinnedCred").build();

		DeviceAccess access = DeviceAccess.of("ssh");
		access = access.toBuilder().globalCredentialSet(pinnedCred).build();
		device.putDeviceAccess(access);

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (_, credentialSet) -> {
			if (credentialSet == pinnedCred) {
				return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
			}
			return new FakeClient(connectCount, null);
		});

		Assertions.assertThrows(InvalidCredentialsException.class, () -> resolution.ensureResolved(true),
			"A failed pin must not fall back to auto-try");
		Assertions.assertEquals(1, connectCount.get(), "Only the pinned candidate should ever have been tried");
	}

	@Test
	@DisplayName("Auto (no pin configured) resolves via the domain-scoped credential pool, cycling in order")
	void autoModeUsesDomainScopedPool() throws IOException {
		try {
			Domain domain = TestDevices.addDomain("Auto pool test domain", "");
			DeviceDraft device = new DeviceDraft(TestDevices.add(Device.create("CiscoIOS12", null, domain, "test").toBuilder().accesses(List.of(DeviceAccess.of("ssh"))).build()));
			DeviceCredentialSet credA = DeviceSshAccount.builder().username("admin").password("wrong").superPassword(null).name("autoPoolCredA").build();
			DeviceCredentialSet credB = DeviceSshAccount.builder().username("admin").password("right").superPassword(null).name("autoPoolCredB").build();
			TestDevices.addCredentialSet(credA);
			TestDevices.addCredentialSet(credB);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), autoPool(device), device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (_, credentialSet) -> {
				if (credentialSet.name().equals("autoPoolCredA")) {
					return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
				}
				return new FakeClient(connectCount, null);
			});

			Client client = resolution.ensureResolved(true);
			Assertions.assertNotNull(client);
			Assertions.assertEquals("autoPoolCredB", resolution.getCurrentCredentialSet().name(),
				"Should have connected using the second domain-pool credential after the first failed");
			Assertions.assertEquals(2, connectCount.get(), "Both domain-pool credentials should have been attempted");
		}
		finally {
			TestDevices.deleteAll();
		}
	}

	@Test
	@DisplayName("A successful auto-pool connection is pinned as the access's global credential set")
	void autoModeSuccessPinsCredentialSet() throws IOException {
		try {
			Domain domain = TestDevices.addDomain("Auto pin test domain", "");
			DeviceDraft device = new DeviceDraft(TestDevices.add(Device.create("CiscoIOS12", null, domain, "test").toBuilder().accesses(List.of(DeviceAccess.of("ssh"))).build()));
			DeviceCredentialSet credA = DeviceSshAccount.builder().username("admin").password("wrong").superPassword(null).name("autoPinCredA").build();
			DeviceCredentialSet credB = DeviceSshAccount.builder().username("admin").password("right").superPassword(null).name("autoPinCredB").build();
			TestDevices.addCredentialSet(credA);
			TestDevices.addCredentialSet(credB);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), autoPool(device), device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (_, credentialSet) -> {
				if (credentialSet.name().equals("autoPinCredA")) {
					return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
				}
				return new FakeClient(connectCount, null);
			});

			Client client = resolution.ensureResolved(true);
			Assertions.assertNotNull(client);
			resolution.confirmCredentialWorks();
			TestWiring.get().transactions().write(dsl -> new DeviceRepository().saveDraft(dsl, device));

			Assertions.assertNotNull(device.getDeviceAccess("ssh"),
				"A DeviceAccess row should have been created to hold the pin");
			Assertions.assertEquals("autoPinCredB", device.getDeviceAccess("ssh").globalCredentialSet().name(),
				"The successful credential set should be pinned as the access's global credential set");

			// Confirm the pin was actually persisted, not just changed in memory.
			Device reloaded = TestDevices.find(device.getId());
			Assertions.assertEquals("autoPinCredB",
				reloaded.getDeviceAccess("ssh").globalCredentialSet().name(),
				"The pin should survive a reload from the database");
		}
		finally {
			TestDevices.deleteAll();
		}
	}

	@Test
	@DisplayName("confirmCredentialWorks() does not pin an already-pinned (global) credential")
	void confirmCredentialWorksDoesNotRePinAlreadyPinnedAccess() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceCredentialSet pinnedCred = DeviceSshAccount.builder().username("admin").password("pinned").superPassword(null).name("pinnedCred").build();
		DeviceAccess access = DeviceAccess.of("ssh");
		access = access.toBuilder().globalCredentialSet(pinnedCred).build();
		device.putDeviceAccess(access);

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
			(_, _) -> new FakeClient(connectCount, null));

		resolution.ensureResolved(true);
		resolution.confirmCredentialWorks();

		Assertions.assertSame(pinnedCred, device.getDeviceAccess("ssh").globalCredentialSet(),
			"An already-pinned access must not be re-pinned/altered by confirmCredentialWorks()");
	}

	@Test
	@DisplayName("An access with no DeviceAccess row is never used, even with compatible domain-pool credentials")
	void accessWithNoRowIsNeverUsed() throws IOException {
		try {
			Domain domain = TestDevices.addDomain("No-row test domain", "");
			// Deliberately no DeviceAccess row for "ssh" at all.
			DeviceDraft device = new DeviceDraft(TestDevices.add(Device.create("CiscoIOS12", null, domain, "test")));
			DeviceCredentialSet cred = DeviceSshAccount.builder().username("admin").password("admin").superPassword(null).name("noRowCred").build();
			TestDevices.addCredentialSet(cred);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), autoPool(device), device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(_, _) -> new FakeClient(connectCount, null));

			Assertions.assertThrows(InvalidCredentialsException.class, () -> resolution.ensureResolved(true),
				"No DeviceAccess row means the access is never used, regardless of eligible domain-pool credentials");
			Assertions.assertEquals(0, connectCount.get(), "No connection attempt should ever happen");
		}
		finally {
			TestDevices.deleteAll();
		}
	}

	@Test
	@DisplayName("A one-time credential set is tried regardless of whether a DeviceAccess row exists")
	void oneTimeCredentialSetBypassesEnabledCheck() throws IOException {
		DeviceDraft device = fakeDevice();
		// Deliberately no DeviceAccess row for "ssh" at all.
		DeviceCredentialSet oneTimeCred = DeviceSshAccount.builder().username("admin").password("admin").superPassword(null).name("oneTimeCred").build();

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(),
			new LinkedHashSet<>(List.of(oneTimeCred)));
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
			(_, _) -> new FakeClient(connectCount, null));

		Client client = resolution.ensureResolved(true);
		Assertions.assertNotNull(client, "A one-time credential set should be tried even with no DeviceAccess row");
		Assertions.assertEquals(oneTimeCred, resolution.getCurrentCredentialSet());
	}

	@Test
	@DisplayName("A successful connection removes sibling accesses in the same group")
	void confirmCredentialWorksRemovesSiblingAccessesInSameGroup() throws IOException {
		DeviceDraft device = fakeDevice();
		DeviceAccess sshRow = DeviceAccess.of("ssh");
		sshRow = sshRow.toBuilder().globalCredentialSet(DeviceSshAccount.builder().username("admin").password("admin").superPassword(null).name("sshCred").build()).build();
		device.putDeviceAccess(sshRow);
		DeviceAccess telnetRow = DeviceAccess.of("telnet");
		telnetRow = telnetRow.toBuilder().globalCredentialSet(DeviceTelnetAccount.builder().username("admin").password("admin").superPassword(null).name("telnetCred").build()).build();
		device.putDeviceAccess(telnetRow);

		AtomicInteger connectCount = new AtomicInteger(0);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(), null);
		Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
			(_, _) -> new FakeClient(connectCount, null));

		resolution.ensureResolved(true);
		Assertions.assertNotNull(device.getDeviceAccess("telnet"), "Sanity check: telnet access exists before confirming");

		resolution.confirmCredentialWorks();

		Assertions.assertNull(device.getDeviceAccess("telnet"),
			"The sibling telnet access should be removed once ssh is confirmed to work");
		Assertions.assertNotNull(device.getDeviceAccess("ssh"), "The access that succeeded should remain");
	}

	@Test
	@DisplayName("resolveAddress/resolvePort use the device's per-access override when set, for any access name")
	void perAccessOverrideResolution() throws IOException {
		DeviceDraft device = fakeDevice();
		NetworkAddress defaultAddress = NetworkAddress.getNetworkAddress(InetAddress.getByName("10.0.0.1"));

		// A brand new, non-legacy access ("alternateSsh") with no override at all:
		// should fall back to the default address and the driver-declared default port.
		AccessDefinition alternateSsh = new AccessDefinition("alternateSsh", DriverProtocol.SSH, null,
			null, null, null, 2222);
		AccessManager manager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, defaultAddress, new FakeTaskContext(), null);
		Assertions.assertEquals(defaultAddress, manager.resolveAddress(alternateSsh));
		Assertions.assertEquals(2222, manager.resolvePort(alternateSsh));

		// Now add a per-access override for that same access, with both an address and a port.
		DeviceAccess override = DeviceAccess.of("alternateSsh");
		override = override.toBuilder().address("192.168.1.50").build();
		override = override.toBuilder().port(2223).build();
		device.putDeviceAccess(override);
		NetworkAddress overrideAddress = NetworkAddress.getNetworkAddress(InetAddress.getByName("192.168.1.50"));
		Assertions.assertEquals(overrideAddress, manager.resolveAddress(alternateSsh));
		Assertions.assertEquals(2223, manager.resolvePort(alternateSsh));

		// The plain "ssh" access is unaffected by the "alternateSsh" override.
		AccessDefinition ssh = sshAccess("ssh");
		Assertions.assertEquals(defaultAddress, manager.resolveAddress(ssh));
		Assertions.assertEquals(22, manager.resolvePort(ssh));
	}

}
