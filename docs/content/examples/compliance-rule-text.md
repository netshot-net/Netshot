# Example: enforcing IOS-XR router names

This is a basic example of the compliance module of Netshot, using a text-based rule.

Let's assume you want your IOS-XR devices to be named `xr0` to `xr9`. You can enforce this with a text rule.

1. Go to the Compliance section.
2. If you haven't created any policy yet, click _Create policy..._, give it a name, and choose the device group the policy will apply to.
3. Click on _+_ (to the right of the policy) to add the rule.
4. Give the rule a name, e.g. "XR device naming", select _Simple text rule_ and click _Add_.
5. Click _Edit content_ and fill in the rule definition, for example:
    * Device type: _Cisco IOS-XR_
    * Field: _Device name_
    * Text (regular expression): `^xr[0-9]$`
    * _The text must exist_

    You can test the rule against a selected device before saving.
6. Click _Save_.
7. Don't forget to enable the rule by clicking the wrench button and checking the _Enabled_ box.

Once enabled, any IOS-XR device whose name does not match a single digit after `xr` (e.g. `router1`, `xr10`) will be flagged as non-conforming.
