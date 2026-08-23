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

const validateRunScript = () => {
	if (typeof run !== "function") {
		throw "'run' is not defined or is not a function.";
	}
	if (typeof Input === "object") {
		Object.entries(Input).forEach(([inputName, inputDef]) => {
			if (typeof inputName !== "string") {
				throw "Invalid type for key in Input object";
			}
			if (!inputName.match(/^[a-zA-Z0-9_]+$/)) {
				throw `Invalid input name '${inputName}': allowed characters are {a to z, A to Z, 0 to 9, _}.`
			}
			inputDef.name = inputName;
			if (typeof inputDef.label == "undefined") {
				inputDef.label = inputDef.name;
				inputDef.label = inputDef.label[0].toUpperCase() + inputDef.label.slice(1);
			}
			if (typeof inputDef.label !== "string") {
				throw `The 'label' field in '${inputName}' input definition should be a string.`;
			}
			if (typeof inputDef.description !== "undefined") {
				if (typeof inputDef.description !== "string") {
					throw `The 'description' field in '${inputName}' input definition should be a string.`;
				}
			}
			if (typeof inputDef.optional === "undefined") {
				inputDef.optional = false;
			}
			if (typeof inputDef.optional !== "boolean") {
				throw `The 'optional' field in '${inputName}' input definition should be a boolean.`;
			}
			if (typeof inputDef.type === "undefined") {
				inputDef.type = "text";
			}
			if (!["text", "boolean"].includes(inputDef.type)) {
				throw `The 'type' field in '${inputName}' input definition should be one of "text" or "boolean".`;
			}
			if (typeof inputDef.choices !== "undefined") {
				if (inputDef.type !== "text") {
					throw `The 'choices' field is not applicable to '${inputName}' input.`;
				}
				if (!Array.isArray(inputDef.choices) || inputDef.choices.length === 0 ||
					!inputDef.choices.every((choice) => typeof choice === "string")) {
					throw `The 'choices' field in '${inputName}' input definition should be a non-empty array of strings.`;
				}
			}
			if (inputDef.type === "boolean" && typeof inputDef.regExp !== "undefined") {
				throw `The 'regExp' field is not applicable to boolean input '${inputName}'.`;
			}
			if (inputDef.type === "text" && typeof inputDef.regExp !== "undefined") {
				if (typeof inputDef.regExp !== "object" || !(inputDef.regExp instanceof RegExp)) {
					throw `The 'regExp' field in '${inputName}' input definition should be a RegExp object.`;
				}
			}
			if (typeof inputDef.default !== "undefined") {
				if (inputDef.type === "boolean" && typeof inputDef.default !== "boolean") {
					throw `The 'default' field in '${inputName}' input definition should be a boolean.`;
				}
				if (inputDef.type === "text" && typeof inputDef.default !== "string") {
					throw `The 'default' field in '${inputName}' input definition should be a string.`;
				}
				if (inputDef.type === "text" && inputDef.choices && !inputDef.choices.includes(inputDef.default)) {
					throw `The 'default' field in '${inputName}' input definition should be one of 'choices'.`;
				}
			}
		});
	}
	else if (typeof Input === "undefined") {
		// OK
	}
	else {
		throw "Input should be an object";
	}
};

const validateUserInputs = (inputs) => {
	const cleanInputs = {};
	if (typeof Input === "object") {
		Object.entries(Input).forEach(([inputName, inputDef]) => {
			let inputVal = inputs && inputs[inputName];
			if (typeof inputVal === "undefined" || inputVal === null || inputVal === "") {
				if (typeof inputDef.default !== "undefined") {
					inputVal = String(inputDef.default);
				}
				else if (!inputDef.optional) {
					throw `${inputDef.label} is missing.`;
				}
				else {
					return;
				}
			}
			if (typeof inputVal !== "string") {
				throw `Invalid type for ${inputName} input.`;
			}
			if (inputDef.type === "boolean") {
				if (inputVal !== "true" && inputVal !== "false") {
					throw `${inputDef.label} input value should be "true" or "false".`;
				}
			}
			else if (inputDef.choices) {
				if (!inputDef.choices.includes(inputVal)) {
					throw `${inputDef.label} input value is invalid (not one of the allowed choices).`;
				}
			}
			else if (inputDef.regExp) {
				if (!inputVal.match(inputDef.regExp)) {
					throw `${inputDef.label} input value is invalid (doesn't match regexp).`
				}
			}
			cleanInputs[inputName] = inputVal;
		});
	}
	return cleanInputs;
};


const _validate = (_target, _options) => {
	if (_target === "runScript") {
		validateRunScript();
	}
	else if (_target === "runInputs") {
		validateRunScript();
		validateUserInputs(_options.getUserInputs());
	}
}

/**
 * Checks whether an error looks like an authentication failure, per the
 * historical convention (driver-declared CLI "fail" mode strings, or the
 * dedicated Java exceptions thrown when autoTryCredentials is disabled).
 */
const isAuthFailureError = (e) => {
	const message = (e && typeof e.message === "string") ? e.message : String(e);
	return message.indexOf("Authentication failed") >= 0;
};

/**
 * Default priority per protocol, mirroring DeviceDriver.defaultPriorityFor
 * (Java) - higher is tried first within the same group.
 */
const ACCESS_PRIORITY_DEFAULTS = {
	ssh: 100, https: 90, snmpv3: 80, http: 30, snmpv2c: 22, snmpv1: 20, telnet: 10,
};

/**
 * Infers the effective protocol of one declared access, mirroring
 * DeviceDriver's own per-loop inference (explicit "protocol" member first,
 * else the key name). Returns null for a CLI-family member that isn't
 * actually an access (e.g. a CLI mode like "enable"), so it can be excluded
 * from group resolution.
 */
const inferAccessProtocol = (family, key, accessValue) => {
	const explicit = (accessValue && typeof accessValue.protocol === "string")
		? accessValue.protocol.toLowerCase() : null;
	if (family === "cli") {
		if (explicit === "ssh" || explicit === "telnet") {
			return explicit;
		}
		if (explicit) {
			return null;
		}
		const k = key.toLowerCase();
		if (k === "ssh" || k === "telnet") {
			return k;
		}
		return null; // Not an access definition (e.g. a CLI mode like 'enable').
	}
	if (family === "snmp") {
		if (explicit === "snmpv1" || explicit === "snmpv2c" || explicit === "snmpv3") {
			return explicit;
		}
		if (explicit) {
			return null;
		}
		const k = key.toLowerCase();
		if (k === "snmpv1" || k === "snmpv2c" || k === "snmpv3") {
			return k;
		}
		return null; // Not a recognized SNMP access key.
	}
	// http
	if (explicit === "http" || explicit === "https") {
		return explicit;
	}
	return (key.toLowerCase() === "http") ? "http" : "https";
};

/**
 * Resolves a literal access name or group word (e.g. "cli", "snmp", "http",
 * or any custom group a driver declared) against the driver's own
 * CLI/SNMP/HTTP globals, returning the ordered list of concrete, literal
 * access names to try. A group word expands to every declared access whose
 * (possibly defaulted) group matches, sorted by priority descending.
 */
const resolveAccessNames = (nameOrArray) => {
	const names = Array.isArray(nameOrArray) ? nameOrArray : [nameOrArray];
	const groups = { cli: (typeof CLI === "object" ? CLI : {}), snmp: (typeof SNMP === "object" ? SNMP : {}),
		http: (typeof HTTP === "object" ? HTTP : {}) };

	const resolveOne = (name) => {
		// 1. Literal key match, across CLI/SNMP/HTTP (in that search order).
		for (const family of ["cli", "snmp", "http"]) {
			if (Object.prototype.hasOwnProperty.call(groups[family], name)) {
				return { family, accessNames: [name] };
			}
		}
		// 2. Group word: every declared access (across CLI/SNMP/HTTP) whose
		// effective group equals this name, sorted by priority descending.
		const matches = [];
		["cli", "snmp", "http"].forEach((family) => {
			Object.keys(groups[family]).forEach((key) => {
				const accessValue = groups[family][key];
				if (!accessValue || typeof accessValue !== "object") {
					return;
				}
				const protocol = inferAccessProtocol(family, key, accessValue);
				if (protocol === null) {
					return;
				}
				const group = (typeof accessValue.group === "string") ? accessValue.group : family;
				if (group !== name) {
					return;
				}
				const priority = (typeof accessValue.priority === "number")
					? accessValue.priority : (ACCESS_PRIORITY_DEFAULTS[protocol] || 0);
				matches.push({ family, key, priority });
			});
		});
		if (matches.length === 0) {
			throw `Unknown access '${name}': no such CLI/SNMP/HTTP access is declared by this driver.`;
		}
		const families = Array.from(new Set(matches.map((m) => m.family)));
		if (families.length > 1) {
			throw `Group '${name}' mixes accesses of different kinds (${families.join(", ")}); a client can only be built from accesses of the same kind.`;
		}
		matches.sort((a, b) => b.priority - a.priority);
		return { family: families[0], accessNames: matches.map((m) => m.key) };
	};

	let family = null;
	let accessNames = [];
	names.forEach((name) => {
		const resolved = resolveOne(name);
		if (family === null) {
			family = resolved.family;
		}
		else if (family !== resolved.family) {
			throw `Cannot mix accesses of different families ('${family}' and '${resolved.family}') in the same client.create(...) call.`;
		}
		accessNames = accessNames.concat(resolved.accessNames);
	});
	return { family, accessNames };
};

/**
 * Wraps `target` in a new, frozen object exposing only bound copies of the
 * named public members - methods delegate to `target` (so its own internal
 * state mutations, e.g. `this._mode = ...`, keep working exactly as
 * before), plain data properties are copied by reference. The returned
 * facade can't have its members reassigned/deleted, and no property not in
 * `memberNames` is reachable through it at all - used to harden every
 * object handed off to user-authored scripts (run/diagnose) against
 * tampering, without changing any of the internal implementation above.
 */
function freezeFacade(target, memberNames) {
	const facade = {};
	for (const name of memberNames) {
		const member = target[name];
		if (member === undefined) {
			continue;
		}
		facade[name] = (typeof member === "function" ? member.bind(target) : member);
	}
	return Object.freeze(facade);
}

const PUBLIC_CLI_MEMBERS =
	["command", "macro", "findSections", "sleep", "debug", "tryNextCredentials", "create", "userInputs"];
const PUBLIC_SNMP_MEMBERS = ["get", "walk", "sleep", "tryNextCredentials"];
const PUBLIC_HTTP_MEMBERS =
	["request", "download", "get", "delete", "head", "options", "post", "put", "patch", "tryNextCredentials", "sleep", "debug"];
const PUBLIC_DEVICE_MEMBERS = ["options", "set", "add", "get", "textDownload"];
const PUBLIC_CONFIG_MEMBERS = [
	"set", "download", "computeHash", "getHash", "getLastHash",
	"isChangedHash", "requestUpload", "awaitUpload", "commitUpload",
];
const PUBLIC_DIAGNOSTIC_MEMBERS = ["set"];

const _connect = (_function, _options) => {

	const _taskContext = _options.getTaskContext();

	const debug = (message) => {
		if (typeof message  === "string") {
			message = String(message);
			_taskContext.debug(message);
		}
	};

	const stripPreviousMatch = (prompt, strictPrompt) => {
		if (typeof(strictPrompt) === "string") {
			const groups = [];
			for (let p = 0; p < prompt.length; p++) {
				if (prompt[p] === '(') {
					groups.push({ start: p });
				}
				else if (prompt[p] === ')') {
					for (let g = groups.length; g > 0; g--) {
						if (typeof groups[g - 1].end === "undefined") {
							groups[g - 1].end = p;
							break;
						}
					}
				}
			}
			for (let i = 0; i < groups.length; i++) {
				const s = groups[i].start;
				const e = groups[i].end;
				if (prompt.substr(s, 3) === '(?:') continue;
				prompt = prompt.substr(0, s + 1) + strictPrompt.replace(/([.?*+^$[\]\\(){}|-])/g, "\\$1")
				  + prompt.substr(e);
				break;
			}
		}
		return prompt;
	};

	/**
	 * Builds a JS "cli"-shaped client object bound to the given Java JsCliHelper.
	 * `_autoTryCredentials` controls whether an in-band authentication failure
	 * (or the initial connect itself) is retried transparently or surfaced to
	 * the driver as a catchable error with an `authenticationFailed` marker.
	 */
	const makeCliClient = (_cli, _autoTryCredentials) => {
		const cli = {

			_mode: null,
			_modeHistory: [],
			_strictPrompt: null,
			CR: "\r",
			_recursion: 0,

			_applyPager: function(mode, name) {
				const pager = mode.pager;
				if (pager) {
					delete this.pagerMatch;
					if (typeof pager !== "object") {
						throw `In CLI mode ${name}, the pager is not an object.`;
					}
					if (pager.match) {
						if (!(pager.match instanceof RegExp)) {
							throw `In CLI mode ${name}, the pager match entry is not a RegExp.`;
						}
						if (typeof(pager.response) !== "string") {
							throw `In CLI mode ${name} the pager response is not a string.`;
						}
						this.pagerMatch = pager.match;
						this.pagerResponse = pager.response;
					}
					if (pager.avoid) {
						let avoid = pager.avoid;
						if (typeof(avoid) === "string") {
							avoid = [avoid];
						}
						if (!Array.isArray(avoid)) {
							throw `In CLI mode ${name}, the pager avoid command is invalid.`;
						}
						avoid.forEach((avoidCommand) => {
							if (typeof(avoidCommand) !== "string") {
								throw `In CLI mode ${name}, one of the avoid commands is invalid.`;
							}
							try {
								this.command(avoidCommand);
							}
							catch(e) {
							}
						});
					}
				}
			},

			/** Lazily connects and seeds `_mode` on first use. Returns true if this call did the seeding. */
			_ensureSeeded: function() {
				if (this._mode === null) {
					this._mode = _cli.getResolvedAccessName();
					this._modeHistory = [this._mode];
					return true;
				}
				return false;
			},

			/** Handles an in-band ("fail" mode) or connect-time auth failure caught around a first-use call. */
			_handleFirstUseAuthFailure: function(e) {
				_cli.resetForInBandAuthFailure();
				if (_autoTryCredentials) {
					if (_cli.tryNextCredentials()) {
						this._mode = null;
						return true; // caller should retry
					}
					return false; // exhausted, caller should rethrow original error
				}
				const authError = new Error(String(e && e.message ? e.message : e));
				authError.authenticationFailed = true;
				throw authError;
			},

			macro: function(macro) {
				const isFirstUse = (this._mode === null);
				try {
					this._ensureSeeded();
					if (this._mode == macro) {
						if (isFirstUse) {
							_cli.confirmFirstUseSuccess();
						}
						return;
					}
					this.recursion = 0;
					this._runningTarget = null;
					this._runningMacro = macro;
					this._originalMode = this._mode;
					this._macro(macro);

					if (typeof(CLI[this._mode]) !== "object") {
						throw `No mode ${this._mode} could be found in CLI object.`;
					}
					this._applyPager(CLI[this._mode], this._mode);
					if (isFirstUse) {
						_cli.confirmFirstUseSuccess();
					}
				}
				catch (e) {
					if (isFirstUse && isAuthFailureError(e) && this._handleFirstUseAuthFailure(e)) {
						return this.macro(macro);
					}
					throw e;
				}
			},

			_macro: function(macroName) {
				_cli.trace(`Macro '${macroName}' was called (current mode is '${this._mode}').`);
				if (this.recursion++ > 10) {
					throw "Too many steps while switching to a new mode.";
				}
				if (typeof(macroName) !== "string") {
					throw "Invalid called macro.";
				}
				if (typeof(CLI[this._mode]) !== "object") {
					throw `No mode ${this._mode} could be found in CLI object.`;
				}
				if (typeof(CLI[this._mode].macros) !== "object") {
					throw `No targets array in ${this._mode} mode in CLI object.`;
				}
				const macro = CLI[this._mode].macros[macroName];
				if (typeof(macro) != "object") {
					throw `Cannot find macro ${macroName} in macros of mode ${this._mode} in CLI object.`;
				}
				if (this._runningTarget === null) {
					const target = macro.target;
					if (typeof(target) !== "string") {
						throw `Cannot find target ${target} of macro ${macroName} of mode ${this._mode} in CLI object.`;
					}
					if (typeof(CLI[target]) !== "object") {
						throw `No mode ${target} in CLI.`;
					}
					this._runningTarget = target;
				}
				let cmd = undefined;
				if (typeof(macro.cmd) !== "undefined") {
					if (typeof(macro.cmd) !== "string") {
						throw `Invalid 'cmd' in macro of mode ${this._mode} in CLI object.`;
					}
					cmd = macro.cmd;
				}
				const prompts = [];
				if (!(macro.options instanceof Array)) {
					throw `Invalid 'options' array in macro of mode ${this._mode} in CLI object.`;
				}
				macro.options.forEach((option) => {
					if (typeof option !== "string") {
						throw `Invalid option in macro of ${this._mode} in CLI object.`;
					}
					if (typeof CLI[option] !== "object") {
						throw `No mode ${option} can be found in CLI object.`;
					}
					if (!(CLI[option].prompt instanceof RegExp)) {
						throw `No regexp prompt in ${option} mode in CLI object.`;
					}
					prompts.push(CLI[option].prompt.source);
				});
				if (cmd === undefined) {
					cmd = "";
				}
				else if (macro.noCr !== true) {
					cmd += this.CR;
				}
				if (typeof(macro.waitBefore) === "number") {
					this.sleep(macro.waitBefore);
				}
				const sendParams = { // defaults
					timeout: -1,
					cleanUpActions: null,
					discoverWaitTime: -1,
				};
				if (typeof(macro.timeout) === "number") {
					sendParams.timeout = macro.timeout;
				}
				if (typeof(macro.discoverWaitTime) === "number") {
					sendParams.discoverWaitTime = macro.discoverWaitTime;
				}
				const output = _cli.send(cmd, prompts, sendParams.timeout,
						sendParams.cleanUpActions, sendParams.discoverWaitTime);
				if (typeof(macro.waitAfter) === "number") {
					this.sleep(macro.waitAfter);
				}
				if (_cli.isErrored()) {
					throw `Error while running CLI macro '${macroName}'.`;
				}
				if (CLI[this._mode].error instanceof RegExp) {
					const errorMatch = CLI[this._mode].error.exec(output);
					if (errorMatch) {
						const messageParts = [];
						messageParts.push("CLI error returned by the device");
						if (errorMatch[1]) {
							messageParts.push(`: '${errorMatch[1]}'`);
						}
						messageParts.push(` after command '${cmd.trim()}'.`);
						const message = messageParts.join("");
						throw message;
					}
				}
				this._mode = macro.options[_cli.getLastExpectMatchIndex()];
				this._modeHistory.push(this._mode);
				this._strictPrompt = _cli.getLastExpectMatchGroup(1);
				if (this._mode === this._runningTarget) {
					_cli.trace(`Reached target mode '${this._mode}'.`);
					return;
				}
				if (typeof(CLI[this._mode].fail) === "string") {
					throw `In mode ${this._mode}: ${CLI[this._mode].fail}`;
				}
				if (typeof(CLI[this._mode].macros) === "object" && typeof(CLI[this._mode].macros.auto) === "object") {
					this._macro("auto");
				}
				else if (typeof(CLI[this._mode].macros) === "object" && typeof(CLI[this._mode].macros[this._runningMacro]) === "object") {
					this._macro(this._runningMacro);
				}
				if (this._mode !== this._runningTarget) {
					throw `Couldn't switch to mode ${this._runningTarget} using macro ${this._runningMacro} from mode ${this._originalMode} (reached mode ${this._mode}).`;
				}
			},

			command: function(command, options) {
				const isFirstUse = (this._mode === null);
				try {
					this._ensureSeeded();
					const result = this._command(command, options);
					if (isFirstUse) {
						_cli.confirmFirstUseSuccess();
					}
					return result;
				}
				catch (e) {
					if (isFirstUse && isAuthFailureError(e) && this._handleFirstUseAuthFailure(e)) {
						return this.command(command, options);
					}
					throw e;
				}
			},

			_command: function(command, options) {
				let mode;
				let clearPrompt = false;
				let noCr = false;
				if (typeof(options) === "object" && typeof(options.mode) === "string") {
					mode = CLI[options.mode];
					if (typeof(mode) !== "object") {
						throw `No mode ${options.mode} can be found in CLI object.`;
					}
					this._applyPager(mode, options.mode);
				}
				else if (typeof(options) === "object" && typeof(options.mode) === "object") {
					mode = options.mode;
					this._applyPager(mode, "[temp]");
				}
				else if (typeof(options) === "object" && typeof(options.mode) !== "undefined") {
					throw "Invalid mode parameters in 'command' options";
				}
				else if (typeof(CLI[this._mode]) !== "object") {
					throw `No mode ${this._mode} in CLI object.`;
				}
				else {
					mode = CLI[this._mode];
				}
				if (mode.prompt && !(mode.prompt instanceof RegExp)) {
					throw "The prompt in the selected mode is not a RegExp.";
				}

				const prompts = [];
				if (mode.clearPrompt === true) {
					clearPrompt = true;
				}
				if (typeof(options) === "object" && options.clearPrompt === true) {
					clearPrompt = true;
				}
				if (typeof options === "object" && options.noCr === true) {
					noCr = true;
				}
				if (clearPrompt) {
					this._strictPrompt = null;
				}
				if (mode.prompt) {
					let prompt = mode.prompt.source;
					prompt = stripPreviousMatch(prompt, this._strictPrompt);
					prompts.push(prompt);
					if (typeof(this.pagerMatch) !== "undefined") {
						prompts.push(this.pagerMatch.source);
					}
				}

				let result = "";
				let toSend = command;
				if (!noCr) {
					toSend += this.CR;
				}
				while (true) {
					const sendParams = { // defaults
						timeout: -1,
						cleanUpActions: null,
						discoverWaitTime: -1,
					};
					if (typeof(options) === "object") {
						if (typeof(options.timeout) === "number") {
							sendParams.timeout = options.timeout;
						}
						if (Array.isArray(options.cleanUpActions)) {
							sendParams.cleanUpActions = options.cleanUpActions;
						}
						if (typeof(options.discoverWaitTime) === "number") {
							sendParams.discoverWaitTime = options.discoverWaitTime;
						}
					}
					const buffer = _cli.send(toSend, prompts,
						sendParams.timeout, sendParams.cleanUpActions, sendParams.discoverWaitTime);

					if (_cli.isErrored()) {
						throw `CLI error after command '${command}'`;
					}
					if (_cli.getLastExpectMatchIndex() === 1) {
						result += _cli.getLastFullOutput();
						toSend = this.pagerResponse;
					}
					else {
						result += buffer;
						break;
					}
				}
				result = _cli.removeEcho(result, command);
				if (mode.error instanceof RegExp) {
					const errorMatch = mode.error.exec(result);
					if (errorMatch) {
						const messageParts = [];
						messageParts.push("CLI error returned by the device");
						if (errorMatch[1]) {
							messageParts.push(`: '${errorMatch[1]}'`);
						}
						messageParts.push(` after command '${command}'.`);
						const message = messageParts.join("");
						throw message;
					}
				}
				return result;
			},

			findSections: function(text, regex) {
				if (typeof(text) !== "string") {
					throw "Invalid text string in findSections.";
				}
				if (typeof(regex) !== "object" || !(regex instanceof RegExp)) {
					throw "Invalid regex parameter in findSections.";
				}
				const sections = [];
				let section;
				let indent = -1;
				const lines = text.split(/[\r\n]+/g);
				for (let l in lines) {
					const line = lines[l];
					const i = line.search(/[^\t\s]/);
					if (i > indent) {
						if (indent > -1) {
							section.lines.push(line);
						}
					}
					else {
						indent = -1;
					}
					if (indent == -1) {
						regex.lastIndex = 0;
						const match = regex.exec(line);
						if (match) {
							indent = i;
							section = {
								match: match,
								lines: []
							};
							sections.push(section);
						}
					}
				}
				sections.forEach((section) => {
					section.config = section.lines.join("\n");
				});
				return sections;
			},

			sleep: function(millis) {
				if (typeof(millis) !== "number") {
					throw "Invalid number of milliseconds in sleep.";
				}
				if (millis < 0) {
					throw "The number of milliseconds to wait can't be negative in sleep.";
				}
				if (millis % 1 !== 0) {
					throw "The number of milliseconds to wait must be integer in sleep.";
				}
				_cli.sleep(millis);
			},

			debug: function(message) {
				debug(message);
			},

			/** Manually advance to the next candidate credential set (manual/autoTryCredentials=false mode). */
			tryNextCredentials: function() {
				const success = _cli.tryNextCredentials();
				this._mode = success ? null : this._mode;
				return success;
			},

		};
		return cli;
	};

	/**
	 * Builds a JS "poller"-shaped (SNMP) client object bound to the given
	 * Java JsSnmpHelper.
	 */
	const makeSnmpClient = (_snmp) => {
		const poller = {
			get: function(oid) {
				if (typeof(oid) == "string") {
					oid = String(oid);
				}
				else {
					throw "The OID should be a string in poller.get.";
				}
				const result = _snmp.getAsString(oid);
				if (_snmp.isErrored()) {
					throw `Error while SNMP polling OID ${oid}`;
				}
				return result;
			},

			walk: function(oid, reindex) {
				if (typeof(oid) === "string") {
					oid = String(oid);
				}
				else {
					throw "The OID should be a string in poller.walk.";
				}
				const results = _snmp.walkAsString(oid);
				if (_snmp.isErrored()) {
					throw `Error while SNMP polling OID ${oid}`;
				}
				const resultMap = {}
				Object.keys(results).forEach((r) => {
					if (reindex) {
						if (r.startsWith(oid + ".")) {
							resultMap[r.slice(oid.length + 1)] = results[r];
						}
					}
					else {
						resultMap[r] = results[r];
					}
				});
				return resultMap;
			},

			sleep: function(millis) {
				if (typeof(millis) !== "number") {
					throw "Invalid number of milliseconds in sleep.";
				}
				if (millis < 0) {
					throw "The number of milliseconds to wait can't be negative in sleep.";
				}
				if (millis % 1 !== 0) {
					throw "The number of milliseconds to wait must be integer in sleep.";
				}
				_snmp.sleep(millis);
			},

			tryNextCredentials: function() {
				return _snmp.tryNextCredentials();
			},
		};
		return poller;
	};

	/**
	 * Builds a JS "http" client object (axios-inspired) bound to the given
	 * Java JsHttpHelper.
	 */
	const makeHttpClient = (_http) => {
		const normalizeConfig = (config) => {
			config = config || {};
			return {
				headers: config.headers || {},
				query: config.params || config.query || {},
				cookies: config.cookies || {},
				validateStatus: (typeof config.validateStatus === "function")
					? config.validateStatus
					: (status) => status >= 200 && status < 300,
			};
		};

		// Shared by request()/download(): JSON-encodes a non-string body and
		// stamps a Content-Type if the driver didn't set one of its own.
		const prepareRequestBody = (config, normalized) => {
			let body = config.data;
			if (typeof body !== "undefined" && body !== null && typeof body !== "string") {
				body = JSON.stringify(body);
				if (!Object.keys(normalized.headers).some((h) => h.toLowerCase() === "content-type")) {
					normalized.headers["Content-Type"] = "application/json";
				}
			}
			return (typeof body === "string") ? body : null;
		};

		const httpClient = {
			request: function(config) {
				config = config || {};
				const method = (config.method || "GET").toUpperCase();
				const path = config.url || config.path || "";
				const normalized = normalizeConfig(config);
				const body = prepareRequestBody(config, normalized);
				const raw = _http.request(method, path, normalized.headers, normalized.query, normalized.cookies, body);
				const response = {
					status: raw.status,
					statusText: String(raw.status),
					headers: raw.headers,
					config: config,
					data: raw.body,
				};
				try {
					response.json = () => JSON.parse(raw.body);
				}
				catch (e) {
					// Leave json() undefined-ish (will throw only if actually called on invalid content)
				}
				if (!normalized.validateStatus(response.status)) {
					const error = new Error(`Request failed with status code ${response.status}`);
					error.response = response;
					error.config = config;
					throw error;
				}
				return response;
			},

			// Binary-safe counterpart to request()/get(): downloads straight
			// into the named BinaryFile config attribute (byte for byte,
			// unlike request()'s `.data`, which is always a charset-decoded
			// String and would corrupt arbitrary content such as a backup
			// archive) - one call, no separate "commit" step, mirroring how
			// config.download(key, "scp"|"sftp", ...) is a single call on the
			// CLI/SSH side too.
			download: function(key, url, config) {
				if (typeof key !== "string") {
					throw "The key should be a string in http.download.";
				}
				config = config || {};
				const method = (config.method || "GET").toUpperCase();
				const normalized = normalizeConfig(config);
				const body = prepareRequestBody(config, normalized);
				let storeFileName = null;
				if (typeof config.storeFileName === "string") {
					storeFileName = String(config.storeFileName);
				}
				else if (typeof config.storeFileName !== "undefined") {
					throw "Invalid 'storeFileName' option in http.download.";
				}
				let checksum = null;
				if (typeof config.checksum === "string") {
					checksum = String(config.checksum);
				}
				else if (typeof config.checksum !== "undefined") {
					throw "Invalid 'checksum' option in http.download.";
				}
				const raw = _http.download(key, method, url, normalized.headers, normalized.query, normalized.cookies,
					body, storeFileName, checksum);
				const response = {
					status: raw.status,
					statusText: String(raw.status),
					headers: raw.headers,
					config: config,
					size: raw.size,
				};
				if (!normalized.validateStatus(response.status)) {
					const error = new Error(`Download failed with status code ${response.status}`);
					error.response = response;
					error.config = config;
					throw error;
				}
				return response;
			},

			get: function(url, config) {
				return this.request(Object.assign({}, config, { method: "GET", url: url }));
			},
			delete: function(url, config) {
				return this.request(Object.assign({}, config, { method: "DELETE", url: url }));
			},
			head: function(url, config) {
				return this.request(Object.assign({}, config, { method: "HEAD", url: url }));
			},
			options: function(url, config) {
				return this.request(Object.assign({}, config, { method: "OPTIONS", url: url }));
			},
			post: function(url, data, config) {
				return this.request(Object.assign({}, config, { method: "POST", url: url, data: data }));
			},
			put: function(url, data, config) {
				return this.request(Object.assign({}, config, { method: "PUT", url: url, data: data }));
			},
			patch: function(url, data, config) {
				return this.request(Object.assign({}, config, { method: "PATCH", url: url, data: data }));
			},

			tryNextCredentials: function() {
				return _http.tryNextCredentials();
			},

			sleep: function(millis) {
				if (typeof(millis) !== "number") {
					throw "Invalid number of milliseconds in sleep.";
				}
				if (millis < 0) {
					throw "The number of milliseconds to wait can't be negative in sleep.";
				}
				if (millis % 1 !== 0) {
					throw "The number of milliseconds to wait must be integer in sleep.";
				}
				_http.sleep(millis);
			},

			debug: function(message) {
				debug(message);
			},
		};
		return httpClient;
	};

	const deviceHelper = {
		// Per-device values of the driver-declared Options (see the
		// top-level `Options` descriptor), read-only - drivers never write
		// these back, they're set by the user through the UI.
		options: _options.getDeviceHelper().getOptions(),
		set: function(key, value) {
			if (typeof(key) === "string") {
				key = String(key);
			}
			else {
				throw "The key should be a string in device.set.";
			}
			if (typeof(value) === "undefined") {
				throw `Undefined value used in device.set, for key ${key}.`;
			}
			else if (typeof(value) === "string") {
				value = String(value);
			}
			_options.getDeviceHelper().set(key, value);
		},
		add: function(collection, value) {
			if (typeof(collection) === "string") {
				collection = String(collection);
			}
			else {
				throw "The collection should be a string in device.add.";
			}
			if (typeof(value) === "undefined") {
				throw `Undefined value used in device.add, for collection ${collection}.`;
			}
			else if (typeof(value) === "object") {
				value["__"] = {};
			}
			else if (typeof(value) === "string") {
				value = String(value);
			}
			_options.getDeviceHelper().add(collection, value);
		},
		get: function(key, id) {
			if (typeof(key) === "string") {
				key = String(key);
				if (typeof(id) === "undefined") {
					return _options.getDeviceHelper().get(key);
				}
				else if (typeof(id) === "number" && !isNaN(id)) {
					return _options.getDeviceHelper().get(key, id);
				}
				else if (typeof(id) === "string") {
					const name = String(id);
					return _options.getDeviceHelper().get(key, name);
				}
				else {
					throw "Invalid device id to retrieve data from.";
				}
			}
			throw "Invalid key to retrieve.";
		},
		textDownload: function(fileName, options) {
			if (typeof(fileName) === "string") {
				fileName = String(fileName);
			}
			else {
				throw "The fileName should be a string in device.textDownload.";
			}
			let method = "sftp";
			let charset = "UTF-8";
			let newSession = false;
			if (typeof options === "object") {
				if (typeof options.charset === "string") {
					charset = String(options.charset);
				}
				else if (typeof options.charset !== "undefined") {
					throw "The charset should be a string in device.textDownload";
				}
				if (typeof options.newSession === "boolean") {
					newSession = options.newSession;
				}
				else if (typeof options.newSession !== "undefined") {
					throw "Invalid option type newSession (should be a boolean) in device.textDowload";
				}
				if (options.method === "sftp" || options.method === "scp") {
					method = options.method;
				}
				else {
					throw "Invalid 'method' option in device.textDownload";
				}
			}
			else {
				throw "Invalid argument in device.textDownload";
			}
			return _options.getDeviceHelper().textDownload(method, fileName, charset, newSession);
		},
	};

	const configHelper = {
		set: function(key, value) {
			if (typeof(key) === "string") {
				key = String(key);
			}
			else {
				throw "The key should be a string in config.set.";
			}
			if (typeof(value) === "undefined") {
				throw `Undefined value used in config.set, for key ${key}.`;
			}
			else if (typeof(value) === "string") {
				value = String(value);
			}
			_options.getConfigHelper().set(key, value);
		},
		download: function(key, fileName, options) {
			if (typeof(key) === "string") {
				key = String(key);
			}
			else {
				throw "The key should be a string in config.download.";
			}
			if (typeof(fileName) === "string") {
				fileName = String(fileName);
			}
			else {
				throw "The fileName should be a string in config.download.";
			}
			let storeFileName = String("");
			let method = "sftp";
			let newSession = false;
			let checksum = null;
			if (typeof options === "object") {
				if (typeof options.newSession === "boolean") {
					newSession = options.newSession;
				}
				else if (typeof options.newSession !== "undefined") {
					throw "Invalid 'newSession' option (should be a boolean) in config.download";
				}
				if (options.method === "sftp" || options.method === "scp") {
					method = options.method;
				}
				else if (typeof options.method !== "undefined") {
					throw "Invalid 'method' option in config.download";
				}
				if (typeof options.storeFileName === "string") {
					storeFileName = String(options.storeFileName);
				}
				else if (typeof options.storeFileName !== "undefined") {
					throw "Invalid 'storeFileName' option in config.download.";
				}
				if (typeof options.checksum === "string") {
					checksum = String(options.checksum);
				}
				else if (typeof options.checksum !== "undefined") {
					throw "Invalid 'checksum' option in config.download.";
				}
			}
			else if (typeof options !== "undefined") {
				throw "Invalid type for options argument in config.download";
			}

			_options.getConfigHelper()
				.download(key, method, fileName, storeFileName, newSession, checksum);
		},
		computeHash: function(...params) {
			const inputs = params.map((i, idx) => {
				if (i === null || typeof(i) === "undefined") {
					return null;
				}
				else if (typeof(i) === "string") {
					return String(i);
				}
				throw `Invalid element type in passed array, index ${idx}, in config.computeHash.`;
			});
			_options.getConfigHelper().computeCustomHash(inputs);
		},
		getHash: function() {
			return _options.getConfigHelper().getCustomHash();
		},
		getLastHash: function() {
			return _options.getConfigHelper().getLastCustomHash();
		},
		isChangedHash: function() {
			return _options.getConfigHelper().getCustomHash() !==
			       _options.getConfigHelper().getLastCustomHash();
		},
		requestUpload: function(options) {
			let method = null; // any method
			let sourceIp = null;
			if (typeof options === "object") {
				if (typeof options.method === "string") {
					if (!["scp", "sftp"].includes(options.method)) {
						throw `Invalid 'method' ${options.method} in config.requestUpload.`;
					}
					method = options.method;
				}
				else if (typeof options.method !== "undefined") {
					throw "Invalid 'method' option in config.requestUpload.";
				}
				if (typeof options.sourceIp === "string") {
					sourceIp = String(options.sourceIp);
				}
				else if (typeof options.sourceIp !== "undefined") {
					throw "Invalid 'sourceIp' option in config.requestUpload.";
				}
			}
			else if (typeof options !== "undefined") {
				throw "Invalid type for options argument in config.requestUpload.";
			}
			return _options.getConfigHelper().requestUpload(method, sourceIp);
		},
		awaitUpload: function(ticketId, timeout) {
			if (typeof ticketId !== "number" || !Number.isInteger(ticketId)) {
				throw "Invalid type for ticketId in config.awaitUpload (expected integer).";
			}
			if (typeof timeout === "undefined") {
				timeout = 60000; // Default 60 seconds
			}
			if (typeof timeout !== "number" || timeout <= 0) {
				throw "Invalid timeout in config.awaitUpload (expected positive number in milliseconds).";
			}
			return _options.getConfigHelper().awaitUpload(ticketId, timeout);
		},
		commitUpload: function(ticketId, fileId, key, options) {
			if (typeof ticketId !== "number" || !Number.isInteger(ticketId)) {
				throw "Invalid type for ticketId in config.commitUpload (expected integer).";
			}
			if (typeof fileId !== "number" || !Number.isInteger(fileId)) {
				throw "Invalid type for fileId in config.commitUpload (expected integer).";
			}
			if (typeof key !== "string") {
				throw "Invalid type for key in config.commitUpload (expected string).";
			}
			let storeName = null;
			let expectedHash = null;
			if (typeof options === "object") {
				if (typeof options.storeName === "string") {
					storeName = String(options.storeName);
				}
				else if (typeof options.storeName !== "undefined") {
					throw "Invalid 'storeName' option in config.commitUpload.";
				}
				if (typeof options.checksum === "string") {
					expectedHash = String(options.checksum);
				}
				else if (typeof options.checksum !== "undefined") {
					throw "Invalid 'checksum' option in config.commitUpload.";
				}
			}
			else if (typeof options !== "undefined") {
				throw "Invalid type for options argument in config.commitUpload.";
			}
			_options.getConfigHelper().commitUpload(ticketId, fileId, String(key), storeName, expectedHash);
		},
	};

	const diagnosticHelper = {
		setKey: function(key) {
			this.currentKey = key;
		},
		set: function(key, value) {
			if (typeof(value) === "undefined") {
				value = key;
				_options.getDiagnosticHelper().set(this.currentKey, value);
			}
			else {
				if (typeof(key) === "string") {
					key = String(key);
				}
				else {
					throw "The key should be a string in diagnostic.set.";
				}
				value = String(value);
				_options.getDiagnosticHelper().set(key, value);
			}
		}
	};

	// The default (implicit, backward-compatible) CLI client, bound to the
	// driver's default SSH/Telnet access(es). autoTryCredentials is always
	// true here, matching historical behavior - a driver that wants control
	// must explicitly call client.create(...) with autoTryCredentials: false.
	const client = makeCliClient(_options.getCliHelper(), true);

	client.create = function(nameOrArray, createOptions) {
		createOptions = createOptions || {};
		const autoTryCredentials = (createOptions.autoTryCredentials !== false);
		const resolved = resolveAccessNames(nameOrArray);
		const factory = _options.getClientFactory();
		if (resolved.family === "cli") {
			return freezeFacade(
				makeCliClient(factory.createCli(resolved.accessNames, autoTryCredentials), autoTryCredentials),
				PUBLIC_CLI_MEMBERS
			);
		}
		if (resolved.family === "snmp") {
			return freezeFacade(makeSnmpClient(factory.createSnmp(resolved.accessNames, autoTryCredentials)), PUBLIC_SNMP_MEMBERS);
		}
		// http
		return freezeFacade(
			makeHttpClient(factory.createHttp(resolved.accessNames, autoTryCredentials, createOptions.basePath || null)),
			PUBLIC_HTTP_MEMBERS
		);
	};

	if (_function === "snapshot") {
		_options.getDeviceHelper().reset();
		snapshot(
			freezeFacade(client, PUBLIC_CLI_MEMBERS),
			freezeFacade(deviceHelper, PUBLIC_DEVICE_MEMBERS),
			freezeFacade(configHelper, PUBLIC_CONFIG_MEMBERS)
		);
	}
	else if (_function === "run") {
		validateRunScript();
		client.userInputs = validateUserInputs(_options.getUserInputs() || {});
		run(
			freezeFacade(client, PUBLIC_CLI_MEMBERS),
			freezeFacade(deviceHelper, PUBLIC_DEVICE_MEMBERS),
			freezeFacade(configHelper, PUBLIC_CONFIG_MEMBERS)
		);
	}
	else if (_function === "diagnostics") {
		const frozenClient = freezeFacade(client, PUBLIC_CLI_MEMBERS);
		const frozenDeviceHelper = freezeFacade(deviceHelper, PUBLIC_DEVICE_MEMBERS);
		const diagnostics = _options.getDiagnosticHelper().getDiagnostics();
		for (let name in diagnostics) {
			try {
				const diagnostic = diagnostics[name];
				diagnosticHelper.setKey(name);
				if (typeof diagnostic === "function") {
					const diagnose = diagnostic;
					diagnose(frozenClient, frozenDeviceHelper, freezeFacade(diagnosticHelper, PUBLIC_DIAGNOSTIC_MEMBERS));
				}
				else {
					frozenClient.macro(diagnostic.getMode());
					const output = frozenClient.command(diagnostic.getCommand());
					diagnosticHelper.set(output);
				}
			}
			catch (diagError) {
				_taskContext.warn(`Error while running diagnostic '${name}'`);
				_taskContext.warn(String(diagError));
			}
		}
	}
}


const _analyzeSyslog = (_message, _taskContext) => {
	if (typeof(analyzeSyslog) === "function") {
		const debug = (message) => {
			if (typeof(message) === "string") {
				_taskContext.debug(message);
			}
		};
		return analyzeSyslog(_message, debug);
	}
	else {
		throw "No analyzeSyslog function.";
	}
}

const _snmpAutoDiscover = (_sysObjectID, _sysDesc, _taskContext) => {
	if (typeof(snmpAutoDiscover) === "function") {
		const debug = (message) => {
			if (typeof(message) === "string") {
				_taskContext.debug(message);
			}
		};
		if (snmpAutoDiscover(_sysObjectID, _sysDesc, debug)) {
			return true;
		}
		else {
			return false;
		}
	}
	else {
		throw "No snmpAutoDiscover function.";
	}
}


const _analyzeTrap = (_data, _taskContext) => {
	if (typeof(analyzeTrap) === "function") {
		const data = { ..._data };
		const debug = (message) => {
			if (typeof(message) === "string") {
				_taskContext.debug(message);
			}
		};
		if (analyzeTrap(data, debug)) {
			return true;
		}
		else {
			return false;
		}
	}
	else {
		throw "No analyzeTrap function.";
	}
}
