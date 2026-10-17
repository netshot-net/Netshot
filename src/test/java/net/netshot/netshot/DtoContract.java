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
package net.netshot.netshot;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.netshot.netshot.ApiContractGolden.Format;

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
final class DtoContract implements BeforeEachCallback {

	/** System property, set to true to rewrite the expected files. */
	static final String UPDATE_PROPERTY = "netshot.dto.contract.update";

	/** Directory holding the expected files. */
	static final Path DIR = Path.of("src", "test", "resources", "dto-contract");

	private final Path dir;
	private final ApiContractGolden golden;
	private final Map<String, Integer> counters = new HashMap<>();
	private String method = "";

	/**
	 * Builds the store of a test class.
	 * @param testClass the test class (its simple name is the directory name)
	 */
	DtoContract(Class<?> testClass) {
		this(testClass, 0);
	}

	/**
	 * Builds the store of a test class.
	 * @param testClass the test class (its simple name is the directory name)
	 * @param startTime the time the test started (epoch milliseconds), to turn the timestamps generated while
	 * it runs into placeholders
	 */
	DtoContract(Class<?> testClass, long startTime) {
		this.dir = DIR.resolve(testClass.getSimpleName());
		this.golden = new ApiContractGolden(startTime);
	}

	static boolean isUpdateMode() {
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
	void checkBoth(byte[] json, byte[] xml) {
		this.checkBoth(json, xml, ApiContractGolden.Transform.NONE);
	}

	/**
	 * Checks what the DTO writes in JSON and in XML, in a case named after the test method and the number
	 * of checks it did before.
	 * @param json what the DTO writes in JSON
	 * @param xml what the DTO writes in XML
	 * @param transform the projection to apply to the bodies before they are compared
	 */
	void checkBoth(byte[] json, byte[] xml, ApiContractGolden.Transform transform) {
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
	void check(String name, Format format, byte[] body) {
		this.check(name, format, body, ApiContractGolden.Transform.NONE);
	}

	/**
	 * Checks what the DTO writes.
	 * @param name the case name
	 * @param format the format of the body
	 * @param body the body written by the DTO
	 * @param transform the projection to apply to the body before it is compared
	 */
	void check(String name, Format format, byte[] body, ApiContractGolden.Transform transform) {
		this.checkCanonical(name, format, this.golden.canonicalize(format, body, transform, false));
	}

	/**
	 * Checks a body already in canonical form.
	 * @param name the case name
	 * @param format the format of the body
	 * @param actual the canonical text
	 */
	void checkCanonical(String name, Format format, String actual) {
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
}
