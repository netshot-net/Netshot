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

/** A section of text matched by findSections(). */
interface TextSection {
  match: RegExpExecArray;
  lines: string[];
  config: string;
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
   * Split multi-line text into indented sections matching \`regex\`.
   * @param text the multi-line text to split (typically a command's output)
   * @param regex matched against each line to decide where a new section starts
   */
  findSections(text: string, regex: RegExp): TextSection[];
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
   * device.add() only recognizes exactly these 4 collections
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
   * Read a built-in device attribute (a plain string one - see the other
   * overloads below for networkClass/virtualDevices/vrfs/modules/interfaces,
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
   * @param key "virtualDevices" or "vrfs"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "virtualDevices" | "vrfs", id?: number | string): string[];
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
 * Narrower than ScriptDevice: read-only, no set/add/textDownload.
 */
interface ComplianceDevice {
  /**
   * Read a built-in device attribute (a plain string one - see the other
   * overloads below for networkClass/virtualDevices/vrfs/modules/interfaces,
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
   * @param key "virtualDevices" or "vrfs"
   * @param id look up this attribute on another device instead of the current one - a device id (number) or name (string)
   */
  get(key: "virtualDevices" | "vrfs", id?: number | string): string[];
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
  /**
   * Split multi-line text into indented sections matching \`regex\` (JS rules only).
   * @param text the multi-line text to split (typically a config, e.g. from device.get("runningConfig"))
   * @param regex matched against each line to decide where a new section starts
   */
  findSections(text: string, regex: RegExp): TextSection[];
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
`

const SCRIPT_GLOBALS_LIB_PATH = "ts:netshot-script-globals.d.ts"

/**
 * Registers SCRIPT_GLOBALS_LIB. Monaco's TS support keeps two entirely
 * separate registries — \`typescriptDefaults\` and \`javascriptDefaults\` —
 * each with its own extra-libs list, compiler options and worker; an editor
 * using language "javascript" (diagnostics, compliance rules) is wired to
 * \`javascriptDefaults\` and never sees anything added only to
 * \`typescriptDefaults\`. Both need the lib registered for JSDoc-typed
 * params to resolve regardless of which of the two languages an editor uses.
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
  method("findSections", "findSections($1, $2)", "findSections(text, regex) -> list"),
  method("sleep", "sleep($1)", "sleep(millis) -> None"),
  method("debug", 'debug("$1")', "debug(message) -> None"),
  method("tryNextCredentials", "tryNextCredentials()", "tryNextCredentials() -> bool"),
]

const SCRIPT_DEVICE_MEMBERS: PyMember[] = [
  method("get", 'get("$1")', "get(key, id=None)", "Read a device attribute (optionally by id/name, for collections)."),
  method("set", 'set("$1", "$2")', "set(key, value) -> None", "Store a device attribute value."),
  method("add", 'add("$1", $2)', "add(collection, value) -> None", "Append a value to a device attribute collection."),
  method("textDownload", 'textDownload("$1")', "textDownload(fileName, options=None) -> str"),
  field("options", "dict of driver Options for this device"),
]

const DIAGNOSTIC_HELPER_MEMBERS: PyMember[] = [
  method("set", 'set("$1")', "set(value) -> None", "Store the result for this diagnostic (implicit key)."),
  method("set", 'set("$1", "$2")', "set(key, value) -> None", "Store the result for another diagnostic, by name."),
]

const COMPLIANCE_DEVICE_MEMBERS: PyMember[] = [
  method("get", 'get("$1")', "get(key, id=None)"),
  method("nslookup", 'nslookup("$1")', "nslookup(host)"),
]

const RESULT_OPTION_MEMBERS: PyMember[] = [
  constant("CONFORMING", "result_option.CONFORMING"),
  constant("NONCONFORMING", "result_option.NONCONFORMING"),
  constant("NOTAPPLICABLE", "result_option.NOTAPPLICABLE"),
]

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

/** Bare (no-dot) global suggestions, per ScriptKind. */
const GLOBALS_BY_KIND: Partial<Record<ScriptKind, PyMember[]>> = {
  compliance: [
    field("result_option", "class result_option", "Holds CONFORMING / NONCONFORMING / NOTAPPLICABLE."),
    func("debug", 'debug("$1")', "debug(message) -> None"),
  ],
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

      const members = dotMatch ? MEMBERS_BY_KIND[kind]?.[dotMatch[1]] : GLOBALS_BY_KIND[kind]
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
