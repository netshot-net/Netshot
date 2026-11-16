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
package net.netshot.netshot.platform.database;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Encrypted columns (converter)")
class LocalSecretConverterTest {

	/** The converter, with the default encryption password. */
	private static final LocalSecretConverter CONVERTER = new LocalSecretConverter("NETSHOT");

	@Test
	@DisplayName("A value encrypted by the former Hibernate converter is read")
	void readsHibernateValue() {
		// Written by StringEncryptorConverter (Hibernate) with the default password, before it was removed
		String stored = "gNLFia/O/0LTQDS0Xc1BOzqqRR1lfRgLNhqdWofbXagDZkrlNCQM2XC+JG2MmwBSIX8kXKyugKc=";
		Assertions.assertEquals("S3cr3t-ẞ-value", CONVERTER.from(stored).reveal());
	}

	@Test
	@DisplayName("A value is encrypted, and read back")
	void roundTrip() {
		String secret = "S3cr3t-ẞ-value";
		String stored = CONVERTER.to(LocalSecret.of(secret));
		Assertions.assertNotEquals(secret, stored, "the stored value is encrypted");
		Assertions.assertEquals(secret, CONVERTER.from(stored).reveal());
	}

	@Test
	@DisplayName("A value read is written back as it was read, not encrypted again")
	void writtenBackAsRead() {
		String stored = CONVERTER.to(LocalSecret.of("S3cr3t"));
		Assertions.assertEquals(stored, CONVERTER.to(CONVERTER.from(stored)));
	}

	@Test
	@DisplayName("A value is read without being decrypted")
	void readWithoutDecrypting() {
		// Not a valid encrypted value: reading it works, revealing it fails
		LocalSecret secret = CONVERTER.from("not-encrypted");
		Assertions.assertEquals("LocalSecret[***]", secret.toString());
		Assertions.assertThrows(RuntimeException.class, secret::reveal);
	}

	@Test
	@DisplayName("Null stays null")
	void nullValue() {
		Assertions.assertNull(CONVERTER.from(null));
		Assertions.assertNull(CONVERTER.to(null));
	}

	@Test
	@DisplayName("Each converter uses its own password")
	void ownPassword() {
		String stored = CONVERTER.to(LocalSecret.of("S3cr3t"));
		Assertions.assertEquals("S3cr3t", new LocalSecretConverter("NETSHOT").from(stored).reveal());
		Assertions.assertThrows(RuntimeException.class, () -> new LocalSecretConverter("other").from(stored).reveal());
	}
}
