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
package net.netshot.netshot.compliance.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.hibernate.Session;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.devicescript.helper.JsDeviceHelper;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.task.engine.TaskContext;

@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
public class TextRule extends Rule {

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String field;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(length = 10000)
	}))
	@Setter
	private String context;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(length = 10000000)
	}))
	private String text;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private boolean regExp;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private boolean invert;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private boolean anyBlock;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private boolean matchAll;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private boolean normalize;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String deviceDriver;

	private List<Pattern> hierarchy = new ArrayList<Pattern>();

	private boolean prepared;

	private boolean valid;

	private Pattern pattern;

	protected TextRule() {

	}

	public TextRule(String name, Policy policy) {
		super(name, policy);
	}

	public void setText(String text) {
		this.text = text;
		if (this.text != null) {
			this.text = this.text.replace("\r", "");
		}
	}

	@Transient
	@XmlElement
	@JsonView(DefaultView.class)
	public String getDeviceDriverDescription() {
		if ("".equals(deviceDriver)) {
			return "";
		}
		try {
			DeviceDriver d = DeviceDriver.getDriverByName(deviceDriver);
			return d.getDescription();
		}
		catch (Exception e) {
			return "Unknown driver";
		}
	}

	private void prepare() {
		if (prepared) {
			return;
		}
		prepared = true;
		if (context != null && context.length() > 0) {
			String[] lines = context.split("\\r?\\n");
			for (String line : lines) {
				try {
					hierarchy.add(Pattern.compile(line));
				}
				catch (Exception e) {
					return;
				}
			}
		}
		if (text == null || field == null) {
			return;
		}
		if (regExp) {
			try {
				pattern = Pattern.compile(text);
			}
			catch (Exception e) {
				return;
			}
		}
		valid = true;
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
	public CheckResult check(Device device, Session session, TaskContext taskContext) {
		if (!this.isEnabled()) {
			return new CheckResult(this, device, ResultOption.DISABLED);
		}
		prepare();
		if (!valid) {
			return new CheckResult(this, device, ResultOption.INVALIDRULE);
		}
		if (deviceDriver != null && !"".equals(deviceDriver) && !deviceDriver.equals(device.getDriver())) {
			return new CheckResult(this, device, ResultOption.NOTAPPLICABLE, "The rule doesn't apply to the device's driver");
		}
		if (device.isExempted(this)) {
			return new CheckResult(this, device, ResultOption.EXEMPTED);
		}
		try {
			JsDeviceHelper deviceHelper = new JsDeviceHelper(device, null, session, taskContext, true);
			String content = deviceHelper.get(field).toString();
			content = content.replace("\r", "");
			if (isNormalize()) {
				content = this.normalizeText(content);
			}
			List<String[]> blocks = new ArrayList<String[]>();
			blocks.add(new String[] { "", content });
			for (Pattern blockPattern : hierarchy) {
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
				doesMatch = doesMatch ^ invert;
				if (!doesMatch) {
					taskContext.debug("Non matching block, number {} (in [{}])", b++, block[0]);
					return new CheckResult(this, device, ResultOption.NONCONFORMING);
				}
				else if (anyBlock) {
					taskContext.debug("Matching block, number {} (in [{}])", b++, block[0]);
					return new CheckResult(this, device, ResultOption.CONFORMING);
				}
			}
			return new CheckResult(this, device, ResultOption.CONFORMING);
		}
		catch (StackOverflowError e) {
			taskContext.info("Overflow error (you might want to rewrite the regular expression)");
			return new CheckResult(this, device, ResultOption.INVALIDRULE, "Overflow error.");
		}
		catch (Exception e) {
			taskContext.info("No such field '" + field + "' on this device");
			return new CheckResult(this, device, ResultOption.NOTAPPLICABLE, "No such field.");
		}
		finally {
			taskContext.debug("End of check");
		}
	}

	@Override
	public String toString() {
		return "Compliance text-based rule " + id + " (name '" + name + "')";
	}

}
