# Example: enforcing an IS-IS NET address scheme (script rule)

Let's assume you want to enforce the configured IS-IS NET address on your IOS and IOS-XR routers to derive from the name of the router.

Example:

| Router | NET address |
|--------|-------------|
| XR1    | 49.0001.0000.0000.0001.00 |
| XR2    | 49.0002.0000.0000.0002.00 |
| XR3    | 49.0003.0000.0000.0003.00 |
| RTR11  | 49.000b.0000.0000.000b.00 |
| RTR12  | 49.000c.0000.0000.000c.00 |

This is not a simple pattern match, so a script-based rule is required to achieve this result.

Here is how you can write it, using the [ConfigTree](../user-guide/script-toolbox.md) configuration parser to find the IS-IS block and its `net` line:

```javascript
/**
 * Check that the IS-IS NET address derives from the router name
 * (e.g. RTR11 -> 49.000b.0000.0000.000b.00).
 */
function check(device) {
    // The rule only applies to Cisco IOS (and IOS-XE/IOS-XR) devices
    if (!/^Cisco IOS/.test(device.get("type"))) {
        return NOTAPPLICABLE;
    }

    // Get the final number in the device name
    const id = device.get("name").match(/(\d+)\D*$/);
    if (id === null) {
        return {
            result: NOTAPPLICABLE,
            comment: "Does not apply to this hostname",
        };
    }
    // Transform the number into a NET address (4 hex digits)
    const hexId = parseInt(id[1], 10).toString(16).padStart(4, "0");
    const expectedNet = `49.${hexId}.0000.0000.${hexId}.00`;

    // Parse the config (configuration for IOS-XR, runningConfig otherwise)
    const config = Toolbox.ConfigTree.parse(device.get("configuration") || device.get("runningConfig"));
    // Find the IS-IS block
    const isis = config.find(/^router isis/);
    if (isis.length === 0) {
        return {
            result: NONCONFORMING,
            comment: "No IS-IS block",
        };
    }
    // Get the configured NET address
    const net = isis[0].findValue(/^net (\S+)/);
    if (net === null) {
        return {
            result: NONCONFORMING,
            comment: "No NET configured",
        };
    }
    // Compare the expected and the configured NET addresses
    if (net.toLowerCase() !== expectedNet) {
        return {
            result: NONCONFORMING,
            comment: `Configured NET ${net}, should be ${expectedNet}`,
        };
    }
    return CONFORMING;
}
```
