import { Position, editor, languages, typescript } from "monaco-editor"
import type { IDisposable } from "monaco-editor"

/**
 * The three distinct shapes of user script recognized by the server-side
 * loaders (driver-loader.js for run scripts/diagnostics, rule-loader.js/.py
 * for compliance rules). Each exposes a different set of objects to the
 * user's function, so editor tooling has to know which one it's editing.
 */
export type ScriptKind = "runScript" | "diagnostic" | "compliance"

/**
 * Ambient TypeScript declarations for the objects passed into `run`,
 * `diagnose` and `check` by driver-loader.js / rule-loader.js. These only
 * describe shapes for editor tooling — nothing here is emitted or executed.
 * Hand-kept in sync with those two files (they're plain JS, not typed) -
 * driver-loader.js's own comments call out the exact Java/JS methods each
 * shape mirrors, so a behavior change there is easy to cross-check here.
 *
 * Because the loaders call the user's function with plain (untyped)
 * parameters, TypeScript can't infer their type on its own: annotate the
 * function signature with JSDoc to get completion, e.g.
 *
 *   /**
 *    * @param {Cli} client
 *    * @param {ScriptDevice} device
 *    * /
 *   function run(client, device) {
 *     client.macro("configure") // <- now autocompletes
 *   }
 *
 * Type names are unique per script kind (ComplianceDevice vs ScriptDevice,
 * etc.) so this single library can be shared by every editor instance.
 */
export const SCRIPT_GLOBALS_LIB = `
/** Options accepted by Cli#command(). */
interface CliCommandOptions {
  /** Force a specific CLI mode (by name) for this command only. */
  mode?: string;
  /** Reset the "strict prompt" match before sending the command. */
  clearPrompt?: boolean;
  /** Don't append a carriage return after the command. */
  noCr?: boolean;
  /** Timeout (ms) to wait for the expected prompt. */
  timeout?: number;
  discoverWaitTime?: number;
}

/** SSH advanced-option override: same shape as \`CLI.ssh.config\`, merged on top of it. Never overrides the port. */
type SshCreateOptions = Record<string, unknown>;

/** Telnet advanced-option override: same shape as \`CLI.telnet.config\`, merged on top of it. Never overrides the port. */
type TelnetCreateOptions = Record<string, unknown>;

/** HTTP advanced-option override. Never overrides the port. */
interface HttpCreateOptions {
  /** Base path prepended to every request path. */
  basePath?: string;
  /** Same shape as HTTP.<access>.auth; replaces the declared auth scheme entirely for this client. */
  auth?: Record<string, unknown>;
}

/** Options accepted by client.create(...). */
interface ClientCreateOptions {
  /** Default true: retry automatically with the next candidate credentials on auth failure. */
  autoTryCredentials?: boolean;
  /** SSH-only advanced overrides (ignored unless the resolved access is SSH). */
  ssh?: SshCreateOptions;
  /** Telnet-only advanced overrides (ignored unless the resolved access is Telnet). */
  telnet?: TelnetCreateOptions;
  /** HTTP-only advanced overrides (ignored unless the resolved access is HTTP/HTTPS). */
  http?: HttpCreateOptions;
}

/**
 * CLI client — the default \`client\` object bound to the device's
 * SSH/Telnet access, or one explicitly obtained via client.create(...).
 */
interface Cli {
  /**
   * Send a command in the current (or given) CLI mode and return its output.
   * @param command the command to send
   * @param options override the CLI mode, prompt handling, or per-command timeout
   */
  command(command: string, options?: CliCommandOptions): string;
  /**
   * Switch to another CLI mode, following the driver's declared macros.
   * @param macroName the name of the macro to run, as declared by the driver (e.g. "configure", "end")
   */
  macro(macroName: string): void;
  /**
   * Pause for the given number of milliseconds.
   * @param millis how long to wait
   */
  sleep(millis: number): void;
  /**
   * Log a debug message to the task log.
   * @param message the message to log
   */
  debug(message: string): void;
  /** Manually advance to the next candidate credential set (autoTryCredentials: false only). */
  tryNextCredentials(): boolean;
  /**
   * Open another client bound to one (or a priority-ordered group of)
   * declared access(es), e.g. client.create("cli"), client.create("snmp"),
   * client.create("http"), or a specific access name declared by the driver.
   * The concrete return type (Cli, SnmpClient or HttpClient) depends on the
   * resolved access — annotate the result if you need completion on it,
   * e.g. \`/** @type {SnmpClient} * / (client.create("snmp"))\`.
   * @param access an access name, group word ("cli"/"snmp"/"http"), or array of names to try in order
   * @param options auto-retry, and/or per-protocol advanced overrides (ssh/telnet/http) - see ClientCreateOptions
   */
  create(access: string | string[], options?: ClientCreateOptions): any;
  /** User-supplied inputs (run scripts only), validated against the top-level Input declaration. */
  userInputs?: Record<string, string>;
}

interface SnmpClient {
  /**
   * SNMP GET on a single OID.
   * @param oid the OID to poll, e.g. "1.3.6.1.2.1.1.1.0"
   */
  get(oid: string): string;
  /**
   * SNMP WALK starting at an OID.
   * @param oid the base OID to walk
   * @param reindex if true, keys of the returned object are relative to \`oid\` (its own index suffix) instead of the full OID
   */
  walk(oid: string, reindex?: boolean): Record<string, string>;
  /**
   * Pause for the given number of milliseconds.
   * @param millis how long to wait
   */
  sleep(millis: number): void;
  /** Manually advance to the next candidate credential set (autoTryCredentials: false only). */
  tryNextCredentials(): boolean;
}

interface HttpRequestConfig {
  method?: string;
  url?: string;
  path?: string;
  headers?: Record<string, string>;
  params?: Record<string, string>;
  query?: Record<string, string>;
  cookies?: Record<string, string>;
  data?: unknown;
  validateStatus?(status: number): boolean;
}

interface HttpResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  config: HttpRequestConfig;
  data: string;
  json?(): unknown;
}

interface HttpClient {
  /**
   * Send a request with full control over method/url/headers/body.
   * @param config the request to send - at minimum \`url\` (or \`path\`) and, if not GET, \`method\`
   */
  request(config: HttpRequestConfig): HttpResponse;
  /**
   * @param url the request path (relative to the client's base path, if any)
   * @param config extra headers/query/cookies, or a custom status validator
   */
  get(url: string, config?: HttpRequestConfig): HttpResponse;
  /**
   * @param url the request path (relative to the client's base path, if any)
   * @param config extra headers/query/cookies, or a custom status validator
   */
  delete(url: string, config?: HttpRequestConfig): HttpResponse;
  /**
   * @param url the request path (relative to the client's base path, if any)
   * @param config extra headers/query/cookies, or a custom status validator
   */
  head(url: string, config?: HttpRequestConfig): HttpResponse;
  /**
   * @param url the request path (relative to the client's base path, if any)
   * @param config extra headers/query/cookies, or a custom status validator
   */
  options(url: string, config?: HttpRequestConfig): HttpResponse;
  /**
   * @param url the request path (relative to the client's base path, if any)
   * @param data the request body - objects are JSON-stringified automatically
   * @param config extra headers/query/cookies, or a custom status validator
   */
  post(url: string, data?: unknown, config?: HttpRequestConfig): HttpResponse;
  /**
   * @param url the request path (relative to the client's base path, if any)
   * @param data the request body - objects are JSON-stringified automatically
   * @param config extra headers/query/cookies, or a custom status validator
   */
  put(url: string, data?: unknown, config?: HttpRequestConfig): HttpResponse;
  /**
   * @param url the request path (relative to the client's base path, if any)
   * @param data the request body - objects are JSON-stringified automatically
   * @param config extra headers/query/cookies, or a custom status validator
   */
  patch(url: string, data?: unknown, config?: HttpRequestConfig): HttpResponse;
  /** Manually advance to the next candidate credential set (autoTryCredentials: false only). */
  tryNextCredentials(): boolean;
  /**
   * Pause for the given number of milliseconds.
   * @param millis how long to wait
   */
  sleep(millis: number): void;
  /**
   * Log a debug message to the task log.
   * @param message the message to log
   */
  debug(message: string): void;
  /**
   * Log a trace message: only shown when the task's full debug log is enabled.
   * @param message the message to log
   */
  trace(message: string): void;
}

/** Options accepted by ScriptDevice#textDownload(). */
interface TextDownloadOptions {
  charset?: string;
  newSession?: boolean;
  method?: "sftp" | "scp";
}

/**
 * Built-in device attribute names recognized directly by device.get()
 * (JsDeviceHelper#getDeviceItem in Java) - independent of any driver.
 * Beyond these, a driver may also declare its own custom attributes (per
 * device type) and diagnostic results (named per configured Diagnostic) are
 * readable the same way - both only exist at runtime, so \`get\`/\`set\`/\`add\`
 * still accept any string; these unions only power autocomplete for the
 * names that are always available, on every device, regardless of driver.
 * Split by return shape (see device.get()'s overloads) rather than one flat
 * union, so each group narrows to its own return type.
 */
type CoreStringGetKey =
  | "type" | "name" | "family" | "managementIpAddress" | "managementDomain"
  | "location" | "contact" | "softwareVersion" | "serialNumber" | "comments";

/** Built-in device attribute names settable via device.set() (JsDeviceHelper#set(String,String) in Java). */
type CoreDeviceSetKey =
  | "name" | "family" | "location" | "contact"
  | "softwareVersion" | "serialNumber" | "comments" | "networkClass";

/** Device.NetworkClass (Java enum) - device.get("networkClass")'s return value, and device.set("networkClass", ...)'s accepted values. */
type DeviceNetworkClass =
  | "FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER"
  | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY";

/** Options accepted by device.add("module", ...) (JsDeviceHelper#add in Java). */
interface ModuleData {
  slot?: string;
  partNumber?: string;
  serialNumber?: string;
}

/** IP usage, mirroring NetworkAddress.AddressUsage (Java) - e.g. for VRRP/HSRP secondary addresses. */
type AddressUsage = "PRIMARY" | "SECONDARY" | "VRRP" | "HSRP" | "SECONDARYVRRP" | "SECONDARYHSRP";

/** One IPv4 entry in device.add("networkInterface", { ip: [...] }). */
interface Ipv4AddressData {
  ip: string;
  /** A prefix length (e.g. 24) or a dotted netmask (e.g. "255.255.255.0"). */
  mask: number | string;
  usage?: AddressUsage;
}

/** One IPv6 entry in device.add("networkInterface", { ip: [...] }). */
interface Ipv6AddressData {
  ipv6: string;
  /** Prefix length, e.g. 64. */
  mask: number;
  usage?: AddressUsage;
}

/** Options accepted by device.add("networkInterface", ...) (JsDeviceHelper#add in Java). */
interface NetworkInterfaceData {
  /** The only required field. */
  name: string;
  virtualDevice?: string;
  vrf?: string;
  /** Default true. */
  enabled?: boolean;
  /** Default true. */
  level3?: boolean;
  description?: string;
  /** Default "0000.0000.0000". */
  mac?: string;
  ip?: (Ipv4AddressData | Ipv6AddressData)[];
}

/** device.get("modules")'s return shape - one entry per hardware module (JsDeviceHelper#getDeviceItem in Java). */
interface ModuleInfo {
  slot: string;
  partNumber: string;
  serialNumber: string;
}

/** One IPv4 entry in device.get("interfaces")'s per-interface \`ip\` array. */
interface Ipv4AddressInfo {
  ip: string;
  /** Always a plain prefix-length string here (e.g. "24") - unlike device.add(), never a dotted netmask or a number. */
  mask: string;
  usage: AddressUsage;
}

/** One IPv6 entry in device.get("interfaces")'s per-interface \`ip\` array. */
interface Ipv6AddressInfo {
  ipv6: string;
  /** Always a plain prefix-length string here (e.g. "64"). */
  mask: string;
  usage: AddressUsage;
}

/** device.get("interfaces")'s return shape - one entry per network interface (JsDeviceHelper#getDeviceItem in Java). */
interface NetworkInterfaceInfo {
  name: string;
  virtualDevice: string;
  vrf: string;
  enabled: boolean;
  level3: boolean;
  description: string;
  mac: string;
  ip: (Ipv4AddressInfo | Ipv6AddressInfo)[];
}

/**
 * The \`device\` object passed to run scripts and diagnostics.
 * NOT the same shape as the compliance rule's device — see ComplianceDevice.
 */
interface ScriptDevice {
  /** Per-device values of the driver-declared Options, as set through the UI. */
  readonly options: Record<string, unknown>;
  /**
   * Store a device attribute value.
   * @param key one of the built-in attribute names, or a driver-declared custom DEVICE-level attribute name
   * @param value the value to store
   */
  set(key: CoreDeviceSetKey | (string & {}), value: string): void;
  /**
   * device.add() only recognizes exactly these 5 collections
   * (JsDeviceHelper#add in Java) - unlike get/set, there's no
   * driver-declared-custom-attribute fallback: any other key silently does
   * nothing. These overloads catch a wrong value shape or an unknown
   * collection name as a real type error (missing/extra/wrong-typed
   * fields) - TypeScript just won't offer live property-name completion
   * *inside* the value object literal (a known TS limitation: overloaded
   * methods don't get the same contextual-typing completion a single
   * non-overloaded signature would).
   * @param collection which collection to append an entry to
   * @param value the entry to append - shape depends on \`collection\`, see the overloads below
   */
  add(collection: "module", value: ModuleData): void;
  add(collection: "networkInterface", value: NetworkInterfaceData): void;
  add(collection: "vrf", value: string): void;
  add(collection: "virtualDevice", value: string): void;
  /**
   * Add a user-defined tag to the device (ignored if already present or blank).
   * @param collection "tag"
   * @param value the tag to add
   */
  add(collection: "tag", value: string): void;
  /**
   * Remove an entry from a device collection - only "tag" is supported.
   * @param collection "tag"
   * @param value the tag to remove
   */
  remove(collection: "tag", value: string): void;
  /**
   * Read a built-in device attribute (a plain string one - see the other
   * overloads below for networkClass/virtualDevices/vrfs/tags/modules/interfaces,
   * which each return a more specific shape).
   * @param key one of the built-in string attribute names
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: CoreStringGetKey, id?: number | string): string;
  /**
   * @param key "networkClass"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "networkClass", id?: number | string): DeviceNetworkClass;
  /**
   * @param key "virtualDevices", "vrfs" or "tags" (the user-defined device tags, sorted)
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "virtualDevices" | "vrfs" | "tags", id?: number | string): string[];
  /**
   * @param key "modules"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "modules", id?: number | string): ModuleInfo[];
  /**
   * @param key "interfaces"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "interfaces", id?: number | string): NetworkInterfaceInfo[];
  /**
   * Read a driver-declared custom attribute or a configured Diagnostic's
   * result by name - returns \`any\`, since neither is known ahead of time.
   * @param key a driver-declared custom attribute name, or a configured Diagnostic's name
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: string, id?: number | string): any;
  /**
   * Download a file from the device over SFTP/SCP and return its text content.
   * @param fileName the full path of the remote file to download
   * @param options download options (method, charset, session reuse)
   */
  textDownload(fileName: string, options?: TextDownloadOptions): string;
}

/** Options accepted by config.download(). */
interface ConfigDownloadOptions {
  newSession?: boolean;
  method?: "sftp" | "scp";
  storeFileName?: string;
  checksum?: string;
}

/** Options accepted by config.requestUpload(). */
interface ConfigUploadRequestOptions {
  method?: "scp" | "sftp";
  sourceIp?: string;
}

/** Options accepted by config.commitUpload(). */
interface ConfigCommitUploadOptions {
  storeName?: string;
  checksum?: string;
}

/** The \`config\` object passed as the 3rd argument to run scripts. */
interface ConfigHelper {
  /**
   * Store a config attribute value.
   * @param key only "author" is a built-in key (JsConfigHelper#set in Java) - anything else must be a driver-declared CONFIG-level custom attribute
   * @param value the value to store
   */
  set(key: "author" | (string & {}), value: string): void;
  /**
   * Download a driver-declared CONFIG-level file attribute from the device and store it as a config file.
   * @param key the driver-declared attribute name to store the downloaded content under
   * @param fileName the full path of the remote file to download
   * @param options download options (method, session reuse, checksum, alternate store name)
   */
  download(key: string, fileName: string, options?: ConfigDownloadOptions): void;
  /**
   * Compute and store a custom hash of the config, from arbitrary string parts (instead of the default full-config hash) - used to detect "no real change" more precisely than a byte-for-byte diff.
   * @param parts the strings to hash together, in order - null entries are allowed
   */
  computeHash(...parts: (string | null)[]): void;
  /** The custom hash computed for this run via computeHash(), if any. */
  getHash(): string;
  /** The custom hash stored from the previous run, for comparison. */
  getLastHash(): string;
  /** True if getHash() differs from getLastHash(). */
  isChangedHash(): boolean;
  /**
   * Ask the device to open an upload channel (SCP/SFTP) so a subsequent step of this script can push a file to it.
   * @param options which upload method to require, and/or a specific source IP to upload from
   * @returns a ticket id to pass to awaitUpload()/commitUpload()
   */
  requestUpload(options?: ConfigUploadRequestOptions): number;
  /**
   * Wait for a file to be uploaded to the channel opened by requestUpload().
   * @param ticketId the ticket id returned by requestUpload()
   * @param timeout how long to wait, in milliseconds (default 60000)
   */
  awaitUpload(ticketId: number, timeout?: number): unknown;
  /**
   * Store an uploaded file (see requestUpload()/awaitUpload()) as a config attribute.
   * @param ticketId the ticket id returned by requestUpload()
   * @param fileId the id of the specific uploaded file to commit
   * @param key the driver-declared attribute name to store the file's content under
   * @param options an alternate store name, and/or a checksum to verify the upload against
   */
  commitUpload(ticketId: number, fileId: number, key: string, options?: ConfigCommitUploadOptions): void;
}

/** The \`diagnostic\` object passed as the 3rd argument to diagnostic scripts. */
interface DiagnosticHelper {
  /** Store the result for this diagnostic (implicit key: the diagnostic's own name). */
  set(value: string): void;
  /**
   * Store the result for another diagnostic, by name.
   * @param key the name of the (other) diagnostic to store a result for
   * @param value the value to store
   */
  set(key: string, value: string): void;
}

/**
 * The \`device\` object passed to compliance rule scripts (check(device)).
 * Narrower than ScriptDevice: read-only except for the device comments and
 * tags (rule-loader.js), no textDownload.
 */
interface ComplianceDevice {
  /**
   * Replace the device comments - the only field a compliance rule can set.
   * @param key "comments"
   * @param value the new comments
   */
  set(key: "comments", value: string): void;
  /**
   * Add a user-defined tag to the device (ignored if already present or blank).
   * @param collection "tag"
   * @param value the tag to add
   */
  add(collection: "tag", value: string): void;
  /**
   * Remove a user-defined tag from the device.
   * @param collection "tag"
   * @param value the tag to remove
   */
  remove(collection: "tag", value: string): void;
  /**
   * Read a built-in device attribute (a plain string one - see the other
   * overloads below for networkClass/virtualDevices/vrfs/tags/modules/interfaces,
   * which each return a more specific shape). Same behavior as
   * ScriptDevice#get() (compliance checks reuse JsDeviceHelper, read-only).
   * @param key one of the built-in string attribute names
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: CoreStringGetKey, id?: number | string): string;
  /**
   * @param key "networkClass"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "networkClass", id?: number | string): DeviceNetworkClass;
  /**
   * @param key "virtualDevices", "vrfs" or "tags" (the user-defined device tags, sorted)
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "virtualDevices" | "vrfs" | "tags", id?: number | string): string[];
  /**
   * @param key "modules"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "modules", id?: number | string): ModuleInfo[];
  /**
   * @param key "interfaces"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "interfaces", id?: number | string): NetworkInterfaceInfo[];
  /**
   * Read a driver-declared custom attribute or a configured Diagnostic's
   * result by name - returns \`any\`, since neither is known ahead of time.
   * @param key a driver-declared custom attribute name, or a configured Diagnostic's name
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: string, id?: number | string): any;
  /**
   * Resolve a hostname to an IP address, or an IP address to a hostname (reverse DNS).
   * @param host the hostname or IP address to resolve
   */
  nslookup(host: string): unknown;
}

/** Compliance rule verdicts (JS rule scripts — result_option.* in Python). */
type ComplianceVerdict = "CONFORMING" | "NONCONFORMING" | "NOTAPPLICABLE";
declare const CONFORMING: "CONFORMING";
declare const NONCONFORMING: "NONCONFORMING";
declare const NOTAPPLICABLE: "NOTAPPLICABLE";

/**
 * What a compliance rule's check() may return: either a bare verdict, or
 * an object pairing a verdict with an explanatory comment (rule-loader.js
 * wraps a bare string return into { result: r, comment: "" } itself).
 */
interface ComplianceResult {
  result: ComplianceVerdict;
  comment?: string;
}

/** The \`debug\` function passed as the 2nd argument to JS compliance rule scripts. */
type DebugFn = (message: string) => void;

/** Configuration syntaxes supported by Toolbox.ConfigTree.parse(). */
type ConfigSyntax = "indent" | "braces" | "fortios";

/** Options accepted by Toolbox.ConfigTree.parse(). */
interface ConfigParseOptions {
  /**
   * "indent" (default - Cisco IOS, NX-OS, Arista EOS, Aruba...: children are more indented),
   * "braces" (Juniper Junos, Palo Alto PAN-OS: { } blocks) or
   * "fortios" (Fortinet: config/edit blocks ended by end/next).
   */
  syntax?: ConfigSyntax;
  /**
   * Keep the original indentation in the node text, so that the queries match it
   * (e.g. /^\\s+shutdown$/), as ciscoconfparse does (default false: trimmed text).
   */
  indented?: boolean;
}

/** Options accepted by the ConfigNode queries. */
interface ConfigQueryOptions {
  /** Look into all descendants instead of the direct children only (default false). */
  recursive?: boolean;
}

/**
 * A node of a configuration parsed by Toolbox.ConfigTree.parse(): one configuration line
 * (or block opening line) along with its children. The root node (returned by parse())
 * holds the top-level lines as children. Queries match their regex against the node \`text\`.
 */
interface ConfigNode {
  /** The line text, trimmed (without the { and ; delimiters with the "braces" syntax), or indented with the indented option. */
  readonly text: string;
  /** The original line(s), as found in the configuration. */
  readonly raw: string;
  /** The (1-based) line number in the configuration, 0 for the root node. */
  readonly line: number;
  /** The number of leading whitespace characters, -1 for the root node. */
  readonly indent: number;
  /** The parent node: the node itself for a top-level line, null for the root node. */
  readonly parent: ConfigNode | null;
  /** Whether this is a comment line (! lines, # lines, C-style comments). */
  readonly isComment: boolean;
  /** The child nodes. */
  readonly children: ConfigNode[];
  /** On nodes returned by a query, the match of the query regex against the node text. */
  readonly match: RegExpExecArray | null;
  /** The line closing the block, if any (e.g. "}", "next" or "end"). */
  readonly closing: string | null;
  /** The (original) lines of all the descendant nodes. */
  readonly lines: string[];
  /** The (original) text of all the descendant nodes. */
  readonly config: string;
  /** The text of the ancestor nodes and of this node, from the top level. */
  readonly path: string[];
  /**
   * Find the child nodes whose text matches the regex.
   * @param regex matched against the text of each node
   * @param options e.g. { recursive: true } to look into all descendants
   */
  find(regex: RegExp | string, options?: ConfigQueryOptions): ConfigNode[];
  /**
   * Whether at least one child node matches the regex.
   * @param regex matched against the text of each node
   * @param options e.g. { recursive: true } to look into all descendants
   */
  hasChild(regex: RegExp | string, options?: ConfigQueryOptions): boolean;
  /**
   * Find the child nodes matching parentRegex which have a direct child matching childRegex.
   * @param options e.g. { recursive: true } to look for the parents among all descendants
   */
  findWithChild(parentRegex: RegExp | string, childRegex: RegExp | string, options?: ConfigQueryOptions): ConfigNode[];
  /**
   * Find the child nodes matching parentRegex which have no direct child matching childRegex.
   * @param options e.g. { recursive: true } to look for the parents among all descendants
   */
  findWithoutChild(parentRegex: RegExp | string, childRegex: RegExp | string, options?: ConfigQueryOptions): ConfigNode[];
  /**
   * The first capture group (or whole match) of the first child node matching the regex, or null.
   * @param regex e.g. /^hostname (\\S+)/
   * @param options e.g. { recursive: true } to look into all descendants
   */
  findValue(regex: RegExp | string, options?: ConfigQueryOptions): string | null;
  /**
   * The first capture group (or whole match) of each child node matching the regex.
   * @param regex e.g. /^ntp server (\\S+)/
   * @param options e.g. { recursive: true } to look into all descendants
   */
  findValues(regex: RegExp | string, options?: ConfigQueryOptions): string[];
  /**
   * Find the child nodes for which the predicate returns true.
   * @param options e.g. { recursive: true } to look into all descendants
   */
  filter(predicate: (node: ConfigNode) => boolean, options?: ConfigQueryOptions): ConfigNode[];
  /**
   * The settings ("set <key> <values...>" direct child lines, FortiOS-style), as a key -> values map.
   * Values are unquoted; a later line overrides an earlier one, "unset <key>" removes the key.
   */
  settings(): Record<string, string[]>;
  /**
   * The unquoted values of a setting ("set <key> <values...>" direct child line), empty if not found.
   * @param key e.g. "allowaccess"
   */
  settingValues(key: string): string[];
  /**
   * The unquoted value of a setting ("set <key> <values...>" direct child line),
   * multiple values joined with a space, or null if not found.
   * @param key e.g. "role"
   */
  setting(key: string): string | null;
}

/** The configuration parser. */
interface ConfigTreeParser {
  /** The supported syntaxes. */
  readonly SYNTAXES: readonly ConfigSyntax[];
  /**
   * Parse a configuration into a tree, and return the root node.
   * @param text the configuration (e.g. from device.get("runningConfig"))
   * @param options e.g. { syntax: "braces" } (default syntax is "indent"), { indented: true } to keep the indentation in the node text
   */
  parse(text: string, options?: ConfigParseOptions): ConfigNode;
}

/** The Netshot script toolbox (available to drivers, compliance rules and diagnostics). */
interface NetshotToolbox {
  /** Configuration parser, e.g. Toolbox.ConfigTree.parse(config).findValue(/^hostname (\\S+)/). */
  readonly ConfigTree: ConfigTreeParser;
}

/** The Netshot script toolbox (available to drivers, compliance rules and diagnostics). */
declare const Toolbox: NetshotToolbox;
`

const SCRIPT_GLOBALS_LIB_PATH = "ts:netshot-script-globals.d.ts"

/**
 * Registers SCRIPT_GLOBALS_LIB. Monaco's TS support keeps two entirely
 * separate registries — \`typescriptDefaults\` and \`javascriptDefaults\` —
 * each with its own extra-libs list, compiler options and worker; an editor
 * using language "javascript" (diagnostics, compliance rules) is wired to
 * \`javascriptDefaults\` and never sees anything added only to
 * \`typescriptDefaults\`. Both get the lib, but only a "javascript" editor
 * resolves the JSDoc-typed params: TypeScript ignores JSDoc types in a TS
 * file (the params stay implicit \`any\`, and its quick fix inserts TS type
 * annotations, which the GraalJS runtime can't run). Script editors must
 * therefore use the "javascript" language.
 *
 * A stable filePath is required in both calls: without one, Monaco mints a
 * fresh random key every call and never dedupes, so re-evaluating this
 * module (e.g. Vite HMR) would pile up duplicate extra libs. With a stable
 * path, Monaco's own content-equality check makes repeated calls a no-op.
 *
 * Also restricts \`lib\` to "esnext": Monaco's TS defaults don't set an
 * explicit one, so TypeScript falls back to including the full
 * DOM/ScriptHost typings alongside the core ECMAScript ones - none of which
 * exist in the real runtime (a headless GraalJS context: no
 * window/document/fetch, and \`console\` is explicitly disabled server-side
 * for compliance rules). Restricting removes that pollution
 * (StereoPannerNode, console, etc.) from completions.
 */
export function registerNetshotScriptGlobals() {
  for (const defaults of [typescript.typescriptDefaults, typescript.javascriptDefaults]) {
    defaults.addExtraLib(SCRIPT_GLOBALS_LIB, SCRIPT_GLOBALS_LIB_PATH)
    defaults.setCompilerOptions({ ...defaults.getCompilerOptions(), lib: ["esnext"] })
  }
}

/** Tracks which ScriptKind a given Monaco model was created for (Python has no real language service, so completion has to be driven off this instead of inferred types). */
export const scriptKindByModel = new WeakMap<editor.ITextModel, ScriptKind>()

type PyMember = {
  label: string
  insertText: string
  detail: string
  documentation?: string
  kind: languages.CompletionItemKind
}

const method = (label: string, insertText: string, detail: string, documentation?: string): PyMember => ({
  label,
  insertText,
  detail,
  documentation,
  kind: languages.CompletionItemKind.Method,
})
const field = (label: string, detail: string, documentation?: string): PyMember => ({
  label,
  insertText: label,
  detail,
  documentation,
  kind: languages.CompletionItemKind.Field,
})
const constant = (label: string, detail: string): PyMember => ({
  label,
  insertText: label,
  detail,
  kind: languages.CompletionItemKind.EnumMember,
})
const func = (label: string, insertText: string, detail: string): PyMember => ({
  label,
  insertText,
  detail,
  kind: languages.CompletionItemKind.Function,
})

const CLI_MEMBERS: PyMember[] = [
  method("command", 'command("$1")', "command(command, options=None) -> str", "Send a command in the current CLI mode and return its output."),
  method("macro", 'macro("$1")', "macro(macro_name) -> None", "Switch to another CLI mode, following the driver's declared macros."),
  method("create", 'create("$1")', "create(access, options=None)", 'Open another client bound to a declared access, e.g. "cli", "snmp", "http".'),
  method("sleep", "sleep($1)", "sleep(millis) -> None"),
  method("debug", 'debug("$1")', "debug(message) -> None"),
  method("tryNextCredentials", "tryNextCredentials()", "tryNextCredentials() -> bool"),
]

const SCRIPT_DEVICE_MEMBERS: PyMember[] = [
  method("get", 'get("$1")', "get(key, id=None)", "Read a device attribute (optionally by id/name, for collections)."),
  method("set", 'set("$1", "$2")', "set(key, value) -> None", "Store a device attribute value."),
  method("add", 'add("$1", $2)', "add(collection, value) -> None", "Append a value to a device attribute collection (e.g. add(\"tag\", \"core\"))."),
  method("remove", 'remove("tag", "$1")', "remove(collection, value) -> None", "Remove a value from a device collection (only \"tag\")."),
  method("textDownload", 'textDownload("$1")', "textDownload(fileName, options=None) -> str"),
  field("options", "dict of driver Options for this device"),
]

const DIAGNOSTIC_HELPER_MEMBERS: PyMember[] = [
  method("set", 'set("$1")', "set(value) -> None", "Store the result for this diagnostic (implicit key)."),
  method("set", 'set("$1", "$2")', "set(key, value) -> None", "Store the result for another diagnostic, by name."),
]

const COMPLIANCE_DEVICE_MEMBERS: PyMember[] = [
  method("get", 'get("$1")', "get(key, id=None)"),
  method("set", 'set("comments", "$1")', "set(key, value) -> None", "Replace the device comments (only \"comments\" can be set)."),
  method("add", 'add("tag", "$1")', "add(collection, value) -> None", "Add a tag to the device (only \"tag\")."),
  method("remove", 'remove("tag", "$1")', "remove(collection, value) -> None", "Remove a tag from the device (only \"tag\")."),
  method("nslookup", 'nslookup("$1")', "nslookup(host)"),
]

const RESULT_OPTION_MEMBERS: PyMember[] = [
  constant("CONFORMING", "result_option.CONFORMING"),
  constant("NONCONFORMING", "result_option.NONCONFORMING"),
  constant("NOTAPPLICABLE", "result_option.NOTAPPLICABLE"),
]

const CONFIG_TREE_MEMBERS: PyMember[] = [
  method("parse", "parse($1)", 'parse(text, syntax="indent", indented=False) -> ConfigNode', 'Parse a configuration into a tree, returns the root node. syntax: "indent" (IOS-like), "braces" (Junos, PAN-OS) or "fortios". indented=True keeps the indentation in the node text (as ciscoconfparse).'),
  field("SYNTAXES", "tuple of the supported syntaxes"),
]

/** ciscoconfparse-compatible parser members (netshot.toolbox CiscoConfParse, and aliases on any ConfigNode). */
const CISCO_CONF_PARSE_MEMBERS: PyMember[] = [
  method("find_objects", 'find_objects(r"$1")', "find_objects(linespec, exactmatch=False, ignore_ws=False) -> list"),
  method("find_lines", 'find_lines(r"$1")', "find_lines(linespec, exactmatch=False, ignore_ws=False) -> list"),
  method("find_objects_w_child", 'find_objects_w_child(r"$1", r"$2")', "find_objects_w_child(parentspec, childspec, ignore_ws=False, recurse=False) -> list"),
  method("find_objects_wo_child", 'find_objects_wo_child(r"$1", r"$2")', "find_objects_wo_child(parentspec, childspec, ignore_ws=False, recurse=False) -> list"),
  method("find_objects_w_all_children", 'find_objects_w_all_children(r"$1", [r"$2"])', "find_objects_w_all_children(parentspec, childspec, ignore_ws=False, recurse=False) -> list"),
  method("find_objects_w_missing_children", 'find_objects_w_missing_children(r"$1", [r"$2"])', "find_objects_w_missing_children(parentspec, childspec, ignore_ws=False) -> list"),
  method("find_objects_w_parents", 'find_objects_w_parents(r"$1", r"$2")', "find_objects_w_parents(parentspec, childspec, ignore_ws=False) -> list"),
  method("find_children", 'find_children(r"$1")', "find_children(linespec, exactmatch=False, ignore_ws=False) -> list"),
  method("find_all_children", 'find_all_children(r"$1")', "find_all_children(linespec, exactmatch=False, ignore_ws=False) -> list"),
  method("find_parents_w_child", 'find_parents_w_child(r"$1", r"$2")', "find_parents_w_child(parentspec, childspec, ignore_ws=False) -> list"),
  method("find_parents_wo_child", 'find_parents_wo_child(r"$1", r"$2")', "find_parents_wo_child(parentspec, childspec, ignore_ws=False) -> list"),
  method("has_line_with", 'has_line_with(r"$1")', "has_line_with(linespec) -> bool"),
  method("re_match_iter_typed", 're_match_iter_typed(r"$1")', 're_match_iter_typed(regexspec, group=1, result_type=str, default="")'),
  field("objs", "list of IOSCfgLine"),
  field("ioscfg", "list of the configuration lines"),
]

/** ConfigNode members (netshot.toolbox), offered on objects not otherwise known in scripts importing netshot.toolbox. */
const CONFIG_NODE_MEMBERS: PyMember[] = [
  method("find", 'find(r"$1")', "find(regex, recursive=False) -> list", "Child nodes whose text matches the regex (each with .match set)."),
  method("find_with_child", 'find_with_child(r"$1", r"$2")', "find_with_child(parent_regex, child_regex, recursive=False) -> list", "Child nodes matching parent_regex with a direct child matching child_regex."),
  method("find_without_child", 'find_without_child(r"$1", r"$2")', "find_without_child(parent_regex, child_regex, recursive=False) -> list", "Child nodes matching parent_regex without any direct child matching child_regex."),
  method("has_child", 'has_child(r"$1")', "has_child(regex, recursive=False) -> bool"),
  method("find_value", 'find_value(r"$1")', "find_value(regex, recursive=False) -> str | None", "First capture group (or whole match) of the first matching child node."),
  method("find_values", 'find_values(r"$1")', "find_values(regex, recursive=False) -> list", "First capture group (or whole match) of each matching child node."),
  method("filter", "filter(lambda node: $1)", "filter(predicate, recursive=False) -> list"),
  method("settings", "settings()", "settings() -> dict", "The \"set <key> <values...>\" direct child lines, as a key -> list of unquoted values dict."),
  method("setting", 'setting("$1")', "setting(key) -> str | None", "Unquoted value of a \"set <key> ...\" direct child line (multiple values joined with a space)."),
  method("setting_values", 'setting_values("$1")', "setting_values(key) -> list", "Unquoted values of a \"set <key> ...\" direct child line (empty if not found)."),
  field("text", "str - the line text, trimmed"),
  field("raw", "str - the original line(s)"),
  field("line", "int - the line number (1-based)"),
  field("indent", "int - the number of leading whitespace characters"),
  field("parent", "ConfigNode | None"),
  field("children", "list of ConfigNode"),
  field("match", "re.Match | None - set on nodes returned by queries"),
  field("config", "str - the original text of all the descendant nodes"),
  field("lines", "list - the original lines of all the descendant nodes"),
  field("path", "list - the text of the ancestors and of this node"),
  field("is_comment", "bool - whether this is a comment line"),
  field("linenum", "int - ciscoconfparse-compatible line number (0-based)"),
  method("re_search_children", 're_search_children(r"$1")', "re_search_children(regex, recurse=False) -> list", "ciscoconfparse-compatible: child nodes matching the regex."),
  method("re_match_typed", 're_match_typed(r"$1")', 're_match_typed(regex, group=1, result_type=str, default="")', "ciscoconfparse-compatible: typed capture group of this line."),
  method("has_child_with", 'has_child_with(r"$1")', "has_child_with(linespec, all_children=False) -> bool"),
  field("all_children", "list - ciscoconfparse-compatible: all the descendant nodes"),
  field("all_parents", "list - ciscoconfparse-compatible: all the ancestor nodes"),
  field("geneology_text", "list - ciscoconfparse-compatible: text of the ancestors and of this node"),
  ...CISCO_CONF_PARSE_MEMBERS,
]

/** Bare-name suggestions for scripts importing from netshot.toolbox (Python rules and diagnostics). */
const TOOLBOX_GLOBALS: PyMember[] = [
  {
    label: "from netshot.toolbox import",
    insertText: "from netshot.toolbox import ${1:ConfigTree}",
    detail: "Netshot script toolbox",
    documentation: "ConfigTree (configuration parser), CiscoConfParse (ciscoconfparse-compatible parser).",
    kind: languages.CompletionItemKind.Module,
  },
  func("ConfigTree", "ConfigTree.parse($1)", 'ConfigTree.parse(text, syntax="indent") -> ConfigNode'),
  func("CiscoConfParse", "CiscoConfParse($1)", 'CiscoConfParse(config, syntax="ios")'),
]

/** Object names conventionally bound to the toolbox classes (no type inference here). */
const TOOLBOX_MEMBERS: Record<string, PyMember[]> = {
  ConfigTree: CONFIG_TREE_MEMBERS,
  CiscoConfParse: CISCO_CONF_PARSE_MEMBERS,
  parse: CISCO_CONF_PARSE_MEMBERS,
  confparse: CISCO_CONF_PARSE_MEMBERS,
  ccp: CISCO_CONF_PARSE_MEMBERS,
}

/** Per-ScriptKind member lookup, keyed by the parameter/object name it applies to. */
const MEMBERS_BY_KIND: Partial<Record<ScriptKind, Record<string, PyMember[]>>> = {
  diagnostic: {
    client: CLI_MEMBERS,
    cli: CLI_MEMBERS,
    device: SCRIPT_DEVICE_MEMBERS,
    diagnostic: DIAGNOSTIC_HELPER_MEMBERS,
  },
  compliance: {
    device: COMPLIANCE_DEVICE_MEMBERS,
    result_option: RESULT_OPTION_MEMBERS,
  },
}

/** Script kinds whose Python scripts get the netshot.toolbox module. */
const TOOLBOX_KINDS: ScriptKind[] = ["diagnostic", "compliance"]

/** Bare (no-dot) global suggestions, per ScriptKind. */
const GLOBALS_BY_KIND: Partial<Record<ScriptKind, PyMember[]>> = {
  compliance: [
    field("result_option", "class result_option", "Holds CONFORMING / NONCONFORMING / NOTAPPLICABLE."),
    func("debug", 'debug("$1")', "debug(message) -> None"),
  ],
}

/**
 * Members to suggest after "<name>." in a Python script: the ScriptKind's own
 * objects first, then the toolbox classes, then - in scripts importing from
 * netshot.toolbox - the ConfigNode members for any other name (most likely
 * a parsed configuration or a query result, since there's no type inference).
 */
function pythonMembersFor(kind: ScriptKind, name: string, model: editor.ITextModel): PyMember[] | undefined {
  const kindMembers = MEMBERS_BY_KIND[kind]?.[name]
  if (kindMembers) return kindMembers
  if (!TOOLBOX_KINDS.includes(kind)) return undefined
  if (TOOLBOX_MEMBERS[name]) return TOOLBOX_MEMBERS[name]
  if (/\bnetshot\.toolbox\b/.test(model.getValue())) return CONFIG_NODE_MEMBERS
  return undefined
}

/** Bare-name suggestions in a Python script, for the given ScriptKind. */
function pythonGlobalsFor(kind: ScriptKind): PyMember[] | undefined {
  if (!TOOLBOX_KINDS.includes(kind)) return GLOBALS_BY_KIND[kind]
  return [...(GLOBALS_BY_KIND[kind] ?? []), ...TOOLBOX_GLOBALS]
}

let pythonCompletionsProvider: IDisposable | undefined

/**
 * Registers a lightweight, snippet-based completion provider for Python
 * script editors. Monaco ships no real Python language service (no type
 * checker), so this can't offer the same fidelity as the TS/JS side — it's
 * a static member list per ScriptKind, looked up from scriptKindByModel.
 *
 * Safe to call more than once; only registers globally the first time.
 * Unlike addExtraLib, registerCompletionItemProvider has no dedupe of its
 * own — every call adds another provider — so under Vite HMR (this module
 * re-evaluated when netshotScriptSupport.ts or MonacoEditor.tsx changes) the
 * previous provider must be explicitly disposed before the module re-runs,
 * or completions start showing duplicate entries.
 */
export function registerNetshotPythonCompletions() {
  if (pythonCompletionsProvider) return

  pythonCompletionsProvider = languages.registerCompletionItemProvider("python", {
    triggerCharacters: ["."],
    provideCompletionItems(model: editor.ITextModel, position: Position) {
      const kind = scriptKindByModel.get(model)
      if (!kind) return { suggestions: [] }

      const wordInfo = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: wordInfo.startColumn,
        endColumn: wordInfo.endColumn,
      }

      const textUntilPosition = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      })
      const dotMatch = textUntilPosition.match(/([A-Za-z_][A-Za-z0-9_]*)\.\w*$/)

      const members = dotMatch ? pythonMembersFor(kind, dotMatch[1], model) : pythonGlobalsFor(kind)
      if (!members) return { suggestions: [] }

      return {
        suggestions: members.map((m) => ({
          label: m.label,
          kind: m.kind,
          detail: m.detail,
          documentation: m.documentation,
          insertText: m.insertText,
          insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
        })),
      }
    },
  })

  import.meta.hot?.dispose(() => {
    pythonCompletionsProvider?.dispose()
    pythonCompletionsProvider = undefined
  })
}
