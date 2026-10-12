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

/*
 * Netshot script toolbox (JavaScript implementation).
 *
 * Evaluated before any user code (driver, compliance rule, diagnostic,
 * device script), it defines the global `Toolbox` object. The Python
 * implementation (toolbox.py, module netshot.toolbox) mirrors this file
 * and both are checked against the same test corpus (ToolboxTest) - keep
 * them in sync.
 *
 * `Toolbox` is a configurable global property, so that a user script
 * declaring its own top-level `Toolbox` doesn't fail; the loaders use
 * the non-configurable `__netshotToolbox` alias, which can't be shadowed
 * (and also holds the legacy findSections implementation).
 */
(() => {

	const SYNTAXES = ["indent", "braces", "fortios"];

	const toRegExp = (regex, functionName) => {
		if (typeof regex === "string") {
			return new RegExp(regex);
		}
		if (typeof regex === "object" && regex instanceof RegExp) {
			return regex;
		}
		throw `Invalid regex parameter in ${functionName}.`;
	};

	/**
	 * Split setting values: whitespace-separated, with double-quoted parts
	 * (possibly with \-escaped characters and line breaks) unquoted.
	 */
	const splitValues = (text) => {
		const values = [];
		let value = null;
		let quoted = false;
		for (let i = 0; i < text.length; i++) {
			const c = text.charAt(i);
			if (!quoted && /\s/.test(c)) {
				if (value !== null) {
					values.push(value);
					value = null;
				}
				continue;
			}
			if (value === null) {
				value = "";
			}
			if (c === "\"") {
				quoted = !quoted;
			}
			else if (c === "\\" && quoted && i + 1 < text.length) {
				i++;
				value += text.charAt(i);
			}
			else {
				value += c;
			}
		}
		if (value !== null) {
			values.push(value);
		}
		return values;
	};

	/** Parse a "set <key> <values...>" or "unset <key>" line, null if it's not one. */
	const parseSetting = (text) => {
		const match = /^\s*(set|unset)\s+(\S+)(?:\s+([\s\S]*))?$/.exec(text);
		if (match === null) {
			return null;
		}
		return {
			key: match[2],
			unset: match[1] === "unset",
			values: splitValues(match[3] || ""),
		};
	};

	const execRegExp = (regex, text) => {
		regex.lastIndex = 0;
		return regex.exec(text);
	};

	/**
	 * A node of a parsed configuration: one configuration line (or block opening line)
	 * along with its children.
	 */
	class ConfigNode {

		constructor(text, raw, line, indent, parent) {
			/**
			 * The line text, trimmed (and without the block delimiters for some syntaxes),
			 * or with its original indentation when parsed with the indented option.
			 */
			this.text = text;
			/** The original line(s), as found in the source text. */
			this.raw = raw;
			/** The (1-based) line number in the source text, 0 for the root node. */
			this.line = line;
			/** The number of leading whitespace characters, -1 for the root node. */
			this.indent = indent;
			/** The parent node: the node itself for a top-level line, null for the root node. */
			this.parent = parent;
			/** The child nodes. */
			this.children = [];
			/** The regular expression match, on nodes returned by queries. */
			this.match = null;
			/** The source line closing the block, if any (e.g. "}" or "next"). */
			this.closing = null;
			/** Whether this is a comment line. */
			this.isComment = false;
		}

		/** The (raw) source lines of the child nodes, recursively. */
		get lines() {
			const lines = [];
			const walk = (node) => {
				for (const child of node.children) {
					lines.push(child.raw);
					walk(child);
					if (child.closing !== null) {
						lines.push(child.closing);
					}
				}
			};
			walk(this);
			return lines;
		}

		/** The (raw) source text of the child nodes, recursively. */
		get config() {
			return this.lines.join("\n");
		}

		/** The text of the ancestor nodes and of this node, from the top level (empty for the root node). */
		get path() {
			const path = [];
			if (this.parent === null) {
				return path;
			}
			let node = this;
			while (true) {
				path.unshift(node.text);
				if (node.parent === node) {
					break;
				}
				node = node.parent;
			}
			return path;
		}

		_withMatch(match) {
			const result = Object.create(this);
			result.match = match;
			if (this.parent === this) {
				// A top-level line is its own parent
				result.parent = result;
			}
			return result;
		}

		_candidates(recursive) {
			if (!recursive) {
				return this.children;
			}
			const nodes = [];
			const walk = (node) => {
				for (const child of node.children) {
					nodes.push(child);
					walk(child);
				}
			};
			walk(this);
			return nodes;
		}

		/**
		 * Find the child nodes whose text matches the regex.
		 * @param regex - RegExp (or string) to match against the node text
		 * @param options - { recursive: true } to look into all descendants
		 * @returns the matching nodes, each one with the `match` property set
		 */
		find(regex, options) {
			regex = toRegExp(regex, "find");
			const recursive = !!(options && options.recursive);
			const results = [];
			for (const node of this._candidates(recursive)) {
				const match = execRegExp(regex, node.text);
				if (match) {
					results.push(node._withMatch(match));
				}
			}
			return results;
		}

		/**
		 * Whether at least one child node matches the regex.
		 * @param regex - RegExp (or string) to match against the node text
		 * @param options - { recursive: true } to look into all descendants
		 */
		hasChild(regex, options) {
			return this.find(toRegExp(regex, "hasChild"), options).length > 0;
		}

		/**
		 * Find the child nodes matching parentRegex, which have a direct child matching childRegex.
		 * @param options - { recursive: true } to look for parents among all descendants
		 */
		findWithChild(parentRegex, childRegex, options) {
			childRegex = toRegExp(childRegex, "findWithChild");
			return this.find(toRegExp(parentRegex, "findWithChild"), options)
				.filter((node) => node.hasChild(childRegex));
		}

		/**
		 * Find the child nodes matching parentRegex, which have no direct child matching childRegex.
		 * @param options - { recursive: true } to look for parents among all descendants
		 */
		findWithoutChild(parentRegex, childRegex, options) {
			childRegex = toRegExp(childRegex, "findWithoutChild");
			return this.find(toRegExp(parentRegex, "findWithoutChild"), options)
				.filter((node) => !node.hasChild(childRegex));
		}

		/**
		 * Get the values (first capture group, or whole match if none) of the child nodes matching the regex.
		 * @param options - { recursive: true } to look into all descendants
		 */
		findValues(regex, options) {
			return this.find(toRegExp(regex, "findValues"), options)
				.map((node) => (node.match.length > 1 ? node.match[1] : node.match[0]));
		}

		/**
		 * Get the value (first capture group, or whole match if none) of the first child node
		 * matching the regex, or null if none matches.
		 * @param options - { recursive: true } to look into all descendants
		 */
		findValue(regex, options) {
			const values = this.findValues(toRegExp(regex, "findValue"), options);
			return values.length > 0 ? values[0] : null;
		}

		/**
		 * Find the child nodes for which the predicate function returns true.
		 * @param predicate - function called with each node
		 * @param options - { recursive: true } to look into all descendants
		 */
		filter(predicate, options) {
			if (typeof predicate !== "function") {
				throw "Invalid predicate function in filter.";
			}
			const recursive = !!(options && options.recursive);
			return this._candidates(recursive).filter((node) => predicate(node));
		}

		/**
		 * Get the settings ("set <key> <values...>" direct child lines, FortiOS-style) of this node,
		 * as a key -> values map. The values are unquoted, a later line overrides an earlier one
		 * with the same key, and "unset <key>" removes the key.
		 * @returns an object mapping each key to the array of its values
		 */
		settings() {
			const settings = {};
			for (const child of this.children) {
				if (child.isComment) {
					continue;
				}
				const setting = parseSetting(child.text);
				if (setting === null) {
					continue;
				}
				if (setting.unset) {
					delete settings[setting.key];
				}
				else {
					settings[setting.key] = setting.values;
				}
			}
			return settings;
		}

		/**
		 * Get the values of a setting ("set <key> <values...>" direct child line), unquoted.
		 * @param key - the setting key
		 * @returns the array of values, empty if the setting is not found
		 */
		settingValues(key) {
			const settings = this.settings();
			return Object.prototype.hasOwnProperty.call(settings, key) ? settings[key] : [];
		}

		/**
		 * Get the value of a setting ("set <key> <values...>" direct child line), unquoted,
		 * multiple values being joined with a space.
		 * @param key - the setting key
		 * @returns the value, or null if the setting is not found
		 */
		setting(key) {
			const settings = this.settings();
			return Object.prototype.hasOwnProperty.call(settings, key) ? settings[key].join(" ") : null;
		}

		toString() {
			return this.text;
		}
	}

	const splitLines = (text) => text.split(/\r\n|\r|\n/);

	const leadingSpaces = (line) => line.search(/[^\t\s]/);

	/**
	 * Parse an indentation-based configuration (Cisco IOS-like):
	 * a line is a child of the previous less indented line.
	 * Blank lines are skipped, comment lines (starting with !) are flagged,
	 * and multi-line banners are kept as a single node.
	 */
	const parseIndent = (text) => {
		const root = new ConfigNode("", "", 0, -1, null);
		const stack = [root];
		const lines = splitLines(text);
		for (let l = 0; l < lines.length; l++) {
			const raw = lines[l];
			const indent = leadingSpaces(raw);
			if (indent === -1) {
				continue;
			}
			while (stack[stack.length - 1].indent >= indent) {
				stack.pop();
			}
			const parent = stack[stack.length - 1];
			const node = new ConfigNode(raw.trim(), raw, l + 1, indent, parent);
			node.isComment = (raw.charAt(indent) === "!");
			parent.children.push(node);
			stack.push(node);

			const banner = node.text.match(/^banner\s+\S+(?:\s+(\^C|\S)(.*))?$/);
			if (banner) {
				// Multi-line banner: consume the lines up to the end delimiter
				// (or up to a EOF line, for Arista-like banners without delimiter)
				const isClosed = banner[1] ? (banner[2].indexOf(banner[1]) >= 0) : false;
				if (!isClosed) {
					const bodyLines = [];
					while (l + 1 < lines.length) {
						l++;
						bodyLines.push(lines[l]);
						if (banner[1] ? (lines[l].indexOf(banner[1]) >= 0) : (lines[l].trim() === "EOF")) {
							break;
						}
					}
					node.raw = [raw].concat(bodyLines).join("\n");
					node.text = [node.text].concat(bodyLines).join("\n");
				}
			}
		}
		return root;
	};

	/**
	 * Parse a curly brace-based configuration (Juniper Junos, Palo Alto PAN-OS).
	 * The block delimiters and statement final semicolons are removed from node text.
	 * Comments (# lines and C-style comments, kept as a single node) are flagged.
	 */
	const parseBraces = (text) => {
		const root = new ConfigNode("", "", 0, -1, null);
		let current = root;
		const lines = splitLines(text);
		for (let l = 0; l < lines.length; l++) {
			let raw = lines[l];
			let content = raw.trim();
			if (content === "") {
				continue;
			}
			const indent = leadingSpaces(raw);
			if (content.startsWith("/*") || content.startsWith("#")) {
				const start = l;
				if (content.startsWith("/*")) {
					// Multi-line comment: consume the lines up to the end delimiter
					while (raw.indexOf("*/") < 0 && l + 1 < lines.length) {
						l++;
						raw += "\n" + lines[l];
						content += "\n" + lines[l];
					}
				}
				const comment = new ConfigNode(content, raw, start + 1, indent, current);
				comment.isComment = true;
				current.children.push(comment);
				continue;
			}
			if (content === "}") {
				if (current.parent !== null) {
					current.closing = raw;
					current = current.parent;
				}
				continue;
			}
			if (content.endsWith("{")) {
				const node = new ConfigNode(content.slice(0, -1).trim(), raw, l + 1, indent, current);
				current.children.push(node);
				current = node;
				continue;
			}
			if (content.endsWith(";")) {
				content = content.slice(0, -1).trim();
			}
			current.children.push(new ConfigNode(content, raw, l + 1, indent, current));
		}
		return root;
	};

	/**
	 * Parse a FortiOS-like configuration: "config" blocks end with "end",
	 * "edit" blocks end with "next". # lines are flagged as comments,
	 * and multi-line quoted values are kept in a single node.
	 */
	const parseFortiOs = (text) => {
		const root = new ConfigNode("", "", 0, -1, null);
		const stack = [root];
		const lines = splitLines(text);
		const closeBlock = (opener, raw) => {
			// Close up to the nearest block opened by the given keyword
			for (let s = stack.length - 1; s > 0; s--) {
				if (stack[s].text.split(/\s/)[0] === opener) {
					stack[s].closing = raw;
					stack.length = s;
					return;
				}
			}
		};
		const countQuotes = (s) => (s.replace(/\\./g, "").match(/"/g) || []).length;
		for (let l = 0; l < lines.length; l++) {
			let raw = lines[l];
			const content = raw.trim();
			if (content === "") {
				continue;
			}
			if (content.startsWith("#")) {
				const comment = new ConfigNode(content, raw, l + 1, leadingSpaces(raw), stack[stack.length - 1]);
				comment.isComment = true;
				stack[stack.length - 1].children.push(comment);
				continue;
			}
			if (content === "end") {
				closeBlock("config", raw);
				continue;
			}
			if (content === "next") {
				closeBlock("edit", raw);
				continue;
			}
			const indent = leadingSpaces(raw);
			let nodeText = content;
			if (countQuotes(content) % 2 === 1) {
				// Quoted value spanning multiple lines (e.g. certificate)
				while (l + 1 < lines.length) {
					l++;
					raw += "\n" + lines[l];
					nodeText += "\n" + lines[l];
					if (countQuotes(lines[l]) % 2 === 1) {
						break;
					}
				}
			}
			const parent = stack[stack.length - 1];
			const node = new ConfigNode(nodeText, raw, l + 1 - (raw.split("\n").length - 1), indent, parent);
			parent.children.push(node);
			if (/^(config|edit)\s/.test(content)) {
				stack.push(node);
			}
		}
		return root;
	};

	const PARSERS = {
		indent: parseIndent,
		braces: parseBraces,
		fortios: parseFortiOs,
	};

	const ConfigTree = Object.freeze({
		/** The supported syntaxes. */
		SYNTAXES: Object.freeze(SYNTAXES.slice()),

		/**
		 * Parse a configuration text into a tree.
		 * @param text - the configuration text
		 * @param options - { syntax: "indent" (default) | "braces" | "fortios",
		 *     indented: true to keep the original indentation in the node text (and match it in the queries) }
		 * @returns the root node, whose children are the top-level configuration lines
		 */
		parse(text, options) {
			if (typeof text !== "string") {
				throw "Invalid text string in ConfigTree.parse.";
			}
			const syntax = (options && options.syntax) || "indent";
			const parser = PARSERS[syntax];
			if (!parser) {
				throw `Invalid syntax '${syntax}' in ConfigTree.parse (expected one of ${SYNTAXES.join(", ")}).`;
			}
			const root = parser(text);
			if (options && options.indented) {
				const walk = (node) => {
					for (const child of node.children) {
						child.text = child.raw.slice(0, child.indent) + child.text;
						walk(child);
					}
				};
				walk(root);
			}
			for (const child of root.children) {
				// A top-level line is its own parent
				child.parent = child;
			}
			return root;
		},
	});

	/**
	 * Raw indentation tree, used by findSections: every line is kept
	 * (no comment or banner handling), lines without any non-space character
	 * are considered at indent -1, and blank lines are dropped (legacy behavior).
	 */
	const parseRawIndent = (text) => {
		const root = new ConfigNode("", "", 0, -2, null);
		const stack = [root];
		for (const raw of text.split(/[\r\n]+/g)) {
			const indent = leadingSpaces(raw);
			while (stack[stack.length - 1].indent >= indent) {
				stack.pop();
			}
			const parent = stack[stack.length - 1];
			const node = new ConfigNode(raw, raw, 0, indent, parent);
			parent.children.push(node);
			if (indent >= 0) {
				// A line without any non-space character never gets any content
				stack.push(node);
			}
		}
		return root;
	};

	/**
	 * Deprecated legacy helper (behind device.findSections and cli.findSections).
	 * Find the sections of text starting with a line matching the regex, along with
	 * their content (the following lines which are more indented).
	 * The content of a matching section isn't searched for other sections.
	 * @param text - the text to search into
	 * @param regex - the RegExp to match against each line
	 * @returns array of { match, lines, config } objects
	 */
	const findSections = (text, regex) => {
		if (typeof text !== "string") {
			throw "Invalid text string in findSections.";
		}
		if (typeof regex !== "object" || !(regex instanceof RegExp)) {
			throw "Invalid regex parameter in findSections.";
		}
		const sections = [];
		const walk = (node) => {
			for (const child of node.children) {
				const match = execRegExp(regex, child.raw);
				if (match) {
					const lines = child.lines;
					sections.push({
						match,
						lines,
						config: lines.join("\n"),
					});
				}
				else {
					walk(child);
				}
			}
		};
		walk(parseRawIndent(text));
		return sections;
	};

	const toolbox = Object.freeze({
		ConfigTree,
	});

	// Internal alias, with the implementation of the deprecated
	// device.findSections (rules) and cli.findSections (drivers) helpers
	Object.defineProperty(globalThis, "__netshotToolbox", {
		value: Object.freeze({
			ConfigTree,
			findSections,
		}),
		writable: false,
		enumerable: false,
		configurable: false,
	});
	Object.defineProperty(globalThis, "Toolbox", {
		value: toolbox,
		writable: true,
		enumerable: false,
		configurable: true,
	});
})();
