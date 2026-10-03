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

import site
import re

class result_option:
    NONCONFORMING = "NONCONFORMING"
    NOTAPPLICABLE = "NOTAPPLICABLE"
    CONFORMING = "CONFORMING"

UNDERSCORE_RE = re.compile(r"([^\-_\s])[\-_\s]+([^\-_\s])")


class Device:
    def __init__(self, _device_helper):
        self._device_helper = _device_helper

    def get(self, key, id = None):
        if not isinstance(key, str):
            raise TypeError("Invalid key type in get")
        if id is None:
            return self._device_helper.get(key)
        else:
            return self._device_helper.get(key, id)

    def set(self, key, value):
        if key != "comments":
            raise ValueError("Only 'comments' can be set on the device from a compliance rule")
        if not isinstance(value, str):
            raise TypeError("Invalid comments type in set")
        self._device_helper.set(key, value)

    def add(self, collection, value):
        if collection != "tag":
            raise ValueError("Only a 'tag' can be added to the device from a compliance rule")
        if not isinstance(value, str):
            raise TypeError("Invalid tag type in add")
        self._device_helper.add(collection, value)

    def remove(self, collection, value):
        if collection != "tag":
            raise ValueError("Only a 'tag' can be removed from the device in a compliance rule")
        if not isinstance(value, str):
            raise TypeError("Invalid tag type in remove")
        self._device_helper.remove(collection, value)

    def nslookup(self, host):
        if not isinstance(host, str):
            raise TypeError("Invalid host type in nslookup")
        record = self._device_helper.nslookup(host)
        return record


def _check(_device_helper):
    global debug
    def debug(message):
        nonlocal _device_helper
        if isinstance(message, str):
            _device_helper.debug(message)
    device = Device(_device_helper)

    result = check(device=device)
    if isinstance(result, str):
        return {'result': result, 'comment': ""}
    return result
