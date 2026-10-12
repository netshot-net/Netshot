# Script toolbox

The script toolbox is a set of helpers available to all user scripts: [script-based compliance rules](compliance.md#script-based-rules), [diagnostics](diagnostics.md), [device drivers](../extending/writing-a-driver.md), and device scripts. Its main component is **ConfigTree**, a configuration parser that turns a device configuration into a tree of lines, to be queried with regular expressions — no need to install a third-party library such as `ciscoconfparse` in a [Python virtualenv](../extending/python-virtualenv.md).

| Language | How to access the toolbox |
|----------|---------------------------|
| JavaScript | the global `Toolbox` object, e.g. `Toolbox.ConfigTree.parse(config)` |
| Python | the `netshot.toolbox` module, e.g. `from netshot.toolbox import ConfigTree` |

Both implementations are native (pure JavaScript and pure Python code), and behave identically; only the naming style differs (`findWithChild` in JavaScript, `find_with_child` in Python).

## ConfigTree

### Parsing a configuration

```javascript
const config = Toolbox.ConfigTree.parse(device.get("runningConfig"));
```

```python
from netshot.toolbox import ConfigTree

config = ConfigTree.parse(device.get("running_config"))
```

`parse` returns the root node of the tree, whose children are the top-level configuration lines. The configuration syntax is passed as an option — `{ syntax: "braces" }` in JavaScript, `syntax="braces"` in Python:

| Syntax | Devices | Structure |
|--------|---------|-----------|
| `indent` (default) | Cisco IOS, IOS-XE, IOS-XR, NX-OS, Arista EOS, Aruba, and most indentation-based configurations | A line is a child of the previous less indented line. Blank lines are skipped, comment lines (starting with `!`) are kept as comment nodes. A multi-line banner (`banner motd ^C` ... `^C`, or Arista-like `banner login` ... `EOF`) is kept as a single node. |
| `braces` | Juniper Junos, Palo Alto PAN-OS | `name {` opens a block, `}` closes it, statements end with `;`. These delimiters are removed from the node text (`host-name mx1;` becomes `host-name mx1`). `#` comment lines and `/* ... */` comments (possibly multi-line) are kept as comment nodes. Prefixes like `inactive:` are kept in the text. |
| `fortios` | Fortinet FortiOS, FortiMail... | `config ...` blocks end with `end`, `edit ...` blocks end with `next`. `#` comment lines are kept as comment nodes, a quoted value spanning multiple lines (e.g. a certificate) is kept as a single node. |

By default, the node text is trimmed. With the `indented` option (`{ indented: true }` in JavaScript, `indented=True` in Python), the node text keeps its original indentation, and the queries match it, as [ciscoconfparse](#migrating-from-ciscoconfparse) does: for example `^\s+shutdown$` matches the ` shutdown` line of an interface, and `^interface` only matches the top-level lines.

```python
config = ConfigTree.parse(device.get("running_config"), indented=True)
shut = config.find_with_child(r"^interface (\S+)", r"^\s+shutdown$")
```

### Nodes

Each node has the following properties:

| Property | Description |
|----------|-------------|
| `text` | The line, trimmed (and without the `{`, `}`, `;` delimiters with the `braces` syntax). With the `indented` option, prefixed by the original indentation. |
| `raw` | The original line (or lines, for a multi-line node). |
| `line` | The line number in the configuration (starting at 1; 0 for the root node). |
| `indent` | The number of leading whitespace characters (-1 for the root node). |
| `parent` | The parent node. As with ciscoconfparse, a top-level line is its own parent; the root node's parent is `null`/`None`. |
| `children` | The child nodes. |
| `config` | The original text of all the descendant nodes, e.g. the content of an interface block. |
| `lines` | The original lines of all the descendant nodes. |
| `path` | The text of the ancestor nodes and of this node, e.g. `["router bgp 65001", "address-family ipv4", "neighbor 10.0.0.2 activate"]`. |
| `isComment` (JavaScript), `is_comment` (Python) | Whether the line is a comment. |
| `match` | On nodes returned by a query: the result of the regular expression match (JavaScript `RegExp` match array, or Python `re.Match`). |

### Queries

The queries are methods available on every node (including the root node). They match regular expressions (a JavaScript `RegExp` or string, a Python string or compiled pattern) against the `text` of the **direct children** of the node, or against all its descendants with the `recursive` option (`{ recursive: true }` in JavaScript, `recursive=True` in Python).

| JavaScript | Python | Returns |
|------------|--------|---------|
| `find(regex, options)` | `find(regex, recursive=False)` | The matching nodes, with their `match` property set. |
| `findWithChild(parentRegex, childRegex, options)` | `find_with_child(parent_regex, child_regex, recursive=False)` | The nodes matching `parentRegex` which have a direct child matching `childRegex`. |
| `findWithoutChild(parentRegex, childRegex, options)` | `find_without_child(parent_regex, child_regex, recursive=False)` | The nodes matching `parentRegex` which don't have any direct child matching `childRegex`. |
| `hasChild(regex, options)` | `has_child(regex, recursive=False)` | Whether at least one node matches. |
| `findValue(regex, options)` | `find_value(regex, recursive=False)` | The first capture group (or the whole match, without group) of the first matching node, or `null`/`None`. |
| `findValues(regex, options)` | `find_values(regex, recursive=False)` | The first capture group (or the whole match) of each matching node. |
| `filter(predicate, options)` | `filter(predicate, recursive=False)` | The nodes for which the `predicate` function returns true. |

As the node text is trimmed by default, a regular expression can be anchored with `^` regardless of the indentation, e.g. `/^shutdown$/` to match the ` shutdown` line of an interface. The comment lines are nodes too, so a broad regular expression (such as `.*`) also matches them; check `isComment`/`is_comment` to tell them apart.

### Settings

FortiOS-style configurations list the parameters of a block as `set <key> <values...>` lines. These methods read them from the **direct children** of a node (the lines of nested `config` blocks are not included), on any syntax:

| JavaScript | Python | Returns |
|------------|--------|---------|
| `settings()` | `settings()` | An object/dict mapping each key to the array/list of its values. |
| `setting(key)` | `setting(key)` | The value of the setting, multiple values being joined with a space, or `null`/`None` if not found. |
| `settingValues(key)` | `setting_values(key)` | The values of the setting, as an array/list (empty if not found). |

The values are split on whitespace and unquoted: double-quoted parts may contain spaces, line breaks (e.g. certificates) and `\`-escaped characters (`\"`, `\\`). Lines are read in order: a later `set` line overrides an earlier one with the same key, and an `unset <key>` line removes the key. Comment lines are ignored.

```
config system interface
    edit "port1"
        set ip 10.0.0.1 255.255.255.0
        set allowaccess ping https ssh
        set description "Uplink to \"core\""
        set role lan
    next
end
```

```javascript
const config = Toolbox.ConfigTree.parse(device.get("configuration"), { syntax: "fortios" });
const port1 = config.find(/^config system interface$/)[0].find(/^edit "port1"$/)[0];
port1.setting("role");               // "lan"
port1.setting("ip");                 // "10.0.0.1 255.255.255.0"
port1.setting("description");        // 'Uplink to "core"'
port1.setting("vdom");               // null
port1.settingValues("allowaccess");  // ["ping", "https", "ssh"]
port1.settings();                    // { ip: ["10.0.0.1", "255.255.255.0"], allowaccess: [...], ... }
```

### Examples

Find the interfaces which are not shut down and have no description (Cisco IOS):

```javascript
function check(device) {
  const config = Toolbox.ConfigTree.parse(device.get("runningConfig"));
  const missing = config
    .findWithoutChild(/^interface (\S+)/, /^shutdown$/)
    .filter((intf) => !intf.hasChild(/^description /))
    .map((intf) => intf.match[1]);
  if (missing.length > 0) {
    return { result: NONCONFORMING, comment: `No description on ${missing.join(", ")}` };
  }
  return CONFORMING;
}
```

```python
from netshot.toolbox import ConfigTree

def check(device):
    config = ConfigTree.parse(device.get("running_config"))
    missing = [intf.match.group(1)
               for intf in config.find_without_child(r"^interface (\S+)", r"^shutdown$")
               if not intf.has_child(r"^description ")]
    if missing:
        return {"result": result_option.NONCONFORMING, "comment": "No description on " + ", ".join(missing)}
    return result_option.CONFORMING
```

Check the BGP neighbors activated in the IPv4 address family (nested blocks):

```python
bgp = config.find(r"^router bgp (\d+)")[0]
for af in bgp.find(r"^address-family ipv4"):
    activated = af.find_values(r"^neighbor (\S+) activate$")
```

Read values from a Junos configuration:

```javascript
const config = Toolbox.ConfigTree.parse(device.get("configuration"), { syntax: "braces" });
const hostname = config.findValue(/^host-name (\S+)/, { recursive: true });
const disabledInterfaces = config
  .find(/^interfaces$/)[0]
  .findWithChild(/^(\S+)$/, /^disable$/)
  .map((intf) => intf.match[1]);
```

Find the FortiOS firewall policies without NAT:

```python
config = ConfigTree.parse(device.get("configuration"), syntax="fortios")
policies = config.find(r"^config firewall policy$")[0]
no_nat = [p.match.group(1) for p in policies.find(r"^edit (\d+)") if not p.has_child(r"^set nat enable$")]
```

Check the role and device identification of the FortiGate interfaces (VDOM mode, with a `config global` block):

```javascript
function check(device, debug) {
  const config = Toolbox.ConfigTree.parse(device.get("configuration"), { syntax: "fortios" });
  const problems = config.find(/^config global$/)
    .flatMap((global) => global.find(/^config system interface$/))
    .flatMap((block) => block.find(/^edit "(.*)"$/))
    .flatMap((intf) => {
      const name = intf.match[1];
      const issues = [];
      if (intf.setting("role") !== null) {
        issues.push(`${name} has a role`);
      }
      if (intf.setting("device-identification") === "enable") {
        issues.push(`${name} has device identification enabled`);
      }
      return issues;
    });
  if (problems.length > 0) {
    return { result: NONCONFORMING, comment: problems.join("\n") };
  }
  return CONFORMING;
}
```

!!! note "findSections is deprecated"
    The historical `device.findSections` (JavaScript compliance rules) and `cli.findSections` (drivers) functions still work, but are deprecated: use ConfigTree in new scripts.

## Migrating from ciscoconfparse

Python scripts written for [ciscoconfparse](https://github.com/mpenning/ciscoconfparse) (1.x) can use the compatible `CiscoConfParse` class of the toolbox, usually by just changing the import:

```python
# from ciscoconfparse import CiscoConfParse
from netshot.toolbox import CiscoConfParse

def check(device):
    parse = CiscoConfParse(device.get("running_config").splitlines())
    for intf in parse.find_objects_w_child(r"^interface", r"^\s+shutdown"):
        debug("Shut interface: " + intf.text)
    hostname = parse.re_match_iter_typed(r"^hostname\s+(\S+)")
    return result_option.CONFORMING
```

`CiscoConfParse(config, syntax="ios")` is the root node of a ConfigTree parsed with the `indented` option: as with ciscoconfparse, the lines hold their indentation in `text` (` shutdown`), the comment lines are kept (with `is_comment` set), and a top-level line is its own parent. The line objects are the ConfigTree nodes themselves, so the ConfigTree queries can be mixed with the ciscoconfparse methods. The configuration is passed as a string or as a list of lines, and the supported syntaxes are `ios` (default), `nxos`, `asa`, `iosxr`, `junos` and `fortios`.

The ciscoconfparse methods are also available on every ConfigTree node (in Python), with the same parameters (`exactmatch`, `ignore_ws`, `recurse`...); they are mainly meant for trees parsed with the `indented` option, since ciscoconfparse regular expressions usually expect the indentation (`r"^\s+shutdown"`):

* Parser methods, searching all the descendant lines of the node: `objs` (`ConfigObjs`), `ioscfg`, `find_objects`, `find_lines`, `has_line_with`, `find_children`, `find_all_children`, `find_objects_w_child` (`find_parent_objects`), `find_objects_wo_child` (`find_parent_objects_wo_child`), `find_parents_w_child`, `find_parents_wo_child`, `find_child_objects` (`find_objects_w_parents`), `find_children_w_parents`, `find_objects_w_all_children`, `find_objects_w_missing_children`, `find_lineage`, `re_search_children` and `re_match_iter_typed`.
* Line properties and methods: `linenum`, `all_children`, `all_parents`, `has_children`, `is_parent`, `is_child`, `is_comment`, `is_config_line`, `siblings`, `ioscfg`, `lineage`, `geneology`, `geneology_text`, `re_search`, `re_match`, `re_search_children`, `has_child_with`, `re_match_typed` and `re_match_iter_typed`.

Unlike the ConfigTree queries, the ciscoconfparse methods return the nodes themselves, without `match`.

Differences with ciscoconfparse:

* The parser is read-only: the configuration modification methods (`insert_after`, `replace_lines`, `delete`, `commit`...) and the diff methods are not available.
* The configuration is passed as a list of lines or as a string; file paths are not supported.
* The vendor-specific object factories (`factory=True`, e.g. `IOSIntfLine` with its interface properties) are not available.
* A multi-line banner is kept as a single object.
* With the `junos` syntax, the `#` and `/* ... */` comment lines have `is_comment` set (ciscoconfparse only flags `!` lines), and the line text keeps the original indentation.
