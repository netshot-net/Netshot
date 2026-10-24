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
package net.netshot.netshot.platform.database;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Encrypted columns (jOOQ converter)")
public class EncryptedStringConverterTest {

	@BeforeAll
	static void config() {
		TestConfig.init(new Properties());
	}

	@Test
	@DisplayName("A value encrypted by the former Hibernate converter is read")
	void readsHibernateValue() {
		// Written by StringEncryptorConverter (Hibernate) with the default password, before it was removed
		String stored = "gNLFia/O/0LTQDS0Xc1BOzqqRR1lfRgLNhqdWofbXagDZkrlNCQM2XC+JG2MmwBSIX8kXKyugKc=";
		Assertions.assertEquals("S3cr3t-ẞ-value", new EncryptedStringConverter().from(stored));
	}

	@Test
	@DisplayName("A value is encrypted, and read back")
	void roundTrip() {
		EncryptedStringConverter jooq = new EncryptedStringConverter();
		String secret = "S3cr3t-ẞ-value";
		Assertions.assertEquals(secret, jooq.from(jooq.to(secret)));
		Assertions.assertNotEquals(secret, jooq.to(secret), "the stored value is encrypted");
	}

	@Test
	@DisplayName("Null stays null")
	void nullValue() {
		EncryptedStringConverter jooq = new EncryptedStringConverter();
		Assertions.assertNull(jooq.from(null));
		Assertions.assertNull(jooq.to(null));
	}
}
