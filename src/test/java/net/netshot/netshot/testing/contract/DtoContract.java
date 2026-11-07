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
package net.netshot.netshot.testing.contract;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.contract.ApiContractGolden.Format;

/**
 * Expected files of the DTO tests: what a DTO writes (JSON, XML or YAML, in the canonical form of
 * {@link ApiContractGolden}) is compared with a stored file, so the tests need no other object to
 * compute the expectation.
 *
 * <p>Files live under {@code src/test/resources/dto-contract/<test class>/<case>.<extension>}. They are
 * (re)written, instead of compared, when the {@value #UPDATE_PROPERTY} system property is true. A test
 * class registers an instance with {@code @RegisterExtension}: the cases checked without an explicit name
 * are named {@code <test method>-<n>}, {@code n} counting the checks of the method.</p>
 */
public final class DtoContract implements BeforeEachCallback {

	/** System property, set to true to rewrite the expected files. */
	public static final String UPDATE_PROPERTY = "netshot.dto.contract.update";

	/** Directory holding the expected files. */
	public static final Path DIR = Path.of("src", "test", "resources", "dto-contract");

	/**
	 * Start time far away from any value the tests hold: no number is taken for a timestamp generated
	 * while the test runs (the window around the start time is the one of {@link ApiContractGolden}).
	 */
	private static final long NEVER = Long.MIN_VALUE / 4;

	private final Path dir;
	private final ApiContractGolden golden;
	private final Map<String, Integer> counters = new HashMap<>();
	private String method = "";

	/**
	 * Builds the store of a test class.
	 * @param testClass the test class (its simple name is the directory name)
	 */
	public DtoContract(Class<?> testClass) {
		this(testClass, NEVER);
	}

	/**
	 * Builds the store of a test class.
	 * @param testClass the test class (its simple name is the directory name)
	 * @param startTime the time the test started (epoch milliseconds), to turn the timestamps generated while
	 * it runs into placeholders (a test whose objects hold the current time)
	 */
	public DtoContract(Class<?> testClass, long startTime) {
		this.dir = DIR.resolve(testClass.getSimpleName());
		this.golden = new ApiContractGolden(startTime);
	}

	public static boolean isUpdateMode() {
		return Boolean.parseBoolean(System.getProperty(UPDATE_PROPERTY, "false"));
	}

	@Override
	public void beforeEach(ExtensionContext context) {
		this.method = context.getRequiredTestMethod().getName();
	}

	/**
	 * Checks what the DTO writes in JSON and in XML, in a case named after the test method and the number
	 * of checks it did before.
	 * @param json what the DTO writes in JSON
	 * @param xml what the DTO writes in XML
	 */
	public void checkBoth(byte[] json, byte[] xml) {
		this.checkBoth(json, xml, ApiContractGolden.Transform.NONE);
	}

	/**
	 * Checks what the DTO writes in JSON and in XML, in a case named after the test method and the number
	 * of checks it did before.
	 * @param json what the DTO writes in JSON
	 * @param xml what the DTO writes in XML
	 * @param transform the projection to apply to the bodies before they are compared
	 */
	public void checkBoth(byte[] json, byte[] xml, ApiContractGolden.Transform transform) {
		int count = this.counters.merge(this.method, 1, Integer::sum);
		String name = this.method + "-" + count;
		this.check(name, Format.JSON, json, transform);
		this.check(name, Format.XML, xml, transform);
	}

	/**
	 * Checks what the DTO writes.
	 * @param name the case name
	 * @param format the format of the body
	 * @param body the body written by the DTO
	 */
	public void check(String name, Format format, byte[] body) {
		this.check(name, format, body, ApiContractGolden.Transform.NONE);
	}

	/**
	 * Checks what the DTO writes.
	 * @param name the case name
	 * @param format the format of the body
	 * @param body the body written by the DTO
	 * @param transform the projection to apply to the body before it is compared
	 */
	public void check(String name, Format format, byte[] body, ApiContractGolden.Transform transform) {
		this.checkCanonical(name, format, this.golden.canonicalize(format, body, transform, false));
	}

	/**
	 * Checks a body already in canonical form.
	 * @param name the case name
	 * @param format the format of the body
	 * @param actual the canonical text
	 */
	public void checkCanonical(String name, Format format, String actual) {
		String fileName = name.replaceAll("[^A-Za-z0-9._-]+", "_") + "." + format.extension;
		if (isUpdateMode()) {
			ApiContractGolden.writeGolden(this.dir, fileName, actual);
			return;
		}
		String expected = ApiContractGolden.readGolden(this.dir, fileName);
		Assertions.assertNotNull(expected, "No expected file " + this.dir.resolve(fileName)
			+ " (run with -D" + UPDATE_PROPERTY + "=true to create it)");
		if (!expected.equals(actual)) {
			Assertions.fail("The " + format + " written for " + this.dir.getFileName() + "/" + fileName + " changed:\n"
				+ ApiContractGolden.diff(expected, actual, fileName));
		}
	}

	/**
	 * Checks a text, for example the description of the object a request is read into.
	 * @param name the case name (the extension is {@code txt})
	 * @param actual the text
	 */
	public void checkText(String name, String actual) {
		String fileName = name.replaceAll("[^A-Za-z0-9._-]+", "_") + ".txt";
		if (isUpdateMode()) {
			ApiContractGolden.writeGolden(this.dir, fileName, actual);
			return;
		}
		String expected = ApiContractGolden.readGolden(this.dir, fileName);
		Assertions.assertNotNull(expected, "No expected file " + this.dir.resolve(fileName)
			+ " (run with -D" + UPDATE_PROPERTY + "=true to create it)");
		Assertions.assertEquals(expected, actual, "The text of " + this.dir.getFileName() + "/" + fileName + " changed");
	}

	/**
	 * Renders the description of an object (its class and its sorted properties), one property per line.
	 * Dates are masked, as they are generated while the test runs.
	 * @param bean the object, or null for a request which is refused
	 * @param description the properties of the object
	 * @return the text
	 */
	public static String render(Object bean, Map<String, Object> description) {
		if (bean == null) {
			return "REFUSED\n";
		}
		StringBuilder text = new StringBuilder(bean.getClass().getSimpleName()).append('\n');
		new TreeMap<>(description).forEach((key, value) -> text.append(key).append('=').append(renderValue(value)).append('\n'));
		return text.toString();
	}

	/**
	 * Describes a credential field the way the former entities exposed it: its local value (in clear text), its
	 * Vault path, and the Vault instance as a pending one ({@code resolvedXxxVaultInstanceId}, the stored
	 * {@code xxxVaultInstanceId} being unset).
	 * @param description the properties of the object, to add the field to
	 * @param name the name of the field
	 * @param secret the field (may be null)
	 */
	public static void describeSecret(Map<String, Object> description, String name, Secret secret) {
		LocalSecret local = Secret.localValue(secret);
		description.put(name, local == null ? null : local.reveal());
		description.put("resolved" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "VaultInstanceId", Secret.vaultInstanceId(secret));
		description.put(name + "VaultInstanceId", null);
		description.put(name + "VaultPath", Secret.vaultPath(secret));
	}

	private static String renderValue(Object value) {
		if (value instanceof Date) {
			return "<date>";
		}
		if (value instanceof Map<?, ?> map) {
			StringBuilder text = new StringBuilder("{");
			new TreeMap<>(map).forEach((key, item) -> text.append(text.length() > 1 ? ", " : "").append(key).append('=').append(renderValue(item)));
			return text.append('}').toString();
		}
		if (value instanceof Collection<?> collection) {
			StringBuilder text = new StringBuilder("[");
			collection.forEach(item -> text.append(text.length() > 1 ? ", " : "").append(renderValue(item)));
			return text.append(']').toString();
		}
		return String.valueOf(value);
	}
}
