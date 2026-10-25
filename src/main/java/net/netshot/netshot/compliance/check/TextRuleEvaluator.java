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
package net.netshot.netshot.compliance.check;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.devicescript.helper.JsDeviceHelper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.log.TaskContext;

/**
 * Evaluates the text-based rules.
 */
public class TextRuleEvaluator implements RuleEvaluator<TextRule> {

	/**
	 * The patterns compiled from a rule.
	 *
	 * @param hierarchy the patterns of the context lines
	 * @param valid false if the rule can't be evaluated
	 * @param pattern the pattern of the text, for a regular expression
	 */
	private record Prepared(List<Pattern> hierarchy, boolean valid, Pattern pattern) {
	}

	private final RuleStateCache<TextRule, Prepared> prepared = new RuleStateCache<>();

	private final DriverRegistry driverRegistry;

	/**
	 * Builds the evaluator.
	 *
	 * @param driverRegistry the loaded device drivers
	 */
	public TextRuleEvaluator(DriverRegistry driverRegistry) {
		this.driverRegistry = driverRegistry;
	}

	@Override
	public Class<TextRule> ruleType() {
		return TextRule.class;
	}

	private static Prepared prepare(TextRule rule) {
		List<Pattern> hierarchy = new ArrayList<Pattern>();
		String context = rule.context();
		if (context != null && context.length() > 0) {
			String[] lines = context.split("\\r?\\n");
			for (String line : lines) {
				try {
					hierarchy.add(Pattern.compile(line));
				}
				catch (Exception e) {
					return new Prepared(hierarchy, false, null);
				}
			}
		}
		if (rule.text() == null || rule.field() == null) {
			return new Prepared(hierarchy, false, null);
		}
		Pattern pattern = null;
		if (rule.regExp()) {
			try {
				pattern = Pattern.compile(rule.text());
			}
			catch (Exception e) {
				return new Prepared(hierarchy, false, null);
			}
		}
		return new Prepared(hierarchy, true, pattern);
	}

	private String normalizeText(String inputText) {
		if (inputText == null) {
			return null;
		}
		return inputText.replaceAll("[\\x0B\\f\\r]", "").replaceAll(" +\\n", "\n");
	}

	private int findIndent(String line) {
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) != ' ') {
				return i;
			}
		}
		return line.length();
	}

	private List<String[]> findSections(String lines, Pattern sectionPattern, String where) {
		return findSections(lines.split("\\n"), sectionPattern, where);
	}

	private List<String[]> findSections(String[] lines, Pattern sectionPattern, String where) {
		List<String[]> blocks = new ArrayList<String[]>();
		StringJoiner joiner = new StringJoiner("\n");
		String title = "";
		int indent = -1;
		for (String line : lines) {
			if (indent >= 0) {
				if (findIndent(line) > indent) {
					joiner.add(line);
				}
				else {
					blocks.add(new String[] { title, joiner.toString() });
					indent = -1;
				}
			}
			if (indent < 0 && sectionPattern.matcher(line).matches()) {
				indent = findIndent(line);
				joiner = new StringJoiner("\n");
				String trimmedLine = line.trim();
				title = where.length() > 0 ? where + " > " + trimmedLine : trimmedLine;
			}
		}
		if (indent >= 0) {
			blocks.add(new String[] { title, joiner.toString() });
		}
		return blocks;
	}

	@Override
	public CheckResult evaluate(TextRule rule, DeviceDraft device, boolean exempted, TaskContext taskContext) {
		if (!rule.enabled()) {
			return CheckResult.of(rule.id(), device.getId(), ResultOption.DISABLED);
		}
		Prepared compiled = this.prepared.get(rule, TextRuleEvaluator::prepare);
		if (!compiled.valid()) {
			return CheckResult.of(rule.id(), device.getId(), ResultOption.INVALIDRULE);
		}
		String deviceDriver = rule.deviceDriver();
		if (deviceDriver != null && !"".equals(deviceDriver) && !deviceDriver.equals(device.getDriver())) {
			return CheckResult.of(rule.id(), device.getId(), ResultOption.NOTAPPLICABLE, "The rule doesn't apply to the device's driver");
		}
		if (exempted) {
			return CheckResult.of(rule.id(), device.getId(), ResultOption.EXEMPTED);
		}
		String field = rule.field();
		String text = rule.text();
		boolean regExp = rule.regExp();
		boolean matchAll = rule.matchAll();
		Pattern pattern = compiled.pattern();
		try {
			JsDeviceHelper deviceHelper = new JsDeviceHelper(this.driverRegistry, device, null, taskContext, true);
			String content = deviceHelper.get(field).toString();
			content = content.replace("\r", "");
			if (rule.normalize()) {
				content = this.normalizeText(content);
			}
			List<String[]> blocks = new ArrayList<String[]>();
			blocks.add(new String[] { "", content });
			for (Pattern blockPattern : compiled.hierarchy()) {
				List<String[]> selectedBlocks = new ArrayList<String[]>();
				for (String[] block : blocks) {
					selectedBlocks.addAll(findSections(block[1], blockPattern, block[0]));
				}
				blocks = selectedBlocks;
			}
			taskContext.debug("Found {} block(s) matching the context.", blocks.size());
			int b = 1;
			for (String[] block : blocks) {
				boolean doesMatch =
					regExp && (matchAll && pattern.matcher(block[1]).matches() || !matchAll && pattern.matcher(block[1]).find())
						|| !regExp && (matchAll && block[1].equals(text) || !matchAll && block[1].contains(text));
				doesMatch = doesMatch ^ rule.invert();
				if (!doesMatch) {
					taskContext.debug("Non matching block, number {} (in [{}])", b++, block[0]);
					return CheckResult.of(rule.id(), device.getId(), ResultOption.NONCONFORMING);
				}
				else if (rule.anyBlock()) {
					taskContext.debug("Matching block, number {} (in [{}])", b++, block[0]);
					return CheckResult.of(rule.id(), device.getId(), ResultOption.CONFORMING);
				}
			}
			return CheckResult.of(rule.id(), device.getId(), ResultOption.CONFORMING);
		}
		catch (StackOverflowError e) {
			taskContext.info("Overflow error (you might want to rewrite the regular expression)");
			return CheckResult.of(rule.id(), device.getId(), ResultOption.INVALIDRULE, "Overflow error.");
		}
		catch (Exception e) {
			taskContext.info("No such field '" + field + "' on this device");
			return CheckResult.of(rule.id(), device.getId(), ResultOption.NOTAPPLICABLE, "No such field.");
		}
		finally {
			taskContext.debug("End of check");
		}
	}

}
