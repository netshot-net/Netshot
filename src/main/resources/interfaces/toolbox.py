# Copyright 2013-2025 Netshot
# 
# This file is part of Netshot project.
# 
# Netshot is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# Netshot is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with Netshot.  If not, see <http://www.gnu.org/licenses/>.

# Netshot script toolbox (Python implementation).
#
# Evaluated before the user's Python code (compliance rule, diagnostic), it
# registers the netshot.toolbox module, to be imported by the scripts:
#     from netshot.toolbox import ConfigTree
# The JavaScript implementation (toolbox.js, global Toolbox object) is mirrored
# here and both are checked against the same test corpus (ToolboxTest) - keep
# them in sync. The ciscoconfparse-compatible names (ConfigNode aliases and the
# CiscoConfParse class) are Python-only.
#
# Everything is defined within a function, so that nothing but the
# netshot.toolbox module leaks into the user script namespace.

def _netshot_register_toolbox():
    # Only builtin modules here: the file system filter may restrict the access
    # to the rest of the standard library
    import re
    import sys

    if "netshot.toolbox" in sys.modules:
        return

    _SYNTAXES = ("indent", "braces", "fortios")

    def _to_pattern(regex, function_name):
        if isinstance(regex, str):
            return re.compile(regex)
        if isinstance(regex, re.Pattern):
            return regex
        raise TypeError("Invalid regex parameter in {}".format(function_name))

    def _split_values(text):
        # Whitespace-separated values, with double-quoted parts (possibly with
        # \-escaped characters and line breaks) unquoted
        values = []
        value = None
        quoted = False
        i = 0
        while i < len(text):
            c = text[i]
            if not quoted and c.isspace():
                if value is not None:
                    values.append(value)
                    value = None
                i += 1
                continue
            if value is None:
                value = ""
            if c == "\"":
                quoted = not quoted
            elif c == "\\" and quoted and i + 1 < len(text):
                i += 1
                value += text[i]
            else:
                value += c
            i += 1
        if value is not None:
            values.append(value)
        return values

    _SETTING_PATTERN = re.compile(r"^\s*(set|unset)\s+(\S+)(?:\s+([\s\S]*))?$")

    def _parse_setting(text):
        # (key, unset, values) of a "set <key> <values...>" or "unset <key>" line, None if it's not one
        m = _SETTING_PATTERN.match(text)
        if m is None:
            return None
        return (m.group(2), m.group(1) == "unset", _split_values(m.group(3) or ""))

    def _leading_spaces(line):
        m = re.search(r"\S", line)
        return m.start() if m else -1

    class ConfigNode:
        """A node of a parsed configuration: one configuration line (or block
        opening line) along with its children."""

        def __init__(self, text, raw, line, indent, parent):
            # The line text, trimmed (and without the block delimiters for some syntaxes),
            # or with its original indentation when parsed with the indented option
            self.text = text
            # The original line(s), as found in the source text
            self.raw = raw
            # The (1-based) line number in the source text, 0 for the root node
            self.line = line
            # The number of leading whitespace characters, -1 for the root node
            self.indent = indent
            # The parent node: the node itself for a top-level line, None for the root node
            self.parent = parent
            # The child nodes
            self.children = []
            # The regular expression match, on nodes returned by queries
            self.match = None
            # The source line closing the block, if any (e.g. "}" or "next")
            self.closing = None
            # Whether this is a comment line
            self.is_comment = False
            # ciscoconfparse-compatible line number: 0-based index among the non-empty lines
            self.linenum = -1

        @property
        def lines(self):
            """The (raw) source lines of the child nodes, recursively."""
            lines = []
            def walk(node):
                for child in node.children:
                    lines.append(child.raw)
                    walk(child)
                    if child.closing is not None:
                        lines.append(child.closing)
            walk(self)
            return lines

        @property
        def config(self):
            """The (raw) source text of the child nodes, recursively."""
            return "\n".join(self.lines)

        @property
        def path(self):
            """The text of the ancestor nodes and of this node, from the top level (empty for the root node)."""
            path = []
            if self.parent is None:
                return path
            node = self
            while True:
                path.insert(0, node.text)
                if node.parent is node:
                    break
                node = node.parent
            return path

        def _with_match(self, match):
            result = ConfigNode.__new__(ConfigNode)
            result.__dict__.update(self.__dict__)
            result.match = match
            if self.parent is self:
                # A top-level line is its own parent
                result.parent = result
            return result

        def _candidates(self, recursive):
            if not recursive:
                return list(self.children)
            nodes = []
            def walk(node):
                for child in node.children:
                    nodes.append(child)
                    walk(child)
            walk(self)
            return nodes

        def find(self, regex, recursive=False):
            """Find the child nodes whose text matches the regex (re.search).
            Set recursive to True to look into all descendants.
            The returned nodes have the match attribute set."""
            pattern = _to_pattern(regex, "find")
            results = []
            for node in self._candidates(recursive):
                match = pattern.search(node.text)
                if match:
                    results.append(node._with_match(match))
            return results

        def has_child(self, regex, recursive=False):
            """Whether at least one child node matches the regex."""
            return len(self.find(_to_pattern(regex, "has_child"), recursive)) > 0

        def find_with_child(self, parent_regex, child_regex, recursive=False):
            """Find the child nodes matching parent_regex, which have a direct child matching child_regex.
            Set recursive to True to look for parents among all descendants."""
            child_pattern = _to_pattern(child_regex, "find_with_child")
            return [n for n in self.find(_to_pattern(parent_regex, "find_with_child"), recursive)
                    if n.has_child(child_pattern)]

        def find_without_child(self, parent_regex, child_regex, recursive=False):
            """Find the child nodes matching parent_regex, which have no direct child matching child_regex.
            Set recursive to True to look for parents among all descendants."""
            child_pattern = _to_pattern(child_regex, "find_without_child")
            return [n for n in self.find(_to_pattern(parent_regex, "find_without_child"), recursive)
                    if not n.has_child(child_pattern)]

        def find_values(self, regex, recursive=False):
            """Get the values (first capture group, or whole match if none) of the child nodes matching the regex."""
            return [(n.match.group(1) if n.match.re.groups > 0 else n.match.group(0))
                    for n in self.find(_to_pattern(regex, "find_values"), recursive)]

        def find_value(self, regex, recursive=False):
            """Get the value (first capture group, or whole match if none) of the first child node
            matching the regex, or None if none matches."""
            values = self.find_values(_to_pattern(regex, "find_value"), recursive)
            return values[0] if len(values) > 0 else None

        def filter(self, predicate, recursive=False):
            """Find the child nodes for which the predicate function returns True."""
            if not callable(predicate):
                raise TypeError("Invalid predicate function in filter")
            return [n for n in self._candidates(recursive) if predicate(n)]

        def settings(self):
            """Get the settings ("set <key> <values...>" direct child lines, FortiOS-style) of this node,
            as a key -> list of values dict. The values are unquoted, a later line overrides an earlier
            one with the same key, and "unset <key>" removes the key."""
            settings = {}
            for child in self.children:
                if child.is_comment:
                    continue
                setting = _parse_setting(child.text)
                if setting is None:
                    continue
                key, unset, values = setting
                if unset:
                    settings.pop(key, None)
                else:
                    settings[key] = values
            return settings

        def setting_values(self, key):
            """Get the values of a setting ("set <key> <values...>" direct child line), unquoted,
            as a list (empty if the setting is not found)."""
            return self.settings().get(key, [])

        def setting(self, key):
            """Get the value of a setting ("set <key> <values...>" direct child line), unquoted,
            multiple values being joined with a space, or None if the setting is not found."""
            values = self.settings().get(key)
            return None if values is None else " ".join(values)

        def __str__(self):
            return self.text

        def __repr__(self):
            return "<ConfigNode #{} {!r}>".format(self.line, self.text)

        def __lt__(self, other):
            return self.line < other.line

        def __gt__(self, other):
            return self.line > other.line

        # ciscoconfparse (1.x) compatible names. ciscoconfparse matches the regular
        # expressions against the indented text, so these are mainly meant for trees
        # parsed with the indented option (as CiscoConfParse does).
        # Unlike the ConfigTree queries, they return the nodes themselves (no match set).

        # Line object (IOSCfgLine) properties and methods

        @property
        def all_children(self):
            return sorted(self._candidates(True))

        @property
        def all_parents(self):
            result = []
            node = self
            while node.parent is not None and node.parent is not node:
                node = node.parent
                result.append(node)
            return sorted(result)

        @property
        def has_children(self):
            return len(self.children) > 0

        @property
        def is_parent(self):
            return self.has_children

        @property
        def is_child(self):
            return self.parent is not None and self.parent is not self

        @property
        def is_config_line(self):
            return len(self.text.strip()) > 0 and not self.is_comment

        @property
        def siblings(self):
            if self.parent is None:
                return []
            return [n for n in self.parent.children if n.indent == self.indent]

        @property
        def ioscfg(self):
            if self.parent is None:
                return [n.text for n in self.objs]
            return [self.text] + [n.text for n in self.all_children]

        @property
        def lineage(self):
            return sorted(self.all_parents + [self] + self.all_children)

        @property
        def geneology(self):
            return self.all_parents + [self]

        @property
        def geneology_text(self):
            return [n.text for n in self.geneology]

        def re_search(self, regex, default=""):
            if (isinstance(regex, str) and regex in self.text) or re.search(regex, self.text) is not None:
                return self.text
            return default

        def re_match(self, regex, group=1, default=""):
            m = re.search(regex, self.text)
            if m is not None:
                return m.group(group)
            return default

        def re_search_children(self, regex, recurse=False):
            return [n for n in self._candidates(recurse) if n.re_search(regex)]

        def has_child_with(self, linespec, all_children=False):
            return len(self.re_search_children(linespec, recurse=all_children)) > 0

        def re_match_typed(self, regex, group=1, untyped_default=False, result_type=str, default=""):
            m = re.search(regex, self.text)
            if m is not None and m.group(group) is not None:
                return result_type(m.group(group))
            return default if untyped_default else result_type(default)

        def re_match_iter_typed(self, regex, group=1, result_type=str, default="",
                                untyped_default=False, recurse=True):
            if self.parent is None:
                # On the whole configuration: the top-level lines only
                candidates = self.children
            else:
                m = re.search(regex, self.text)
                if m is not None:
                    return result_type(m.group(group))
                candidates = self._candidates(recurse)
            for n in candidates:
                m = re.search(regex, n.text)
                if m is not None:
                    return result_type(m.group(group))
            return default if untyped_default else result_type(default)

        # Parser object (CiscoConfParse) methods, searching all the descendant nodes

        @property
        def objs(self):
            return sorted(self._candidates(True))

        @property
        def ConfigObjs(self):
            return self.objs

        def find_objects(self, linespec, exactmatch=False, ignore_ws=False):
            pattern = re.compile(_ccp_spec(linespec, exactmatch, ignore_ws))
            return [n for n in self.objs if pattern.search(n.text)]

        def find_lines(self, linespec, exactmatch=False, ignore_ws=False):
            return [n.text for n in self.find_objects(linespec, exactmatch, ignore_ws)]

        def has_line_with(self, linespec):
            return len(self.find_objects(linespec)) > 0

        def find_children(self, linespec, exactmatch=False, ignore_ws=False):
            result = set()
            for parent in self.find_objects(linespec, exactmatch, ignore_ws):
                result.add(parent)
                result.update(parent.children)
            return [n.text for n in sorted(result)]

        def find_all_children(self, linespec, exactmatch=False, ignore_ws=False):
            result = set()
            for parent in self.find_objects(linespec, exactmatch, ignore_ws):
                result.add(parent)
                result.update(parent.all_children)
            return [n.text for n in sorted(result)]

        def find_objects_w_child(self, parentspec, childspec, ignore_ws=False, recurse=False):
            if ignore_ws:
                parentspec = _space_tolerant(parentspec)
                childspec = _space_tolerant(childspec)
            return [n for n in self.find_objects(parentspec) if n.re_search_children(childspec, recurse=recurse)]

        def find_parent_objects(self, parentspec, childspec, ignore_ws=False, recurse=False):
            return self.find_objects_w_child(parentspec, childspec, ignore_ws=ignore_ws, recurse=recurse)

        def find_parents_w_child(self, parentspec, childspec, ignore_ws=False):
            return [n.text for n in self.find_objects_w_child(parentspec, childspec, ignore_ws=ignore_ws)]

        def find_objects_wo_child(self, parentspec, childspec, ignore_ws=False, recurse=False):
            if ignore_ws:
                parentspec = _space_tolerant(parentspec)
                childspec = _space_tolerant(childspec)
            return [n for n in self.find_objects(parentspec) if not n.re_search_children(childspec, recurse=recurse)]

        def find_parent_objects_wo_child(self, parentspec, childspec, ignore_ws=False, recurse=False):
            return self.find_objects_wo_child(parentspec, childspec, ignore_ws=ignore_ws, recurse=recurse)

        def find_parents_wo_child(self, parentspec, childspec, ignore_ws=False):
            return [n.text for n in self.find_objects_wo_child(parentspec, childspec, ignore_ws=ignore_ws)]

        def find_child_objects(self, parentspec, childspec, ignore_ws=False, recurse=False):
            if ignore_ws:
                parentspec = _space_tolerant(parentspec)
                childspec = _space_tolerant(childspec)
            result = set()
            for parent in self.find_objects(parentspec):
                for child in parent._candidates(recurse):
                    if re.search(childspec, child.text):
                        result.add(child)
            return sorted(result)

        def find_objects_w_parents(self, parentspec, childspec, ignore_ws=False):
            return self.find_child_objects(parentspec, childspec, ignore_ws=ignore_ws)

        def find_children_w_parents(self, parentspec, childspec, ignore_ws=False):
            return [n.text for n in self.find_child_objects(parentspec, childspec, ignore_ws=ignore_ws)]

        def find_objects_w_all_children(self, parentspec, childspec, ignore_ws=False, recurse=False):
            if ignore_ws:
                parentspec = _space_tolerant(parentspec)
                childspec = [_space_tolerant(c) for c in childspec]
            return [n for n in self.find_objects(parentspec)
                    if all(n.re_search_children(c, recurse=recurse) for c in childspec)]

        def find_objects_w_missing_children(self, parentspec, childspec, ignore_ws=False):
            if ignore_ws:
                parentspec = _space_tolerant(parentspec)
                childspec = [_space_tolerant(c) for c in childspec]
            return [n for n in self.find_objects(parentspec)
                    if not all(n.re_search_children(c) for c in childspec)]

        def find_lineage(self, linespec, exactmatch=False):
            nodes = self.find_objects(linespec, exactmatch=exactmatch)
            if len(nodes) > 1:
                raise ValueError("linespec must be unique")
            return [n.text for n in nodes[0].lineage]

    def _space_tolerant(regex):
        return re.sub(r"\s+", r"\\s+", regex)

    def _ccp_spec(linespec, exactmatch, ignore_ws):
        if ignore_ws:
            linespec = _space_tolerant(linespec)
        if exactmatch:
            linespec = "^%s$" % linespec
        return linespec

    def _split_lines(text):
        return re.split(r"\r\n|\r|\n", text)

    def _new_root():
        return ConfigNode("", "", 0, -1, None)

    def _parse_indent(text):
        # Indentation-based configuration (Cisco IOS-like): a line is a child of the
        # previous less indented line. Blank lines are skipped, comment lines (starting
        # with !) are flagged, and multi-line banners are kept as a single node.
        root = _new_root()
        stack = [root]
        lines = _split_lines(text)
        l = 0
        while l < len(lines):
            raw = lines[l]
            indent = _leading_spaces(raw)
            if indent == -1:
                l += 1
                continue
            while stack[-1].indent >= indent:
                stack.pop()
            parent = stack[-1]
            node = ConfigNode(raw.strip(), raw, l + 1, indent, parent)
            node.is_comment = raw[indent] == "!"
            parent.children.append(node)
            stack.append(node)

            banner = re.match(r"^banner\s+\S+(?:\s+(\^C|\S)(.*))?$", node.text)
            if banner:
                # Multi-line banner: consume the lines up to the end delimiter
                # (or up to a EOF line, for Arista-like banners without delimiter)
                delimiter = banner.group(1)
                is_closed = (delimiter in banner.group(2)) if delimiter else False
                if not is_closed:
                    body_lines = []
                    while l + 1 < len(lines):
                        l += 1
                        body_lines.append(lines[l])
                        if (delimiter in lines[l]) if delimiter else (lines[l].strip() == "EOF"):
                            break
                    node.raw = "\n".join([raw] + body_lines)
                    node.text = "\n".join([node.text] + body_lines)
            l += 1
        return root

    def _parse_braces(text):
        # Curly brace-based configuration (Juniper Junos, Palo Alto PAN-OS).
        # The block delimiters and statement final semicolons are removed from node text.
        # Comments (# lines and C-style comments, kept as a single node) are flagged.
        root = _new_root()
        current = root
        lines = _split_lines(text)
        l = -1
        while l + 1 < len(lines):
            l += 1
            raw = lines[l]
            content = raw.strip()
            if content == "":
                continue
            indent = _leading_spaces(raw)
            if content.startswith("/*") or content.startswith("#"):
                start = l
                if content.startswith("/*"):
                    # Multi-line comment: consume the lines up to the end delimiter
                    while "*/" not in raw and l + 1 < len(lines):
                        l += 1
                        raw += "\n" + lines[l]
                        content += "\n" + lines[l]
                node = ConfigNode(content, raw, start + 1, indent, current)
                node.is_comment = True
                current.children.append(node)
                continue
            if content == "}":
                if current.parent is not None:
                    current.closing = raw
                    current = current.parent
                continue
            if content.endswith("{"):
                node = ConfigNode(content[:-1].strip(), raw, l + 1, indent, current)
                current.children.append(node)
                current = node
                continue
            if content.endswith(";"):
                content = content[:-1].strip()
            current.children.append(ConfigNode(content, raw, l + 1, indent, current))
        return root

    def _count_quotes(s):
        return re.sub(r"\\.", "", s).count('"')

    def _parse_fortios(text):
        # FortiOS-like configuration: "config" blocks end with "end",
        # "edit" blocks end with "next". # lines are flagged as comments,
        # and multi-line quoted values are kept in a single node.
        root = _new_root()
        stack = [root]
        lines = _split_lines(text)

        def close_block(opener, raw):
            # Close up to the nearest block opened by the given keyword
            for s in range(len(stack) - 1, 0, -1):
                if stack[s].text.split()[0] == opener:
                    stack[s].closing = raw
                    del stack[s:]
                    return

        l = 0
        while l < len(lines):
            raw = lines[l]
            content = raw.strip()
            start = l
            l += 1
            if content == "":
                continue
            indent = _leading_spaces(raw)
            if content.startswith("#"):
                node = ConfigNode(content, raw, start + 1, indent, stack[-1])
                node.is_comment = True
                stack[-1].children.append(node)
                continue
            if content == "end":
                close_block("config", raw)
                continue
            if content == "next":
                close_block("edit", raw)
                continue
            node_text = content
            if _count_quotes(content) % 2 == 1:
                # Quoted value spanning multiple lines (e.g. certificate)
                while l < len(lines):
                    raw += "\n" + lines[l]
                    node_text += "\n" + lines[l]
                    l += 1
                    if _count_quotes(lines[l - 1]) % 2 == 1:
                        break
            parent = stack[-1]
            node = ConfigNode(node_text, raw, start + 1, indent, parent)
            parent.children.append(node)
            if re.match(r"^(config|edit)\s", content):
                stack.append(node)
        return root

    _PARSERS = {
        "indent": _parse_indent,
        "braces": _parse_braces,
        "fortios": _parse_fortios,
    }

    class ConfigTree:
        """Configuration parser."""

        # The supported syntaxes
        SYNTAXES = _SYNTAXES

        @staticmethod
        def parse(text, syntax="indent", indented=False):
            """Parse a configuration text into a tree.
            syntax is one of "indent" (default), "braces" or "fortios".
            Set indented to True to keep the original indentation in the node text
            (and match it in the queries), as ciscoconfparse does.
            Returns the root node, whose children are the top-level configuration lines."""
            if not isinstance(text, str):
                raise TypeError("Invalid text string in ConfigTree.parse")
            parser = _PARSERS.get(syntax)
            if parser is None:
                raise ValueError("Invalid syntax '{}' in ConfigTree.parse (expected one of {})".format(
                    syntax, ", ".join(_SYNTAXES)))
            return _finish_tree(parser(text), text, indented)

    def _finish_tree(root, text, indented):
        # Line numbers as ciscoconfparse counts them: index among the non-empty lines
        linenums = []
        count = 0
        for line in _split_lines(text):
            linenums.append(count)
            if line != "":
                count += 1

        def walk(node):
            for child in node.children:
                child.linenum = linenums[child.line - 1]
                if indented:
                    child.text = child.raw[:child.indent] + child.text
                walk(child)

        walk(root)
        for child in root.children:
            # A top-level line is its own parent
            child.parent = child
        return root

    # ciscoconfparse (1.x) compatibility

    _CCP_SYNTAXES = {
        "ios": "indent",
        "nxos": "indent",
        "asa": "indent",
        "iosxr": "indent",
        "junos": "braces",
        "fortios": "fortios",
    }

    class CiscoConfParse(ConfigNode):
        """ciscoconfparse-compatible (read-only) configuration parser: the root node of
        a ConfigTree parsed with the indented option.
        config is the configuration, as a string or a list of lines.
        syntax is one of ios (default), nxos, asa, iosxr, junos or fortios."""

        def __init__(self, config=None, syntax="ios", **kwargs):
            if isinstance(config, (list, tuple)):
                config = "\n".join(config)
            if not isinstance(config, str):
                raise TypeError("Invalid config in CiscoConfParse (should be a string or a list of lines)")
            tree_syntax = _CCP_SYNTAXES.get(syntax)
            if tree_syntax is None:
                raise ValueError("Unsupported syntax '{}' in CiscoConfParse (expected one of {})".format(
                    syntax, ", ".join(_CCP_SYNTAXES)))
            ConfigNode.__init__(self, "", "", 0, -1, None)
            self.syntax = syntax
            # The top-level nodes are their own parent, so they can be adopted as they are
            self.children = ConfigTree.parse(config, syntax=tree_syntax, indented=True).children

        def __repr__(self):
            return "<CiscoConfParse: {} lines / syntax: {}>".format(len(self.objs), self.syntax)

    package = sys.modules.get("netshot")
    if package is None:
        package = type(sys)("netshot")
        package.__path__ = []
        sys.modules["netshot"] = package
    module = type(sys)("netshot.toolbox")
    module.__doc__ = "Netshot script toolbox."
    module.ConfigTree = ConfigTree
    module.ConfigNode = ConfigNode
    module.CiscoConfParse = CiscoConfParse
    module.IOSCfgLine = ConfigNode
    module.__all__ = ["ConfigTree", "ConfigNode", "CiscoConfParse", "IOSCfgLine"]
    package.toolbox = module
    sys.modules["netshot.toolbox"] = module

_netshot_register_toolbox()
del _netshot_register_toolbox
