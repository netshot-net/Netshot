// Generated from openapi.json by `npm run api:types`: do not edit.

export interface paths {
    "/api-tokens": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the API tokens
         * @description Lists the API tokens (never their values), in their brief view by default.
         */
        get: operations["listApiTokens"];
        put?: never;
        /**
         * Create an API token
         * @description Creates an API token, with a random value or the one given (32 alphanumeric characters). The value is in the answer, and ONLY in that answer: Netshot only keeps a hash of it, so it can't be read afterwards.
         */
        post: operations["createApiToken"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api-tokens/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get an API token
         * @description Gets an API token, without its value.
         */
        get: operations["getApiToken"];
        put?: never;
        post?: never;
        /**
         * Delete an API token
         * @description Deletes an API token, which can't authenticate anymore.
         */
        delete: operations["deleteApiToken"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the current user
         * @description Gets the principal the request is authenticated as: kind USER for the user of a session or of a JWT access token, API_TOKEN for an API token (which has no user name), with the authentication method, the permission level and its role.
         */
        get: operations["getMe"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Change own password
         * @description Changes the password of the current user, who must be a local user (not an API token, nor a user authenticated by an identity provider). The current password must be given, and the new one must comply with the password policy (400 otherwise).
         */
        patch: operations["changePassword"];
        trace?: never;
    };
    "/auth/session": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the current session
         * @description Gets the session identified by the session cookie: its user, when it was created and its idle timeout. Answers 401 if the request is not authenticated, and 404 if it is authenticated otherwise than by a session (API token, JWT access token): use GET /auth/me for the principal of any request.
         */
        get: operations["getSession"];
        put?: never;
        /**
         * Log in
         * @description Logs in with a username and a password, or an OIDC authorization code with its redirect URI, and creates a session: the session cookie is set in the response, and the previous session of the client (if any) is dropped. Answers 201 with the session. A wrong user name, password or code is a 401. An expired password is a 403 (give a newPassword to change it while logging in); a new password which does not comply with the password policy is a 400. Useless when using API tokens.
         */
        post: operations["login"];
        /**
         * Log out
         * @description Terminates the session identified by the session cookie. Always answers 204, even without a session (the operation is idempotent). For a user who logged in through an identity provider (not a local user), when OIDC is available, the X-OIDC-EndSessionEndpoint header gives the IdP endpoint the client can send the user to, to end the session there too.
         */
        delete: operations["logout"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance-results/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Count the compliance results
         * @description Counts the compliance results matching the filters of the list, by the values of up to two fields (groupBy: result, policy, rule, device, group, domain) and/or by time bucket (interval: hour, day, week or month, on checkDate, from/to, timeZone). A result is counted once per group of its device.
         */
        get: operations["getResultStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance-results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the compliance results
         * @description Lists the results of the last compliance checks, one per device and rule (a rule never checked on a device has no result here), with the exemption of the device from the rule. Filters: device, deviceName, domain and group (of the device), rule, ruleName, policy, policyName, result, checkDate, exemptionExpirationDate; q searches the device, rule and policy names.
         */
        get: operations["listResults"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/compliance-results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the compliance results of a device
         * @description Lists the rules of the policies applying to a device (through its groups) with the result of their last check (none if never checked) and the exemption of the device. (unlike the list across devices, which only has the checked ones). Filters: rule, ruleName, policy, policyName, result, checkDate, exemptionExpirationDate.
         */
        get: operations["listResults_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/hardware-rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the hardware rules
         * @description Lists the hardware compliance rules, in their brief view by default.
         */
        get: operations["listHardwareRules"];
        put?: never;
        /** Create a hardware rule */
        post: operations["createHardwareRule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/hardware-rules/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a hardware rule */
        get: operations["getHardwareRule"];
        /**
         * Replace a hardware rule
         * @description Replaces the definition of a hardware rule. With If-Match, the rule must still be at the given version (ETag).
         */
        put: operations["replaceHardwareRule"];
        post?: never;
        /**
         * Delete a hardware rule
         * @description Deletes a hardware rule. With If-Match, the rule must still be at the given version (ETag).
         */
        delete: operations["deleteHardwareRule"];
        options?: never;
        head?: never;
        /**
         * Update a hardware rule
         * @description Changes some properties of a hardware rule (JSON merge patch, RFC 7396). With If-Match, the rule must still be at the given version (ETag).
         */
        patch: operations["updateHardwareRule"];
        trace?: never;
    };
    "/compliance/policies": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the compliance policies
         * @description Lists the compliance policies, in their brief view by default. The targetGroup filter gives the policies applying to a group.
         */
        get: operations["listPolicies"];
        put?: never;
        /**
         * Create a compliance policy
         * @description Creates a compliance policy, without rules.
         */
        post: operations["createPolicy"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/policies/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a compliance policy */
        get: operations["getPolicy"];
        /**
         * Replace a compliance policy
         * @description Replaces the name and the target groups of a policy. The compliance results of the devices of a group removed from the policy are deleted. With If-Match, the policy must still be at the given version (ETag).
         */
        put: operations["replacePolicy"];
        post?: never;
        /**
         * Delete a compliance policy
         * @description Deletes a policy, with its rules, their exemptions and results. With If-Match, the policy must still be at the given version (ETag).
         */
        delete: operations["deletePolicy"];
        options?: never;
        head?: never;
        /**
         * Update a compliance policy
         * @description Changes some properties of a policy (JSON merge patch, RFC 7396). With If-Match, the policy must still be at the given version (ETag).
         */
        patch: operations["updatePolicy"];
        trace?: never;
    };
    "/compliance/policies/{id}/rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the rules of a policy
         * @description Lists the rules of a policy, as the rule list filtered on the policy (same parameters).
         */
        get: operations["listPolicyRules"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the compliance rules
         * @description Lists the compliance rules of all the policies, in their brief view by default (the type, TEXT, JAVASCRIPT or PYTHON, tells which other properties a rule has).
         */
        get: operations["listRules"];
        put?: never;
        /**
         * Create a compliance rule
         * @description Creates a rule in a policy (required). A script rule without script gets the template script; the rule is disabled unless enabled is true.
         */
        post: operations["createRule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/rules/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a compliance rule */
        get: operations["getRule"];
        /**
         * Replace a compliance rule
         * @description Replaces the definition of a rule (its type and its policy can't change; its exemptions are unchanged). With If-Match, the rule must still be at the given version (ETag).
         */
        put: operations["replaceRule"];
        post?: never;
        /**
         * Delete a compliance rule
         * @description Deletes a rule, with its exemptions and results. With If-Match, the rule must still be at the given version (ETag).
         */
        delete: operations["deleteRule"];
        options?: never;
        head?: never;
        /**
         * Update a compliance rule
         * @description Changes some properties of a rule (JSON merge patch, RFC 7396). With If-Match, the rule must still be at the given version (ETag).
         */
        patch: operations["updateRule"];
        trace?: never;
    };
    "/compliance/rules/{id}/exemptions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the exemptions of a rule
         * @description Lists the devices exempted from a rule, with the date their exemption ends. Filters: device, expirationDate.
         */
        get: operations["listExemptions"];
        /**
         * Replace the exemptions of a rule
         * @description Replaces the whole set of devices exempted from a rule (an empty list removes them all), and answers the new set. The exemptions are part of the rule: the version of the rule is incremented; with If-Match, the rule must still be at the given version (ETag).
         */
        put: operations["replaceExemptions"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/rules/test": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Test a compliance rule
         * @description Checks a rule definition (which isn't stored) against a device, without any side effect, and answers the result, with what the check logged. A device without configuration gives a NOTAPPLICABLE result.
         */
        post: operations["testRule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/software-rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the software rules
         * @description Lists the software compliance rules, in the order they are checked in by default (priority is the position of the rule, from 1).
         */
        get: operations["listSoftwareRules"];
        put?: never;
        /**
         * Create a software rule
         * @description Creates a software rule, at the given priority (position) or as the last one.
         */
        post: operations["createSoftwareRule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/compliance/software-rules/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a software rule */
        get: operations["getSoftwareRule"];
        /**
         * Replace a software rule
         * @description Replaces the definition of a software rule. A priority moves the rule to that position (the other rules shift); without priority the rule keeps its position. With If-Match, the rule must still be at the given version (ETag).
         */
        put: operations["replaceSoftwareRule"];
        post?: never;
        /**
         * Delete a software rule
         * @description Deletes a software rule (the rules after it move up). With If-Match, the rule must still be at the given version (ETag).
         */
        delete: operations["deleteSoftwareRule"];
        options?: never;
        head?: never;
        /**
         * Update a software rule
         * @description Changes some properties of a software rule (JSON merge patch, RFC 7396), possibly its priority (position). With If-Match, the rule must still be at the given version (ETag).
         */
        patch: operations["updateSoftwareRule"];
        trace?: never;
    };
    "/configs/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a configuration
         * @description Gets a configuration with its attributes. The long texts and files are not inlined: the attributes give the url to download them.
         */
        get: operations["getConfig"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/configs/{id}/diff": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Compare a configuration with another one
         * @description Compares the configuration (the revised one) with another one (the original one, given by with, by default the one gathered just before it on the same device, or an empty configuration if it is the first). The configurations must be of the same type of device. The configurations are written in their brief view unless expanded (expand=original,revised).
         */
        get: operations["getConfigDiff"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/configs/{id}/items/{item}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Download an item of a configuration
         * @description Downloads a long text item (text/plain, UTF-8) or a file item (application/octet-stream) of a configuration, as an attachment. 404 if the configuration has no such item, or not a downloadable one (a short value), or if it has no content.
         */
        get: operations["getConfigItem"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/configs/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Count the configurations
         * @description Counts the configurations (the configuration changes) matching the filters of the configuration list, by device and/or author (groupBy) and/or by time bucket (interval: hour, day, week or month, on changeDate, from/to, timeZone).
         */
        get: operations["getConfigStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/configs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the configurations
         * @description Lists the configurations of all the devices, in their brief view by default (device, date, author), the latest first. The attributes are only there in the full view (or with fields=attributes), and never hold the long texts and files: download them from their url. The filters on domain and group apply to the device of the configuration.
         */
        get: operations["listConfigs"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/configs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the configurations of a device
         * @description Lists the configurations of a device, as the configuration list filtered on the device (same parameters), the latest first.
         */
        get: operations["listDeviceConfigs"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/credential-sets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the credential sets
         * @description Lists the global credential sets, in their brief view by default. The credential sets specific to a device access are left out, unless the deviceSpecific filter is given (deviceSpecific=true lists only those, deviceSpecific=false only the global ones). The secrets are never returned.
         */
        get: operations["listCredentialSets"];
        put?: never;
        /**
         * Create a credential set
         * @description Creates a global credential set. The secrets the type needs must be given, by value or as a location in a Vault instance.
         */
        post: operations["createCredentialSet"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/credential-sets/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a credential set
         * @description Gets a credential set. The secrets are never returned, only whether they are set.
         */
        get: operations["getCredentialSet"];
        /**
         * Replace a credential set
         * @description Replaces the definition of a global credential set (its type can't change). A username, secret or Vault location left out is unchanged; a secret set to null is cleared (which the secrets the type needs refuse). With If-Match, the credential set must still be at the given version (ETag).
         */
        put: operations["replaceCredentialSet"];
        post?: never;
        /**
         * Delete a credential set
         * @description Deletes a global credential set, which must not be used by any device or task. With If-Match, the credential set must still be at the given version (ETag).
         */
        delete: operations["deleteCredentialSet"];
        options?: never;
        head?: never;
        /**
         * Update a credential set
         * @description Changes some properties of a global credential set (JSON merge patch, RFC 7396). The usernames and secrets are unchanged unless the patch gives them (or a Vault location replacing them). With If-Match, the credential set must still be at the given version (ETag).
         */
        patch: operations["updateCredentialSet"];
        trace?: never;
    };
    "/device-families": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the device families
         * @description Lists the distinct device families (driver and family) of the devices, with their number of devices. An aggregate: items have no id nor url.
         */
        get: operations["listDeviceFamilies"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/interfaces": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the interfaces of a device
         * @description Lists the network interfaces of a device, in their brief view by default (name, description, enabled; view=full adds the addresses). Besides the filters on the fields, ipAddress (exact or net lookup) and macAddress filter on the addresses.
         */
        get: operations["listDeviceInterfaces"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/modules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the modules of a device
         * @description Lists the hardware modules of a device, in their brief view by default (slot, part number, serial number): the current ones only, unless removed=true, which lists the modules removed from the device (kept for the history) too, flagged removed.
         */
        get: operations["listDeviceModules"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the devices
         * @description Lists the devices, in their brief view by default. Besides the filters on the fields, the query parameter takes a device search query (such as [Family] is "Cisco IOS" and [Location] contains "Paris"), and group, tag and compliant filter on the group membership, the tags and the configuration compliance. Like any list, it can be exported as CSV (Accept: text/csv) or Excel (Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet). With the Excel format, the include parameter writes a workbook with more sheets for the matching devices.
         */
        get: operations["listDevices"];
        put?: never;
        /**
         * Add a device
         * @description Adds a device, asynchronously: answers 202 with the task doing the work, whose location is in the Location header. Without a driver, a task discovers the type of the device and the device is only created if the discovery succeeds (an SNMP community is needed). With a driver, the device is created immediately and a task takes its first snapshot.
         */
        post: operations["addDevice"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/compliance-check": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Check the compliance of a device
         * @description Creates a task which checks the configuration compliance of the device (as soon as possible by default). Answers 202 with the task, whose location is in the Location header.
         */
        post: operations["checkCompliance"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a device */
        get: operations["getDevice"];
        /**
         * Replace a device
         * @description Replaces the editable properties of a device (status, management address and domain, comments, tags, options, accesses). The other properties are read only and ignored. The secrets of the credential sets specific to the device which are left out are kept. With If-Match, the device must still be at the given version (ETag).
         */
        put: operations["replaceDevice"];
        post?: never;
        /**
         * Delete a device
         * @description Deletes a device, with its configurations and results. With If-Match, the device must still be at the given version (ETag).
         */
        delete: operations["deleteDevice"];
        options?: never;
        head?: never;
        /**
         * Update a device
         * @description Changes some editable properties of a device (JSON merge patch, RFC 7396; a list such as the accesses is replaced as a whole). With If-Match, the device must still be at the given version (ETag).
         */
        patch: operations["updateDevice"];
        trace?: never;
    };
    "/devices/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Count the devices
         * @description Counts the devices matching the filters of the device list (and the query), by the values of up to two fields (groupBy: status, mgmtDomain, family, driver, networkClass, softwareVersion, softwareLevel, location, eolDate, eosDate, group, tag, compliant) and/or by time bucket (interval: hour, day, week or month, on createdDate, changeDate, eolDate or eosDate, from/to, timeZone).
         */
        get: operations["getDeviceStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/diagnostics-run": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Run the diagnostics of a device
         * @description Creates a task which runs the diagnostics of the device (as soon as possible by default). Answers 202 with the task, whose location is in the Location header.
         */
        post: operations["runDiagnostics"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/script-runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Run a script on a device
         * @description Creates a task which runs a script on the device (as soon as possible by default): either a saved script (savedScript), or the text of a script with the driver it is written for (script and deviceDriver). Answers 202 with the task, whose location is in the Location header.
         */
        post: operations["runScript"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshot": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Take a snapshot of a device
         * @description Creates a task which takes a snapshot of the device (as soon as possible by default). Answers 202 with the task, whose location is in the Location header.
         */
        post: operations["takeSnapshot"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-tags": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the device tags
         * @description Lists the distinct tags attached to at least one device, with their number of devices. An aggregate: items have no id nor url.
         */
        get: operations["listDeviceTags"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-types": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the device types
         * @description Lists the device types: the device drivers loaded by the server, in their brief view by default (name, description, version). View full adds the attributes, options, accesses... Items have no id nor url: the name is what the devices refer to.
         */
        get: operations["listDeviceTypes"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-types/reload": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reload the device drivers
         * @description Reloads the device drivers from their sources (the driver files), on this server and the other members of the cluster. A driver which can't be loaded is left out (see the logs).
         */
        post: operations["reloadDeviceTypes"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/part-numbers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the part numbers
         * @description Lists the distinct part numbers of the modules, with their number of modules. An aggregate: items have no id nor url.
         */
        get: operations["listPartNumbers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/software-versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the software versions
         * @description Lists the distinct (non empty) software versions of the devices, with their number of devices. An aggregate: items have no id nor url.
         */
        get: operations["listSoftwareVersions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/diagnostics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the diagnostics
         * @description Lists the diagnostics, in their brief view by default.
         */
        get: operations["listDiagnostics"];
        put?: never;
        /**
         * Create a diagnostic
         * @description Creates a diagnostic: a CLI command (SIMPLE), or a JavaScript or Python script.
         */
        post: operations["createDiagnostic"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/diagnostics/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a diagnostic */
        get: operations["getDiagnostic"];
        /**
         * Replace a diagnostic
         * @description Replaces the definition of a diagnostic (its type can't change). The results of the diagnostic are deleted when its target group changes. With If-Match, the diagnostic must still be at the given version (ETag).
         */
        put: operations["replaceDiagnostic"];
        post?: never;
        /**
         * Delete a diagnostic
         * @description Deletes a diagnostic, with its results. With If-Match, the diagnostic must still be at the given version (ETag).
         */
        delete: operations["deleteDiagnostic"];
        options?: never;
        head?: never;
        /**
         * Update a diagnostic
         * @description Changes some properties of a diagnostic (JSON merge patch, RFC 7396). With If-Match, the diagnostic must still be at the given version (ETag).
         */
        patch: operations["updateDiagnostic"];
        trace?: never;
    };
    "/devices/{id}/diagnostic-results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the diagnostic results of a device
         * @description Lists the last result of each diagnostic run on a device, with the diagnostic as a reference.
         */
        get: operations["listDiagnosticResults"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/domains": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the domains
         * @description Lists the device domains, in their brief view by default.
         */
        get: operations["listDomains"];
        put?: never;
        /** Create a domain */
        post: operations["createDomain"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/domains/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a domain */
        get: operations["getDomain"];
        /**
         * Replace a domain
         * @description Replaces the definition of a domain. With If-Match, the domain must still be at the given version (ETag).
         */
        put: operations["replaceDomain"];
        post?: never;
        /**
         * Delete a domain
         * @description Deletes a domain, which must not be used by any device or credential set. With If-Match, the domain must still be at the given version (ETag).
         */
        delete: operations["deleteDomain"];
        options?: never;
        head?: never;
        /**
         * Update a domain
         * @description Changes some properties of a domain (JSON merge patch, RFC 7396). With If-Match, the domain must still be at the given version (ETag).
         */
        patch: operations["updateDomain"];
        trace?: never;
    };
    "/groups": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the groups
         * @description Lists the device groups, in their brief view by default. The device filter gives the groups a device is a member of.
         */
        get: operations["listGroups"];
        put?: never;
        /**
         * Create a group
         * @description Creates a device group: a static group of the given devices, or a dynamic group of the devices matching the query.
         */
        post: operations["createGroup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/groups/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a group */
        get: operations["getGroup"];
        /**
         * Replace a group
         * @description Replaces the definition of a group (its type can't change; the devices of a static group are unchanged unless given). With If-Match, the group must still be at the given version (ETag).
         */
        put: operations["replaceGroup"];
        post?: never;
        /**
         * Delete a group
         * @description Deletes a group (not its devices). With If-Match, the group must still be at the given version (ETag).
         */
        delete: operations["deleteGroup"];
        options?: never;
        head?: never;
        /**
         * Update a group
         * @description Changes some properties of a group (JSON merge patch, RFC 7396). With If-Match, the group must still be at the given version (ETag).
         */
        patch: operations["updateGroup"];
        trace?: never;
    };
    "/groups/{id}/devices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the devices of a group
         * @description Lists the devices of a group, as the device list filtered on the group (same parameters).
         */
        get: operations["listGroupDevices"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/hooks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the hooks
         * @description Lists the hooks, in their brief view by default.
         */
        get: operations["listHooks"];
        put?: never;
        /**
         * Create a hook
         * @description Creates a hook: Netshot runs its action when one of its triggers occurs (a Web hook posts the event to a URL).
         */
        post: operations["createHook"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/hooks/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a hook */
        get: operations["getHook"];
        /**
         * Replace a hook
         * @description Replaces the definition of a hook (its type can't change). With If-Match, the hook must still be at the given version (ETag).
         */
        put: operations["replaceHook"];
        post?: never;
        /**
         * Delete a hook
         * @description Deletes a hook. With If-Match, the hook must still be at the given version (ETag).
         */
        delete: operations["deleteHook"];
        options?: never;
        head?: never;
        /**
         * Update a hook
         * @description Changes some properties of a hook (JSON merge patch, RFC 7396; the triggers are replaced as a whole). With If-Match, the hook must still be at the given version (ETag).
         */
        patch: operations["updateHook"];
        trace?: never;
    };
    "/scripts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the scripts
         * @description Lists the command scripts, in their brief view by default (without the code). The user inputs are only extracted (by evaluating the scripts) when they are asked for.
         */
        get: operations["listScripts"];
        put?: never;
        /**
         * Create a script
         * @description Validates and creates a command script, the author being the user.
         */
        post: operations["createScript"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/scripts/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a script */
        get: operations["getScript"];
        /**
         * Replace a script
         * @description Validates and replaces the definition of a command script (the author is kept). With If-Match, the script must still be at the given version (ETag).
         */
        put: operations["replaceScript"];
        post?: never;
        /**
         * Delete a script
         * @description Deletes a command script. With If-Match, the script must still be at the given version (ETag).
         */
        delete: operations["deleteScript"];
        options?: never;
        head?: never;
        /**
         * Update a script
         * @description Changes some properties of a command script (JSON merge patch, RFC 7396), validated as on creation (the author is kept). With If-Match, the script must still be at the given version (ETag).
         */
        patch: operations["updateScript"];
        trace?: never;
    };
    "/scripts/validation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Validate a script
         * @description Checks the driver, the name and the code of a script definition (by evaluating it) without saving it: 200 with the script and its user inputs, 400 with the problem otherwise.
         */
        post: operations["validateScript"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Probe the health of the server
         * @description Liveness and readiness probe, for load balancers and orchestrators. Not authenticated, and it gives nothing but the status (no version, no detail): 200 with status UP when all the services of the server are running and healthy, 503 with status DOWN otherwise (while starting or stopping, or when a service fails). The services are detailed by GET /system/services.
         */
        get: operations["getHealth"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/system/cluster/master": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Check whether the server is the cluster master
         * @description Gets the mastership status of the server answering, to let a load balancer send the requests to the master only. Not authenticated. Answers 200 when the server is the master (or clustering is not enabled) and 503 when it is not.
         */
        get: operations["getClusterMaster"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/system/info": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the server information
         * @description Gets the version of Netshot, the time of the server and the idle timeout of the user sessions.
         */
        get: operations["getInfo"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/system/cluster/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the cluster members
         * @description Lists the members of the high availability cluster (none if clustering is not enabled), in a single page.
         */
        get: operations["listClusterMembers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/system/services": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the services of the server
         * @description Lists the services of the server (task manager, Syslog server, web server...) in start order, with their state, whether they are healthy and their statistics, in a single page.
         */
        get: operations["listServices"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the tasks of a device
         * @description Lists the tasks targeting a device, as the task list filtered on the device (same parameters).
         */
        get: operations["listDeviceTasks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel a task
         * @description Cancels a scheduled task, or requests the cancellation of a running group task or of a device task still waiting for its device (cancelRequested). Refused (409) in any other status.
         */
        post: operations["cancelTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the tasks
         * @description Lists the tasks, in their brief view by default, the latest first.
         */
        get: operations["listTasks"];
        put?: never;
        /**
         * Create a task
         * @description Creates a task and schedules it (as soon as possible by default). Answers 202 with the task, whose location is in the Location header. Running scripts needs the execute-read-write role, scanning subnets the read-write role, and purging the database the admin role.
         */
        post: operations["createTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a task */
        get: operations["getTask"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{id}/debug-log": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the debug log of a task
         * @description Gets the full debug log of a task run with debug enabled.
         */
        get: operations["getTaskDebugLog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Count the tasks
         * @description Counts the tasks matching the filters of the task list, by the values of up to two fields (groupBy: type, status, author, scheduleType, device, group, runnerId) and/or by time bucket (interval: hour, day, week or month, on executionDate (default), creationDate, changeDate or scheduleReference, from/to, timeZone). Without execution date, a task isn't counted by executionDate.
         */
        get: operations["getTaskStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/status-counts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the number of tasks by status
         * @description Returns the number of tasks in each status (0 included), and the number of task threads.
         */
        get: operations["getTaskStatusCounts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the users
         * @description Lists the Netshot users, in their brief view by default.
         */
        get: operations["listUsers"];
        put?: never;
        /**
         * Create a user
         * @description Creates a local user (the password is required, and must comply with the password policy) or a remote one (no password: it authenticates through RADIUS, TACACS+ or OIDC, and the user only gives it its permission level).
         */
        post: operations["createUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a user */
        get: operations["getUser"];
        /**
         * Replace a user
         * @description Replaces the definition of a user. An absent password is unchanged (it is required to make a remote user a local one). A user can't edit itself (403). With If-Match, the user must still be at the given version (ETag).
         */
        put: operations["replaceUser"];
        post?: never;
        /**
         * Delete a user
         * @description Deletes a user. A user can't delete itself (403). With If-Match, the user must still be at the given version (ETag).
         */
        delete: operations["deleteUser"];
        options?: never;
        head?: never;
        /**
         * Update a user
         * @description Changes some properties of a user (JSON merge patch, RFC 7396). The password is changed by giving one. A user can't edit itself (403). With If-Match, the user must still be at the given version (ETag).
         */
        patch: operations["updateUser"];
        trace?: never;
    };
    "/vault-instances": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the Vault instances
         * @description Lists the Vault instances, in their brief view by default. The secrets are never returned.
         */
        get: operations["listVaultInstances"];
        put?: never;
        /**
         * Create a Vault instance
         * @description Creates a Vault instance. The secrets required by the authentication method must be given.
         */
        post: operations["createVaultInstance"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/vault-instances/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a Vault instance
         * @description Gets a Vault instance. The secrets are never returned, only whether they are set.
         */
        get: operations["getVaultInstance"];
        /**
         * Replace a Vault instance
         * @description Replaces the definition of a Vault instance. A secret left out is unchanged; a secret set to null is cleared (which an authentication method needing it refuses). With If-Match, the Vault instance must still be at the given version (ETag).
         */
        put: operations["replaceVaultInstance"];
        post?: never;
        /**
         * Delete a Vault instance
         * @description Deletes a Vault instance, which must not be referenced by any credential field. With If-Match, the Vault instance must still be at the given version (ETag).
         */
        delete: operations["deleteVaultInstance"];
        options?: never;
        head?: never;
        /**
         * Update a Vault instance
         * @description Changes some properties of a Vault instance (JSON merge patch, RFC 7396). The secrets are unchanged unless the patch gives them. With If-Match, the Vault instance must still be at the given version (ETag).
         */
        patch: operations["updateVaultInstance"];
        trace?: never;
    };
    "/vault-instances/{id}/test": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Test a Vault instance
         * @description Tries to log in to a saved Vault instance, without changing anything. A failed login is a successful answer with success=false and the reason.
         */
        post: operations["testVaultInstance"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/vault-instances/test": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Test a Vault instance definition
         * @description Tries to log in with a Vault instance definition which isn't saved (it must be complete, secrets included). A failed login is a successful answer with success=false and the reason.
         */
        post: operations["testVaultInstanceDefinition"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description A newly created API token, with its value (only given once) */
        ApiTokenCreated: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            /** Format: date-time */
            readonly changeDate: Date | null;
            description: string | null;
            /** Format: int32 */
            level: number;
            readonly role: string | null;
            /** Format: password */
            readonly token: string | null;
        };
        /** @description Definition of an API token */
        ApiTokenRequest: {
            description?: string | null;
            /** Format: int32 */
            level: number;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            token?: string | null;
        };
        /** @description API token (the value of the token is not part of it) */
        ApiToken: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            /** Format: date-time */
            readonly changeDate: Date | null;
            description: string | null;
            /** Format: int32 */
            level: number;
            readonly role: string | null;
        };
        ListResponseApiToken: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["ApiToken"][] | null;
        };
        /** @description The principal (user or API token) a request is authenticated as */
        CurrentUser: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            readonly kind: string | null;
            readonly authMethod: string | null;
            readonly username: string | null;
            readonly local: boolean | null;
            /** Format: int32 */
            readonly level: number;
            readonly role: string | null;
        };
        /** @description Change of the password of the current user */
        PasswordChangeRequest: {
            /** Format: password */
            currentPassword: string;
            /** Format: password */
            newPassword: string;
        };
        /** @description A user session (cookie-authenticated) */
        Session: {
            readonly url: string | null;
            /** Format: date-time */
            readonly creationDate: Date | null;
            /** Format: int32 */
            readonly maxIdleTime: number;
            user: components["schemas"]["CurrentUser"] | null;
        };
        /** @description Credentials to log in: username and password, or OIDC authorization code */
        SessionRequest: {
            username?: string | null;
            /** Format: password */
            password?: string | null;
            /** Format: password */
            newPassword?: string | null;
            /** Format: password */
            authorizationCode?: string | null;
            redirectUri?: string | null;
        };
        /** @description Counts of objects, by field values and/or by time bucket */
        Stats: {
            /** Format: int64 */
            count: number | null;
            groupBy: string[] | null;
            interval: string | null;
            on: string | null;
            /** Format: date-time */
            from: Date | null;
            /** Format: date-time */
            to: Date | null;
            timeZone: string | null;
            results: {
                [key: string]: unknown;
            }[] | null;
        };
        /** @description Device credential set (secrets are never returned) */
        CredentialSet: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            /** Format: int64 */
            readonly version: number;
            /** Format: date-time */
            readonly changeDate: Date | null;
            name: string | null;
            /** @enum {string|null} */
            type: "SSH" | "SSH_KEY" | "TELNET" | "HTTP" | "SNMP_V1" | "SNMP_V2C" | "SNMP_V3" | null;
            mgmtDomain: components["schemas"]["Domain"] | null;
            readonly deviceSpecific: boolean;
            username: string | null;
            usernameVault: components["schemas"]["VaultSecret"] | null;
            readonly hasPassword: boolean | null;
            passwordVault: components["schemas"]["VaultSecret"] | null;
            readonly hasSuperPassword: boolean | null;
            superPasswordVault: components["schemas"]["VaultSecret"] | null;
            readonly hasPrivateKey: boolean | null;
            privateKeyVault: components["schemas"]["VaultSecret"] | null;
            readonly hasCommunity: boolean | null;
            communityVault: components["schemas"]["VaultSecret"] | null;
            /** @enum {string|null} */
            authType: "NONE" | "MD5" | "SHA" | "HMAC128SHA224" | "HMAC192SHA256" | "HMAC256SHA384" | "HMAC384SHA512" | null;
            readonly hasAuthKey: boolean | null;
            authKeyVault: components["schemas"]["VaultSecret"] | null;
            /** @enum {string|null} */
            privType: "NONE" | "DES" | "DES3" | "AES128" | "AES192" | "AES256" | null;
            readonly hasPrivKey: boolean | null;
            privKeyVault: components["schemas"]["VaultSecret"] | null;
        };
        /** @description Device (network or security equipment) */
        Device: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number | null;
            /** Format: date-time */
            changeDate: Date | null;
            /** Format: date-time */
            createdDate: Date | null;
            creator: string | null;
            name: string | null;
            mgmtAddress: string | null;
            /** @enum {string|null} */
            status: "DISABLED" | "INPRODUCTION" | "PREPRODUCTION" | null;
            mgmtDomain: components["schemas"]["Domain"] | null;
            family: string | null;
            driver: string | null;
            driverDescription: string | null;
            /** @enum {string|null} */
            networkClass: "FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER" | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY" | null;
            softwareVersion: string | null;
            /** @enum {string|null} */
            softwareLevel: "GOLD" | "SILVER" | "BRONZE" | "UNKNOWN" | null;
            serialNumber: string | null;
            location: string | null;
            contact: string | null;
            comments: string | null;
            cachedIpAddress: string | null;
            compliant: boolean | null;
            endOfLife: boolean | null;
            endOfSale: boolean | null;
            /** Format: date-time */
            eolDate: Date | null;
            eolModule: components["schemas"]["Module"] | null;
            /** Format: date-time */
            eosDate: Date | null;
            eosModule: components["schemas"]["Module"] | null;
            /** Format: date-time */
            lastSnapshotSuccessDate: Date | null;
            /** Format: date-time */
            lastSnapshotFailureDate: Date | null;
            tags: string[] | null;
            virtualDevices: string[] | null;
            vrfInstances: string[] | null;
            options: {
                [key: string]: unknown;
            } | null;
            attributes: components["schemas"]["DeviceAttribute"][] | null;
            accesses: components["schemas"]["DeviceAccess"][] | null;
            ownerGroups: components["schemas"]["Group"][] | null;
        };
        /** @description Per-access settings of a device */
        DeviceAccess: {
            accessName: string | null;
            address: string | null;
            /** Format: int32 */
            port: number | null;
            globalCredentialSet: components["schemas"]["CredentialSet"] | null;
            specificCredentialSet: components["schemas"]["CredentialSet"] | null;
            /** @enum {string|null} */
            sshHostKeyVerification: "TRUST_ANY" | "TRUST_KNOWN" | null;
            sshTrustedHostKeys: string | null;
            /** Format: date-time */
            sshTrustedHostKeysSince: Date | null;
            /** @enum {string|null} */
            httpsCaTrustMode: "TRUST_ANY" | "SYSTEM_TRUSTSTORE" | "CUSTOM_CA" | null;
            httpsCustomCaCertificate: string | null;
        };
        /** @description Device attribute collected by the driver */
        DeviceAttribute: {
            name: string | null;
            type: string | null;
            value: unknown;
        };
        /** @description Compliance state of a device for a rule */
        DeviceComplianceResult: {
            device: components["schemas"]["Device"] | null;
            rule: Omit<components["schemas"]["Rule"], "type"> | null;
            policy: components["schemas"]["Policy"] | null;
            /** @enum {string|null} */
            result: "CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE" | null;
            comment: string | null;
            /** Format: date-time */
            checkDate: Date | null;
            /** Format: date-time */
            exemptionExpirationDate: Date | null;
        };
        /** @description Device domain */
        Domain: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            /** Format: int64 */
            readonly version: number;
            /** Format: date-time */
            readonly changeDate: Date | null;
            name: string | null;
            description: string | null;
            server4Address: string | null;
            readonly server6Address: string | null;
        };
        /** @description Device group */
        Group: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            name: string | null;
            folder: string | null;
            type: string | null;
            hiddenFromReports: boolean;
            query: string | null;
        };
        /** @description Compliance rule checked by a JavaScript script */
        JavaScriptRule: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "JAVASCRIPT";
            name: string | null;
            enabled: boolean;
            policy: components["schemas"]["Policy"] | null;
            script: string | null;
        };
        ListResponseDeviceComplianceResult: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["DeviceComplianceResult"][] | null;
        };
        /** @description Device module */
        Module: {
            /** Format: int64 */
            id: number;
            slot: string | null;
            partNumber: string | null;
            serialNumber: string | null;
            /** Format: date-time */
            firstSeenDate: Date | null;
            /** Format: date-time */
            lastSeenDate: Date | null;
            removed: boolean;
        };
        /** @description Compliance policy */
        Policy: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            name: string | null;
            targetGroups: components["schemas"]["Group"][] | null;
        };
        /** @description Compliance rule checked by a Python script */
        PythonRule: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "PYTHON";
            name: string | null;
            enabled: boolean;
            policy: components["schemas"]["Policy"] | null;
            script: string | null;
        };
        /** @description Compliance rule */
        Rule: {
            type: string;
        } & (components["schemas"]["TextRule"] | components["schemas"]["JavaScriptRule"] | components["schemas"]["PythonRule"]);
        /** @description Compliance rule matching a text */
        TextRule: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "TEXT";
            name: string | null;
            enabled: boolean;
            policy: components["schemas"]["Policy"] | null;
            field: string | null;
            context: string | null;
            text: string | null;
            regExp: boolean;
            invert: boolean;
            anyBlock: boolean;
            matchAll: boolean;
            normalize: boolean;
            deviceDriver: string | null;
        };
        /** @description Vault instance, an external secret store (secrets are never returned) */
        VaultInstance: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            /** Format: int64 */
            readonly version: number;
            /** Format: date-time */
            readonly changeDate: Date | null;
            name: string | null;
            /** @enum {string|null} */
            type: "HASHICORP_KV2" | null;
            baseUrl: string | null;
            namespace: string | null;
            /** @enum {string|null} */
            httpsCaTrustMode: "TRUST_ANY" | "SYSTEM_TRUSTSTORE" | "CUSTOM_CA" | null;
            httpsCustomCaCertificate: string | null;
            kvMountPath: string | null;
            /** @enum {string|null} */
            authMethod: "APPROLE" | "JWT" | null;
            appRoleMountPath: string | null;
            appRoleId: string | null;
            readonly hasAppRoleSecretId: boolean;
            jwtMountPath: string | null;
            jwtIdpTokenEndpoint: string | null;
            jwtClientId: string | null;
            readonly hasJwtClientSecret: boolean;
            jwtVaultRole: string | null;
            jwtScope: string | null;
        };
        /** @description Location of a secret in a Vault instance */
        VaultSecret: {
            vaultInstance: components["schemas"]["VaultInstance"] | null;
            path: string | null;
        };
        /** @description Hardware compliance rule */
        HardwareRule: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            partNumber: string | null;
            /** Format: date-time */
            endOfSale: Date | null;
            /** Format: date-time */
            endOfLife: Date | null;
            group: components["schemas"]["Group"] | null;
            driver: string | null;
            family: string | null;
            familyRegExp: boolean;
            partNumberRegExp: boolean;
        };
        /** @description Definition of a hardware compliance rule */
        HardwareRuleRequest: {
            partNumber?: string | null;
            /** Format: date-time */
            endOfSale?: Date | null;
            /** Format: date-time */
            endOfLife?: Date | null;
            /** Format: int64 */
            group?: number | null;
            driver?: string | null;
            family?: string | null;
            familyRegExp?: boolean | null;
            partNumberRegExp?: boolean | null;
        };
        ListResponseHardwareRule: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["HardwareRule"][] | null;
        };
        JsonNode: Record<string, never>;
        /** @description Definition of a compliance policy */
        PolicyRequest: {
            name: string;
            targetGroups?: number[] | null;
        };
        ListResponsePolicy: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Policy"][] | null;
        };
        ListResponseRule: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Rule"][] | null;
        };
        /** @description Definition of a JavaScript compliance rule */
        JavaScriptRuleRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "JAVASCRIPT";
            name?: string | null;
            enabled?: boolean | null;
            /** Format: int64 */
            policy?: number | null;
            script?: string | null;
        };
        /** @description Definition of a Python compliance rule */
        PythonRuleRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "PYTHON";
            name?: string | null;
            enabled?: boolean | null;
            /** Format: int64 */
            policy?: number | null;
            script?: string | null;
        };
        /** @description Definition of a compliance rule */
        RuleRequest: {
            type: string;
        } & (components["schemas"]["TextRuleRequest"] | components["schemas"]["JavaScriptRuleRequest"] | components["schemas"]["PythonRuleRequest"]);
        /** @description Definition of a text compliance rule */
        TextRuleRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "TEXT";
            name?: string | null;
            enabled?: boolean | null;
            /** Format: int64 */
            policy?: number | null;
            field?: string | null;
            context?: string | null;
            text?: string | null;
            regExp?: boolean | null;
            invert?: boolean | null;
            anyBlock?: boolean | null;
            matchAll?: boolean | null;
            normalize?: boolean | null;
            deviceDriver?: string | null;
        };
        /** @description Exemption of a device from a compliance rule */
        Exemption: {
            device: components["schemas"]["Device"] | null;
            /** Format: date-time */
            expirationDate: Date | null;
        };
        ListResponseExemption: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Exemption"][] | null;
        };
        /** @description Exemption of a device from a compliance rule */
        ExemptionRequest: {
            /** Format: int64 */
            device: number;
            /** Format: date-time */
            expirationDate: Date;
        };
        /** @description Set of exemptions of a compliance rule */
        ExemptionsRequest: {
            exemptions: components["schemas"]["ExemptionRequest"][];
        };
        /** @description Result of the test of a compliance rule */
        RuleTestResult: {
            /** @enum {string|null} */
            result: "CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE" | null;
            log: string | null;
            comment: string | null;
        };
        /** @description Test of a compliance rule definition against a device */
        RuleTestRequest: {
            rule: components["schemas"]["RuleRequest"];
            /** Format: int64 */
            device: number;
        };
        /** @description Software compliance rule */
        SoftwareRule: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            /** Format: int32 */
            priority: number;
            /** @enum {string|null} */
            level: "GOLD" | "SILVER" | "BRONZE" | "UNKNOWN" | null;
            group: components["schemas"]["Group"] | null;
            driver: string | null;
            family: string | null;
            familyRegExp: boolean;
            softwareVersion: string | null;
            softwareVersionRegExp: boolean;
            partNumber: string | null;
            partNumberRegExp: boolean;
        };
        /** @description Definition of a software compliance rule */
        SoftwareRuleRequest: {
            /** Format: int32 */
            priority?: number | null;
            /** @enum {string} */
            level: "GOLD" | "SILVER" | "BRONZE" | "UNKNOWN";
            /** Format: int64 */
            group?: number | null;
            driver?: string | null;
            family?: string | null;
            familyRegExp?: boolean | null;
            softwareVersion?: string | null;
            softwareVersionRegExp?: boolean | null;
            partNumber?: string | null;
            partNumberRegExp?: boolean | null;
        };
        ListResponseSoftwareRule: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["SoftwareRule"][] | null;
        };
        Binary: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "BINARY";
            name: string | null;
            assumption: boolean | null;
        };
        BinaryFile: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "BINARYFILE";
            name: string | null;
            originalName: string | null;
            /** Format: int64 */
            fileSize: number;
            checksum: string | null;
            url: string | null;
        };
        /** @description Device configuration */
        Config: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number | null;
            device: components["schemas"]["Device"] | null;
            /** Format: date-time */
            changeDate: Date | null;
            author: string | null;
            customHash: string | null;
            attributes: components["schemas"]["ConfigAttribute"][] | null;
        };
        /** @description Attribute of a configuration */
        ConfigAttribute: {
            type: string;
        } & (components["schemas"]["Numeric"] | components["schemas"]["Text"] | components["schemas"]["LongText"] | components["schemas"]["Binary"] | components["schemas"]["BinaryFile"]);
        LongText: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "LONGTEXT";
            name: string | null;
            url: string | null;
        };
        Numeric: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "NUMERIC";
            name: string | null;
            /** Format: double */
            number: number | null;
        };
        Text: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "TEXT";
            name: string | null;
            text: string | null;
        };
        /** @description Differences between two configurations */
        ConfigDiff: {
            original: components["schemas"]["Config"] | null;
            revised: components["schemas"]["Config"] | null;
            /** Format: date-time */
            originalDate: Date | null;
            /** Format: date-time */
            revisedDate: Date | null;
            deltas: {
                [key: string]: components["schemas"]["Delta"][];
            } | null;
        };
        Delta: {
            /** @enum {string|null} */
            type: "CHANGE" | "DELETE" | "INSERT" | null;
            /** Format: int32 */
            originalPosition: number;
            /** Format: int32 */
            revisedPosition: number;
            originalLines: string[] | null;
            revisedLines: string[] | null;
            preContext: string[] | null;
            postContext: string[] | null;
            hierarchy: components["schemas"]["LineWithPosition"][] | null;
        };
        LineWithPosition: {
            line: string | null;
            /** Format: int32 */
            position: number;
        };
        ListResponseConfig: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Config"][] | null;
        };
        /** @description Definition of a global credential set */
        CredentialSetRequest: {
            name: string;
            /** @enum {string} */
            type: "SSH" | "SSH_KEY" | "TELNET" | "HTTP" | "SNMP_V1" | "SNMP_V2C" | "SNMP_V3";
            /** Format: int64 */
            mgmtDomain?: number | null;
            username?: string | null;
            usernameVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            password?: string | null;
            passwordVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            superPassword?: string | null;
            superPasswordVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            privateKey?: string | null;
            privateKeyVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            community?: string | null;
            communityVault?: components["schemas"]["VaultSecretRequest"] | null;
            /** @enum {string|null} */
            authType?: "NONE" | "MD5" | "SHA" | "HMAC128SHA224" | "HMAC192SHA256" | "HMAC256SHA384" | "HMAC384SHA512" | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            authKey?: string | null;
            authKeyVault?: components["schemas"]["VaultSecretRequest"] | null;
            /** @enum {string|null} */
            privType?: "NONE" | "DES" | "DES3" | "AES128" | "AES192" | "AES256" | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            privKey?: string | null;
            privKeyVault?: components["schemas"]["VaultSecretRequest"] | null;
        };
        /** @description Location of a secret in a Vault instance */
        VaultSecretRequest: {
            /** Format: int64 */
            vaultInstance: number;
            path: string;
        };
        ListResponseCredentialSet: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["CredentialSet"][] | null;
        };
        /** @description Device family (driver and family) with its number of devices */
        DeviceFamily: {
            driver: string | null;
            family: string | null;
            /** Format: int64 */
            deviceCount: number;
        };
        ListResponseDeviceFamily: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["DeviceFamily"][] | null;
        };
        /** @description Network interface of a device */
        DeviceInterface: {
            /** Format: int64 */
            id: number;
            display: string | null;
            name: string | null;
            description: string | null;
            enabled: boolean;
            level3: boolean;
            vrf: string | null;
            virtualDevice: string | null;
            macAddress: string | null;
            ipAddresses: components["schemas"]["IpAddress"][] | null;
        };
        IpAddress: {
            ip: string | null;
            /** Format: int32 */
            prefixLength: number;
            /** Format: int32 */
            version: number;
            /** @enum {string|null} */
            usage: "PRIMARY" | "SECONDARY" | "VRRP" | "HSRP" | "SECONDARYVRRP" | "SECONDARYHSRP" | null;
        };
        ListResponseDeviceInterface: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["DeviceInterface"][] | null;
        };
        /** @description Hardware module of a device */
        DeviceModule: {
            /** Format: int64 */
            id: number;
            display: string | null;
            slot: string | null;
            partNumber: string | null;
            serialNumber: string | null;
            /** Format: date-time */
            firstSeenDate: Date | null;
            /** Format: date-time */
            lastSeenDate: Date | null;
            removed: boolean;
        };
        ListResponseDeviceModule: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["DeviceModule"][] | null;
        };
        /** @description Task */
        Task: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number | null;
            type: string | null;
            /** @enum {string|null} */
            status: "CANCELLED" | "FAILURE" | "NEW" | "RUNNING" | "SCHEDULED" | "SUCCESS" | "WAITING" | "DELAYED" | null;
            description: string | null;
            target: string | null;
            author: string | null;
            comments: string | null;
            device: components["schemas"]["Device"] | null;
            group: components["schemas"]["Group"] | null;
            parentTask: components["schemas"]["Task"] | null;
            /** Format: int32 */
            childOrder: number | null;
            /** Format: int32 */
            priority: number | null;
            /** @enum {string|null} */
            scheduleType: "ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY" | null;
            /** Format: int32 */
            scheduleFactor: number | null;
            /** Format: date-time */
            scheduleReference: Date | null;
            /** Format: date-time */
            nextExecutionDate: Date | null;
            repeating: boolean | null;
            /** @enum {string|null} */
            scheduleMode: "PARALLEL" | "SEQUENTIAL" | null;
            stopOnFailure: boolean | null;
            /** Format: date-time */
            creationDate: Date | null;
            /** Format: date-time */
            changeDate: Date | null;
            /** Format: date-time */
            executionDate: Date | null;
            runnerId: string | null;
            debugEnabled: boolean | null;
            cancelRequested: boolean | null;
            log: string | null;
            /** @description Parameters of the type of task */
            parameters: {
                [key: string]: unknown;
            } | null;
        };
        /** @description Settings of one access of a device */
        DeviceAccessRequest: {
            accessName: string;
            address?: string | null;
            /** Format: int32 */
            port?: number | null;
            /** Format: int64 */
            globalCredentialSet?: number | null;
            specificCredentialSet?: components["schemas"]["DeviceCredentialSetRequest"] | null;
            /** @enum {string|null} */
            sshHostKeyVerification?: "TRUST_ANY" | "TRUST_KNOWN" | null;
            sshTrustedHostKeys?: string | null;
            /** @enum {string|null} */
            httpsCaTrustMode?: "TRUST_ANY" | "SYSTEM_TRUSTSTORE" | "CUSTOM_CA" | null;
            httpsCustomCaCertificate?: string | null;
        };
        /** @description Device to add */
        DeviceCreationRequest: {
            mgmtAddress: string;
            /** Format: int64 */
            mgmtDomain: number;
            /** @description Name of the device driver; if absent, the device type is auto discovered */
            driver?: string | null;
            comments?: string | null;
            tags?: string[] | null;
            options?: {
                [key: string]: unknown;
            } | null;
            accesses?: components["schemas"]["DeviceAccessRequest"][] | null;
        };
        /** @description Credential set specific to one access of a device */
        DeviceCredentialSetRequest: {
            /** @enum {string} */
            type: "SSH" | "SSH_KEY" | "TELNET" | "HTTP" | "SNMP_V1" | "SNMP_V2C" | "SNMP_V3";
            username?: string | null;
            usernameVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            password?: string | null;
            passwordVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            superPassword?: string | null;
            superPasswordVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            privateKey?: string | null;
            privateKeyVault?: components["schemas"]["VaultSecretRequest"] | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            community?: string | null;
            communityVault?: components["schemas"]["VaultSecretRequest"] | null;
            /** @enum {string|null} */
            authType?: "NONE" | "MD5" | "SHA" | "HMAC128SHA224" | "HMAC192SHA256" | "HMAC256SHA384" | "HMAC384SHA512" | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            authKey?: string | null;
            authKeyVault?: components["schemas"]["VaultSecretRequest"] | null;
            /** @enum {string|null} */
            privType?: "NONE" | "DES" | "DES3" | "AES128" | "AES192" | "AES256" | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            privKey?: string | null;
            privKeyVault?: components["schemas"]["VaultSecretRequest"] | null;
        };
        /** @description Options of a compliance check of a device */
        ComplianceCheckRequest: {
            comments?: string | null;
            /** @enum {string|null} */
            scheduleType?: "ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY" | null;
            /** Format: date-time */
            scheduleReference?: Date | null;
            /** Format: int32 */
            scheduleFactor?: number | null;
            /** Format: int32 */
            priority?: number | null;
            debugEnabled?: boolean | null;
        };
        ListResponseDevice: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Device"][] | null;
        };
        /** @description Editable properties of a device */
        DeviceRequest: {
            mgmtAddress: string;
            /** Format: int64 */
            mgmtDomain: number;
            /** @enum {string|null} */
            status?: "DISABLED" | "INPRODUCTION" | "PREPRODUCTION" | null;
            comments?: string | null;
            tags?: string[] | null;
            options?: {
                [key: string]: unknown;
            } | null;
            accesses?: components["schemas"]["DeviceAccessRequest"][] | null;
        };
        /** @description Options of a run of the diagnostics of a device */
        DiagnosticsRunRequest: {
            dontCheckCompliance?: boolean | null;
            comments?: string | null;
            /** @enum {string|null} */
            scheduleType?: "ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY" | null;
            /** Format: date-time */
            scheduleReference?: Date | null;
            /** Format: int32 */
            scheduleFactor?: number | null;
            /** Format: int32 */
            priority?: number | null;
            debugEnabled?: boolean | null;
        };
        /** @description Script to run on a device */
        ScriptRunRequest: {
            /** Format: int64 */
            savedScript?: number | null;
            script?: string | null;
            deviceDriver?: string | null;
            userInputValues?: {
                [key: string]: string;
            } | null;
            runSnapshot?: boolean | null;
            runDiagnostics?: boolean | null;
            checkCompliance?: boolean | null;
            comments?: string | null;
            /** @enum {string|null} */
            scheduleType?: "ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY" | null;
            /** Format: date-time */
            scheduleReference?: Date | null;
            /** Format: int32 */
            scheduleFactor?: number | null;
            /** Format: int32 */
            priority?: number | null;
            debugEnabled?: boolean | null;
        };
        /** @description Options of a snapshot of a device */
        SnapshotRequest: {
            dontRunDiagnostics?: boolean | null;
            dontCheckCompliance?: boolean | null;
            comments?: string | null;
            /** @enum {string|null} */
            scheduleType?: "ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY" | null;
            /** Format: date-time */
            scheduleReference?: Date | null;
            /** Format: int32 */
            scheduleFactor?: number | null;
            /** Format: int32 */
            priority?: number | null;
            debugEnabled?: boolean | null;
        };
        /** @description Device tag with its number of devices */
        DeviceTag: {
            name: string | null;
            /** Format: int64 */
            deviceCount: number;
        };
        ListResponseDeviceTag: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["DeviceTag"][] | null;
        };
        Access: {
            name: string | null;
            /** @enum {string|null} */
            protocol: "TELNET" | "SSH" | "SNMPV1" | "SNMPV2C" | "SNMPV3" | "HTTP" | "HTTPS" | null;
            description: string | null;
            /** Format: int32 */
            defaultPort: number;
            group: string | null;
            /** Format: int32 */
            priority: number;
        };
        Attribute: {
            name: string | null;
            title: string | null;
            /** @enum {string|null} */
            type: "NUMERIC" | "TEXT" | "LONGTEXT" | "DATE" | "BINARY" | "BINARYFILE" | "ENUM" | null;
            /** @enum {string|null} */
            level: "DEVICE" | "CONFIG" | null;
            comparable: boolean;
            searchable: boolean;
            checkable: boolean;
        };
        /** @description Device type (device driver loaded by the server) */
        DeviceType: {
            name: string | null;
            description: string | null;
            version: string | null;
            author: string | null;
            /** Format: int32 */
            priority: number;
            protocols: ("TELNET" | "SSH" | "SNMPV1" | "SNMPV2C" | "SNMPV3" | "HTTP" | "HTTPS")[] | null;
            cliMainModes: string[] | null;
            sourceHash: string | null;
            location: components["schemas"]["Location"] | null;
            attributes: components["schemas"]["Attribute"][] | null;
            options: components["schemas"]["Option"][] | null;
            accesses: components["schemas"]["Access"][] | null;
        };
        ListResponseDeviceType: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["DeviceType"][] | null;
        };
        Location: {
            /** @enum {string|null} */
            type: "EMBEDDED" | "FILE" | null;
            fileName: string | null;
        };
        Option: {
            name: string | null;
            title: string | null;
            /** @enum {string|null} */
            type: "TEXT" | "BOOLEAN" | null;
            choices: string[] | null;
            defaultValue: unknown;
        };
        ListResponsePartNumber: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["PartNumber"][] | null;
        };
        /** @description Part number of modules with its number of modules */
        PartNumber: {
            partNumber: string | null;
            /** Format: int64 */
            moduleCount: number;
        };
        ListResponseSoftwareVersion: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["SoftwareVersion"][] | null;
        };
        /** @description Software version with its number of devices */
        SoftwareVersion: {
            version: string | null;
            /** Format: int64 */
            deviceCount: number;
        };
        /** @description Diagnostic (data collected from devices) */
        Diagnostic: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            name: string | null;
            type: string | null;
            resultType: string | null;
            enabled: boolean;
            targetGroup: components["schemas"]["Group"] | null;
            script: string | null;
            deviceDriver: string | null;
            deviceDriverDescription: string | null;
            cliMode: string | null;
            command: string | null;
            modifierPattern: string | null;
            modifierReplacement: string | null;
        };
        /** @description Definition of a diagnostic */
        DiagnosticRequest: {
            name: string;
            /** @enum {string} */
            type: "SIMPLE" | "JAVASCRIPT" | "PYTHON";
            /** @enum {string} */
            resultType: "TEXT" | "LONGTEXT" | "NUMERIC" | "BINARY";
            enabled?: boolean | null;
            /**
             * Format: int64
             * @description The target group: its ID, or an object with its ID (such as the one read)
             */
            targetGroup?: number | null;
            script?: string | null;
            deviceDriver?: string | null;
            cliMode?: string | null;
            command?: string | null;
            modifierPattern?: string | null;
            modifierReplacement?: string | null;
        };
        ListResponseDiagnostic: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Diagnostic"][] | null;
        };
        /** @description Result of a diagnostic on a device */
        DiagnosticResult: {
            display: string | null;
            diagnostic: components["schemas"]["Diagnostic"] | null;
            type: string | null;
            value: unknown;
            /** Format: date-time */
            creationDate: Date | null;
            /** Format: date-time */
            lastCheckDate: Date | null;
        };
        ListResponseDiagnosticResult: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["DiagnosticResult"][] | null;
        };
        /** @description Definition of a device domain */
        DomainRequest: {
            name: string;
            description?: string | null;
            server4Address: string;
        };
        ListResponseDomain: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Domain"][] | null;
        };
        /** @description Definition of a device group */
        GroupRequest: {
            name: string;
            folder?: string | null;
            /** @enum {string} */
            type: "STATIC" | "DYNAMIC";
            hiddenFromReports?: boolean | null;
            query?: string | null;
            /** @description IDs of the devices of a static group (write-only: absent means unchanged) */
            devices?: number[] | null;
        };
        ListResponseGroup: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Group"][] | null;
        };
        /** @description Hook (action run when an event occurs) */
        Hook: {
            type: string;
        } & components["schemas"]["WebHook"];
        /** @description Event which triggers a hook */
        HookTrigger: {
            /** @enum {string|null} */
            type: "POST_TASK" | null;
            item: string | null;
        };
        /** @description Hook calling a URL */
        WebHook: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            /** Format: int64 */
            readonly version: number;
            /** Format: date-time */
            readonly changeDate: Date | null;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "WEB";
            name: string | null;
            enabled: boolean;
            triggers: components["schemas"]["HookTrigger"][] | null;
            /** @enum {string|null} */
            action: "POST_XML" | "POST_JSON" | "POST_YAML" | null;
            targetUrl: string | null;
            /** @enum {string|null} */
            httpsCaTrustMode: "TRUST_ANY" | "SYSTEM_TRUSTSTORE" | "CUSTOM_CA" | null;
            httpsCustomCaCertificate: string | null;
        };
        /** @description Definition of a hook */
        HookRequest: {
            type: string;
        } & components["schemas"]["WebHookRequest"];
        /** @description Definition of a hook calling a URL */
        WebHookRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "WEB";
            name?: string | null;
            enabled?: boolean | null;
            triggers?: components["schemas"]["HookTrigger"][] | null;
            /** @enum {string|null} */
            action?: "POST_XML" | "POST_JSON" | "POST_YAML" | null;
            targetUrl?: string | null;
            /** @enum {string|null} */
            httpsCaTrustMode?: "TRUST_ANY" | "SYSTEM_TRUSTSTORE" | "CUSTOM_CA" | null;
            httpsCustomCaCertificate?: string | null;
        };
        ListResponseHook: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Hook"][] | null;
        };
        /** @description Command script */
        Script: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
            /** Format: int64 */
            version: number;
            /** Format: date-time */
            changeDate: Date | null;
            name: string | null;
            folder: string | null;
            deviceDriver: string | null;
            deviceDriverDescription: string | null;
            author: string | null;
            script: string | null;
            userInputDefinitions: {
                [key: string]: components["schemas"]["UserInputDefinition"];
            } | null;
        };
        /** @description User input of a command script */
        UserInputDefinition: {
            name: string | null;
            /** @enum {string|null} */
            type: "TEXT" | "BOOLEAN" | null;
            label: string | null;
            description: string | null;
            choices: string[] | null;
            defaultValue: string | null;
        };
        /** @description Definition of a command script */
        ScriptRequest: {
            name: string;
            folder?: string | null;
            deviceDriver: string;
            script: string;
        };
        ListResponseScript: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Script"][] | null;
        };
        /** @description Liveness and readiness of the server */
        Health: {
            status: string | null;
        };
        /** @description Mastership status of the server in the high availability cluster */
        ClusterMaster: {
            clusterEnabled: boolean;
            master: boolean;
            currentMasterId: string | null;
        };
        /** @description General information about the Netshot server */
        SystemInfo: {
            readonly version: string | null;
            /** Format: date-time */
            readonly serverTime: Date | null;
            /** Format: int32 */
            readonly sessionMaxIdleTime: number;
        };
        /** @description Member of the high availability cluster */
        ClusterMember: {
            readonly instanceId: string | null;
            readonly display: string | null;
            local: boolean;
            hostname: string | null;
            /** Format: int32 */
            clusteringVersion: number;
            /** Format: int32 */
            masterPriority: number;
            /** Format: int32 */
            runnerPriority: number;
            /** Format: int32 */
            runnerWeight: number;
            appVersion: string | null;
            jvmVersion: string | null;
            driverHash: string | null;
            status: string | null;
            /** Format: date-time */
            lastStatusChangeDate: Date | null;
            /** Format: date-time */
            lastSeenDate: Date | null;
        };
        ListResponseClusterMember: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["ClusterMember"][] | null;
        };
        ListResponseService: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Service"][] | null;
        };
        /** @description Service of the Netshot server */
        Service: {
            readonly display: string | null;
            name: string | null;
            state: string | null;
            healthy: boolean;
            error: string | null;
            stats: {
                [key: string]: unknown;
            } | null;
        };
        ListResponseTask: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["Task"][] | null;
        };
        /** @description Task to create */
        TaskRequest: {
            type: string;
            /** Format: int64 */
            device?: number | null;
            /** Format: int64 */
            group?: number | null;
            deviceList?: number[] | null;
            /** @description Parameters of the type of task */
            parameters?: {
                [key: string]: unknown;
            } | null;
            comments?: string | null;
            /** @enum {string|null} */
            scheduleType?: "ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY" | null;
            /** Format: date-time */
            scheduleReference?: Date | null;
            /** Format: int32 */
            scheduleFactor?: number | null;
            /** Format: int32 */
            priority?: number | null;
            debugEnabled?: boolean | null;
            /** @enum {string|null} */
            scheduleMode?: "PARALLEL" | "SEQUENTIAL" | null;
            stopOnFailure?: boolean | null;
        };
        /** @description Number of tasks by status */
        TaskStatusCounts: {
            countByStatus: {
                [key: string]: number;
            } | null;
            /** Format: int32 */
            threadCount: number;
        };
        /** @description Netshot user */
        User: {
            /** Format: int64 */
            readonly id: number;
            readonly url: string | null;
            readonly display: string | null;
            /** Format: int64 */
            readonly version: number;
            /** Format: date-time */
            readonly changeDate: Date | null;
            username: string | null;
            local: boolean;
            /** Format: int32 */
            level: number;
            readonly role: string | null;
            readonly hasPassword: boolean;
            /** Format: date-time */
            readonly lastPasswordChangeDate: Date | null;
        };
        /** @description Definition of a Netshot user */
        UserRequest: {
            username: string;
            local: boolean;
            /** Format: int32 */
            level: number;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            password?: string | null;
        };
        ListResponseUser: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["User"][] | null;
        };
        /** @description Definition of a Vault instance */
        VaultInstanceRequest: {
            name: string;
            /** @enum {string} */
            type: "HASHICORP_KV2";
            baseUrl: string;
            namespace?: string | null;
            /** @enum {string|null} */
            httpsCaTrustMode?: "TRUST_ANY" | "SYSTEM_TRUSTSTORE" | "CUSTOM_CA" | null;
            httpsCustomCaCertificate?: string | null;
            kvMountPath?: string | null;
            /** @enum {string} */
            authMethod: "APPROLE" | "JWT";
            appRoleMountPath?: string | null;
            appRoleId?: string | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            appRoleSecretId?: string | null;
            jwtMountPath?: string | null;
            jwtIdpTokenEndpoint?: string | null;
            jwtClientId?: string | null;
            /**
             * Format: password
             * @description Write-only secret: absent leaves the stored value unchanged, null clears it
             */
            jwtClientSecret?: string | null;
            jwtVaultRole?: string | null;
            jwtScope?: string | null;
        };
        ListResponseVaultInstance: {
            /**
             * Format: int64
             * @description Total number of matching items (null unless count=true)
             */
            count: number | null;
            /**
             * Format: int32
             * @description Offset of the first item of the page
             */
            offset: number;
            /**
             * Format: int32
             * @description Maximum number of items of the page (0: all the items)
             */
            limit: number;
            /** @description Path of the next page */
            next: string | null;
            /** @description Path of the previous page */
            previous: string | null;
            /** @description The items of the page */
            results: components["schemas"]["VaultInstance"][] | null;
        };
        /** @description Outcome of a test of the connection to a Vault server */
        VaultConnectionTest: {
            success: boolean;
            message: string | null;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    listApiTokens: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, description, level */
                sort?: string;
                /** @description Free text search; fields: description */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: description equal to */
                description?: string[];
                /** @description Filter: description not equal to */
                description__n?: string[];
                /** @description Filter: description equal to, ignoring the case */
                description__ie?: string[];
                /** @description Filter: description not equal to, ignoring the case */
                description__nie?: string[];
                /** @description Filter: description contains, ignoring the case */
                description__ic?: string[];
                /** @description Filter: description doesn't contain, ignoring the case */
                description__nic?: string[];
                /** @description Filter: description starts with, ignoring the case */
                description__isw?: string[];
                /** @description Filter: description doesn't start with, ignoring the case */
                description__nisw?: string[];
                /** @description Filter: description ends with, ignoring the case */
                description__iew?: string[];
                /** @description Filter: description doesn't end with, ignoring the case */
                description__niew?: string[];
                /** @description Filter: description matches the (POSIX) regular expression */
                description__regex?: string[];
                /** @description Filter: description matches the (POSIX) regular expression, ignoring the case */
                description__iregex?: string[];
                /** @description Filter: description is empty (null, or an empty string) for true, isn't for false */
                description__empty?: boolean;
                /** @description Filter: level equal to */
                level?: number[];
                /** @description Filter: level not equal to */
                level__n?: number[];
                /** @description Filter: level lower than */
                level__lt?: number[];
                /** @description Filter: level lower than or equal to */
                level__lte?: number[];
                /** @description Filter: level greater than */
                level__gt?: number[];
                /** @description Filter: level greater than or equal to */
                level__gte?: number[];
                /** @description Filter: level is empty (null, or an empty string) for true, isn't for false */
                level__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseApiToken"];
                    "application/xml": components["schemas"]["ListResponseApiToken"];
                    "application/yaml": components["schemas"]["ListResponseApiToken"];
                };
            };
        };
    };
    createApiToken: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ApiTokenRequest"];
                "application/xml": components["schemas"]["ApiTokenRequest"];
                "application/yaml": components["schemas"]["ApiTokenRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ApiTokenCreated"];
                    "application/xml": components["schemas"]["ApiTokenCreated"];
                    "application/yaml": components["schemas"]["ApiTokenCreated"];
                };
            };
        };
    };
    getApiToken: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description API token ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ApiToken"];
                    "application/xml": components["schemas"]["ApiToken"];
                    "application/yaml": components["schemas"]["ApiToken"];
                };
            };
        };
    };
    deleteApiToken: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description API token ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    getMe: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CurrentUser"];
                    "application/xml": components["schemas"]["CurrentUser"];
                    "application/yaml": components["schemas"]["CurrentUser"];
                };
            };
        };
    };
    changePassword: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PasswordChangeRequest"];
                "application/xml": components["schemas"]["PasswordChangeRequest"];
                "application/yaml": components["schemas"]["PasswordChangeRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CurrentUser"];
                    "application/xml": components["schemas"]["CurrentUser"];
                    "application/yaml": components["schemas"]["CurrentUser"];
                };
            };
        };
    };
    getSession: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Session"];
                    "application/xml": components["schemas"]["Session"];
                    "application/yaml": components["schemas"]["Session"];
                };
            };
        };
    };
    login: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SessionRequest"];
                "application/xml": components["schemas"]["SessionRequest"];
                "application/yaml": components["schemas"]["SessionRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Session"];
                    "application/xml": components["schemas"]["Session"];
                    "application/yaml": components["schemas"]["Session"];
                };
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    getResultStats: {
        parameters: {
            query?: {
                /** @description Free text search (as in the list) */
                q?: string;
                /** @description Fields to count by (up to two), comma-separated; fields: device, domain, group, rule, policy, result */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets; fields: checkDate, exemptionExpirationDate */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: deviceName equal to */
                deviceName?: string[];
                /** @description Filter: deviceName not equal to */
                deviceName__n?: string[];
                /** @description Filter: deviceName equal to, ignoring the case */
                deviceName__ie?: string[];
                /** @description Filter: deviceName not equal to, ignoring the case */
                deviceName__nie?: string[];
                /** @description Filter: deviceName contains, ignoring the case */
                deviceName__ic?: string[];
                /** @description Filter: deviceName doesn't contain, ignoring the case */
                deviceName__nic?: string[];
                /** @description Filter: deviceName starts with, ignoring the case */
                deviceName__isw?: string[];
                /** @description Filter: deviceName doesn't start with, ignoring the case */
                deviceName__nisw?: string[];
                /** @description Filter: deviceName ends with, ignoring the case */
                deviceName__iew?: string[];
                /** @description Filter: deviceName doesn't end with, ignoring the case */
                deviceName__niew?: string[];
                /** @description Filter: deviceName matches the (POSIX) regular expression */
                deviceName__regex?: string[];
                /** @description Filter: deviceName matches the (POSIX) regular expression, ignoring the case */
                deviceName__iregex?: string[];
                /** @description Filter: deviceName is empty (null, or an empty string) for true, isn't for false */
                deviceName__empty?: boolean;
                /** @description Filter: domain equal to */
                domain?: number[];
                /** @description Filter: domain not equal to */
                domain__n?: number[];
                /** @description Filter: domain lower than */
                domain__lt?: number[];
                /** @description Filter: domain lower than or equal to */
                domain__lte?: number[];
                /** @description Filter: domain greater than */
                domain__gt?: number[];
                /** @description Filter: domain greater than or equal to */
                domain__gte?: number[];
                /** @description Filter: domain is empty (null, or an empty string) for true, isn't for false */
                domain__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: rule equal to */
                rule?: number[];
                /** @description Filter: rule not equal to */
                rule__n?: number[];
                /** @description Filter: rule lower than */
                rule__lt?: number[];
                /** @description Filter: rule lower than or equal to */
                rule__lte?: number[];
                /** @description Filter: rule greater than */
                rule__gt?: number[];
                /** @description Filter: rule greater than or equal to */
                rule__gte?: number[];
                /** @description Filter: rule is empty (null, or an empty string) for true, isn't for false */
                rule__empty?: boolean;
                /** @description Filter: ruleName equal to */
                ruleName?: string[];
                /** @description Filter: ruleName not equal to */
                ruleName__n?: string[];
                /** @description Filter: ruleName equal to, ignoring the case */
                ruleName__ie?: string[];
                /** @description Filter: ruleName not equal to, ignoring the case */
                ruleName__nie?: string[];
                /** @description Filter: ruleName contains, ignoring the case */
                ruleName__ic?: string[];
                /** @description Filter: ruleName doesn't contain, ignoring the case */
                ruleName__nic?: string[];
                /** @description Filter: ruleName starts with, ignoring the case */
                ruleName__isw?: string[];
                /** @description Filter: ruleName doesn't start with, ignoring the case */
                ruleName__nisw?: string[];
                /** @description Filter: ruleName ends with, ignoring the case */
                ruleName__iew?: string[];
                /** @description Filter: ruleName doesn't end with, ignoring the case */
                ruleName__niew?: string[];
                /** @description Filter: ruleName matches the (POSIX) regular expression */
                ruleName__regex?: string[];
                /** @description Filter: ruleName matches the (POSIX) regular expression, ignoring the case */
                ruleName__iregex?: string[];
                /** @description Filter: ruleName is empty (null, or an empty string) for true, isn't for false */
                ruleName__empty?: boolean;
                /** @description Filter: policy equal to */
                policy?: number[];
                /** @description Filter: policy not equal to */
                policy__n?: number[];
                /** @description Filter: policy lower than */
                policy__lt?: number[];
                /** @description Filter: policy lower than or equal to */
                policy__lte?: number[];
                /** @description Filter: policy greater than */
                policy__gt?: number[];
                /** @description Filter: policy greater than or equal to */
                policy__gte?: number[];
                /** @description Filter: policy is empty (null, or an empty string) for true, isn't for false */
                policy__empty?: boolean;
                /** @description Filter: policyName equal to */
                policyName?: string[];
                /** @description Filter: policyName not equal to */
                policyName__n?: string[];
                /** @description Filter: policyName equal to, ignoring the case */
                policyName__ie?: string[];
                /** @description Filter: policyName not equal to, ignoring the case */
                policyName__nie?: string[];
                /** @description Filter: policyName contains, ignoring the case */
                policyName__ic?: string[];
                /** @description Filter: policyName doesn't contain, ignoring the case */
                policyName__nic?: string[];
                /** @description Filter: policyName starts with, ignoring the case */
                policyName__isw?: string[];
                /** @description Filter: policyName doesn't start with, ignoring the case */
                policyName__nisw?: string[];
                /** @description Filter: policyName ends with, ignoring the case */
                policyName__iew?: string[];
                /** @description Filter: policyName doesn't end with, ignoring the case */
                policyName__niew?: string[];
                /** @description Filter: policyName matches the (POSIX) regular expression */
                policyName__regex?: string[];
                /** @description Filter: policyName matches the (POSIX) regular expression, ignoring the case */
                policyName__iregex?: string[];
                /** @description Filter: policyName is empty (null, or an empty string) for true, isn't for false */
                policyName__empty?: boolean;
                /** @description Filter: result equal to */
                result?: ("CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE")[];
                /** @description Filter: result not equal to */
                result__n?: ("CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE")[];
                /** @description Filter: result is empty (null, or an empty string) for true, isn't for false */
                result__empty?: boolean;
                /** @description Filter: checkDate equal to */
                checkDate?: string[];
                /** @description Filter: checkDate not equal to */
                checkDate__n?: string[];
                /** @description Filter: checkDate lower than */
                checkDate__lt?: string[];
                /** @description Filter: checkDate lower than or equal to */
                checkDate__lte?: string[];
                /** @description Filter: checkDate greater than */
                checkDate__gt?: string[];
                /** @description Filter: checkDate greater than or equal to */
                checkDate__gte?: string[];
                /** @description Filter: checkDate is empty (null, or an empty string) for true, isn't for false */
                checkDate__empty?: boolean;
                /** @description Filter: exemptionExpirationDate equal to */
                exemptionExpirationDate?: string[];
                /** @description Filter: exemptionExpirationDate not equal to */
                exemptionExpirationDate__n?: string[];
                /** @description Filter: exemptionExpirationDate lower than */
                exemptionExpirationDate__lt?: string[];
                /** @description Filter: exemptionExpirationDate lower than or equal to */
                exemptionExpirationDate__lte?: string[];
                /** @description Filter: exemptionExpirationDate greater than */
                exemptionExpirationDate__gt?: string[];
                /** @description Filter: exemptionExpirationDate greater than or equal to */
                exemptionExpirationDate__gte?: string[];
                /** @description Filter: exemptionExpirationDate is empty (null, or an empty string) for true, isn't for false */
                exemptionExpirationDate__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Stats"];
                    "application/xml": components["schemas"]["Stats"];
                    "application/yaml": components["schemas"]["Stats"];
                };
            };
        };
    };
    listResults: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: device, deviceName, rule, ruleName, policy, policyName, result, checkDate, exemptionExpirationDate */
                sort?: string;
                /** @description Free text search; fields: deviceName, ruleName, policyName */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: deviceName equal to */
                deviceName?: string[];
                /** @description Filter: deviceName not equal to */
                deviceName__n?: string[];
                /** @description Filter: deviceName equal to, ignoring the case */
                deviceName__ie?: string[];
                /** @description Filter: deviceName not equal to, ignoring the case */
                deviceName__nie?: string[];
                /** @description Filter: deviceName contains, ignoring the case */
                deviceName__ic?: string[];
                /** @description Filter: deviceName doesn't contain, ignoring the case */
                deviceName__nic?: string[];
                /** @description Filter: deviceName starts with, ignoring the case */
                deviceName__isw?: string[];
                /** @description Filter: deviceName doesn't start with, ignoring the case */
                deviceName__nisw?: string[];
                /** @description Filter: deviceName ends with, ignoring the case */
                deviceName__iew?: string[];
                /** @description Filter: deviceName doesn't end with, ignoring the case */
                deviceName__niew?: string[];
                /** @description Filter: deviceName matches the (POSIX) regular expression */
                deviceName__regex?: string[];
                /** @description Filter: deviceName matches the (POSIX) regular expression, ignoring the case */
                deviceName__iregex?: string[];
                /** @description Filter: deviceName is empty (null, or an empty string) for true, isn't for false */
                deviceName__empty?: boolean;
                /** @description Filter: domain equal to */
                domain?: number[];
                /** @description Filter: domain not equal to */
                domain__n?: number[];
                /** @description Filter: domain lower than */
                domain__lt?: number[];
                /** @description Filter: domain lower than or equal to */
                domain__lte?: number[];
                /** @description Filter: domain greater than */
                domain__gt?: number[];
                /** @description Filter: domain greater than or equal to */
                domain__gte?: number[];
                /** @description Filter: domain is empty (null, or an empty string) for true, isn't for false */
                domain__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: rule equal to */
                rule?: number[];
                /** @description Filter: rule not equal to */
                rule__n?: number[];
                /** @description Filter: rule lower than */
                rule__lt?: number[];
                /** @description Filter: rule lower than or equal to */
                rule__lte?: number[];
                /** @description Filter: rule greater than */
                rule__gt?: number[];
                /** @description Filter: rule greater than or equal to */
                rule__gte?: number[];
                /** @description Filter: rule is empty (null, or an empty string) for true, isn't for false */
                rule__empty?: boolean;
                /** @description Filter: ruleName equal to */
                ruleName?: string[];
                /** @description Filter: ruleName not equal to */
                ruleName__n?: string[];
                /** @description Filter: ruleName equal to, ignoring the case */
                ruleName__ie?: string[];
                /** @description Filter: ruleName not equal to, ignoring the case */
                ruleName__nie?: string[];
                /** @description Filter: ruleName contains, ignoring the case */
                ruleName__ic?: string[];
                /** @description Filter: ruleName doesn't contain, ignoring the case */
                ruleName__nic?: string[];
                /** @description Filter: ruleName starts with, ignoring the case */
                ruleName__isw?: string[];
                /** @description Filter: ruleName doesn't start with, ignoring the case */
                ruleName__nisw?: string[];
                /** @description Filter: ruleName ends with, ignoring the case */
                ruleName__iew?: string[];
                /** @description Filter: ruleName doesn't end with, ignoring the case */
                ruleName__niew?: string[];
                /** @description Filter: ruleName matches the (POSIX) regular expression */
                ruleName__regex?: string[];
                /** @description Filter: ruleName matches the (POSIX) regular expression, ignoring the case */
                ruleName__iregex?: string[];
                /** @description Filter: ruleName is empty (null, or an empty string) for true, isn't for false */
                ruleName__empty?: boolean;
                /** @description Filter: policy equal to */
                policy?: number[];
                /** @description Filter: policy not equal to */
                policy__n?: number[];
                /** @description Filter: policy lower than */
                policy__lt?: number[];
                /** @description Filter: policy lower than or equal to */
                policy__lte?: number[];
                /** @description Filter: policy greater than */
                policy__gt?: number[];
                /** @description Filter: policy greater than or equal to */
                policy__gte?: number[];
                /** @description Filter: policy is empty (null, or an empty string) for true, isn't for false */
                policy__empty?: boolean;
                /** @description Filter: policyName equal to */
                policyName?: string[];
                /** @description Filter: policyName not equal to */
                policyName__n?: string[];
                /** @description Filter: policyName equal to, ignoring the case */
                policyName__ie?: string[];
                /** @description Filter: policyName not equal to, ignoring the case */
                policyName__nie?: string[];
                /** @description Filter: policyName contains, ignoring the case */
                policyName__ic?: string[];
                /** @description Filter: policyName doesn't contain, ignoring the case */
                policyName__nic?: string[];
                /** @description Filter: policyName starts with, ignoring the case */
                policyName__isw?: string[];
                /** @description Filter: policyName doesn't start with, ignoring the case */
                policyName__nisw?: string[];
                /** @description Filter: policyName ends with, ignoring the case */
                policyName__iew?: string[];
                /** @description Filter: policyName doesn't end with, ignoring the case */
                policyName__niew?: string[];
                /** @description Filter: policyName matches the (POSIX) regular expression */
                policyName__regex?: string[];
                /** @description Filter: policyName matches the (POSIX) regular expression, ignoring the case */
                policyName__iregex?: string[];
                /** @description Filter: policyName is empty (null, or an empty string) for true, isn't for false */
                policyName__empty?: boolean;
                /** @description Filter: result equal to */
                result?: ("CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE")[];
                /** @description Filter: result not equal to */
                result__n?: ("CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE")[];
                /** @description Filter: result is empty (null, or an empty string) for true, isn't for false */
                result__empty?: boolean;
                /** @description Filter: checkDate equal to */
                checkDate?: string[];
                /** @description Filter: checkDate not equal to */
                checkDate__n?: string[];
                /** @description Filter: checkDate lower than */
                checkDate__lt?: string[];
                /** @description Filter: checkDate lower than or equal to */
                checkDate__lte?: string[];
                /** @description Filter: checkDate greater than */
                checkDate__gt?: string[];
                /** @description Filter: checkDate greater than or equal to */
                checkDate__gte?: string[];
                /** @description Filter: checkDate is empty (null, or an empty string) for true, isn't for false */
                checkDate__empty?: boolean;
                /** @description Filter: exemptionExpirationDate equal to */
                exemptionExpirationDate?: string[];
                /** @description Filter: exemptionExpirationDate not equal to */
                exemptionExpirationDate__n?: string[];
                /** @description Filter: exemptionExpirationDate lower than */
                exemptionExpirationDate__lt?: string[];
                /** @description Filter: exemptionExpirationDate lower than or equal to */
                exemptionExpirationDate__lte?: string[];
                /** @description Filter: exemptionExpirationDate greater than */
                exemptionExpirationDate__gt?: string[];
                /** @description Filter: exemptionExpirationDate greater than or equal to */
                exemptionExpirationDate__gte?: string[];
                /** @description Filter: exemptionExpirationDate is empty (null, or an empty string) for true, isn't for false */
                exemptionExpirationDate__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDeviceComplianceResult"];
                    "application/xml": components["schemas"]["ListResponseDeviceComplianceResult"];
                    "application/yaml": components["schemas"]["ListResponseDeviceComplianceResult"];
                };
            };
        };
    };
    listResults_1: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: ruleName, policy, policyName, result, checkDate, exemptionExpirationDate */
                sort?: string;
                /** @description Free text search; fields: ruleName, policyName */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: rule equal to */
                rule?: number[];
                /** @description Filter: rule not equal to */
                rule__n?: number[];
                /** @description Filter: rule lower than */
                rule__lt?: number[];
                /** @description Filter: rule lower than or equal to */
                rule__lte?: number[];
                /** @description Filter: rule greater than */
                rule__gt?: number[];
                /** @description Filter: rule greater than or equal to */
                rule__gte?: number[];
                /** @description Filter: rule is empty (null, or an empty string) for true, isn't for false */
                rule__empty?: boolean;
                /** @description Filter: ruleName equal to */
                ruleName?: string[];
                /** @description Filter: ruleName not equal to */
                ruleName__n?: string[];
                /** @description Filter: ruleName equal to, ignoring the case */
                ruleName__ie?: string[];
                /** @description Filter: ruleName not equal to, ignoring the case */
                ruleName__nie?: string[];
                /** @description Filter: ruleName contains, ignoring the case */
                ruleName__ic?: string[];
                /** @description Filter: ruleName doesn't contain, ignoring the case */
                ruleName__nic?: string[];
                /** @description Filter: ruleName starts with, ignoring the case */
                ruleName__isw?: string[];
                /** @description Filter: ruleName doesn't start with, ignoring the case */
                ruleName__nisw?: string[];
                /** @description Filter: ruleName ends with, ignoring the case */
                ruleName__iew?: string[];
                /** @description Filter: ruleName doesn't end with, ignoring the case */
                ruleName__niew?: string[];
                /** @description Filter: ruleName matches the (POSIX) regular expression */
                ruleName__regex?: string[];
                /** @description Filter: ruleName matches the (POSIX) regular expression, ignoring the case */
                ruleName__iregex?: string[];
                /** @description Filter: ruleName is empty (null, or an empty string) for true, isn't for false */
                ruleName__empty?: boolean;
                /** @description Filter: policy equal to */
                policy?: number[];
                /** @description Filter: policy not equal to */
                policy__n?: number[];
                /** @description Filter: policy lower than */
                policy__lt?: number[];
                /** @description Filter: policy lower than or equal to */
                policy__lte?: number[];
                /** @description Filter: policy greater than */
                policy__gt?: number[];
                /** @description Filter: policy greater than or equal to */
                policy__gte?: number[];
                /** @description Filter: policy is empty (null, or an empty string) for true, isn't for false */
                policy__empty?: boolean;
                /** @description Filter: policyName equal to */
                policyName?: string[];
                /** @description Filter: policyName not equal to */
                policyName__n?: string[];
                /** @description Filter: policyName equal to, ignoring the case */
                policyName__ie?: string[];
                /** @description Filter: policyName not equal to, ignoring the case */
                policyName__nie?: string[];
                /** @description Filter: policyName contains, ignoring the case */
                policyName__ic?: string[];
                /** @description Filter: policyName doesn't contain, ignoring the case */
                policyName__nic?: string[];
                /** @description Filter: policyName starts with, ignoring the case */
                policyName__isw?: string[];
                /** @description Filter: policyName doesn't start with, ignoring the case */
                policyName__nisw?: string[];
                /** @description Filter: policyName ends with, ignoring the case */
                policyName__iew?: string[];
                /** @description Filter: policyName doesn't end with, ignoring the case */
                policyName__niew?: string[];
                /** @description Filter: policyName matches the (POSIX) regular expression */
                policyName__regex?: string[];
                /** @description Filter: policyName matches the (POSIX) regular expression, ignoring the case */
                policyName__iregex?: string[];
                /** @description Filter: policyName is empty (null, or an empty string) for true, isn't for false */
                policyName__empty?: boolean;
                /** @description Filter: result equal to */
                result?: ("CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE")[];
                /** @description Filter: result not equal to */
                result__n?: ("CONFORMING" | "EXEMPTED" | "INVALIDRULE" | "DISABLED" | "NONCONFORMING" | "NOTAPPLICABLE")[];
                /** @description Filter: result is empty (null, or an empty string) for true, isn't for false */
                result__empty?: boolean;
                /** @description Filter: checkDate equal to */
                checkDate?: string[];
                /** @description Filter: checkDate not equal to */
                checkDate__n?: string[];
                /** @description Filter: checkDate lower than */
                checkDate__lt?: string[];
                /** @description Filter: checkDate lower than or equal to */
                checkDate__lte?: string[];
                /** @description Filter: checkDate greater than */
                checkDate__gt?: string[];
                /** @description Filter: checkDate greater than or equal to */
                checkDate__gte?: string[];
                /** @description Filter: checkDate is empty (null, or an empty string) for true, isn't for false */
                checkDate__empty?: boolean;
                /** @description Filter: exemptionExpirationDate equal to */
                exemptionExpirationDate?: string[];
                /** @description Filter: exemptionExpirationDate not equal to */
                exemptionExpirationDate__n?: string[];
                /** @description Filter: exemptionExpirationDate lower than */
                exemptionExpirationDate__lt?: string[];
                /** @description Filter: exemptionExpirationDate lower than or equal to */
                exemptionExpirationDate__lte?: string[];
                /** @description Filter: exemptionExpirationDate greater than */
                exemptionExpirationDate__gt?: string[];
                /** @description Filter: exemptionExpirationDate greater than or equal to */
                exemptionExpirationDate__gte?: string[];
                /** @description Filter: exemptionExpirationDate is empty (null, or an empty string) for true, isn't for false */
                exemptionExpirationDate__empty?: boolean;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDeviceComplianceResult"];
                    "application/xml": components["schemas"]["ListResponseDeviceComplianceResult"];
                    "application/yaml": components["schemas"]["ListResponseDeviceComplianceResult"];
                };
            };
        };
    };
    listHardwareRules: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, driver, family, partNumber, endOfSale, endOfLife */
                sort?: string;
                /** @description Free text search; fields: family, partNumber */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: group lower than */
                group__lt?: number[];
                /** @description Filter: group lower than or equal to */
                group__lte?: number[];
                /** @description Filter: group greater than */
                group__gt?: number[];
                /** @description Filter: group greater than or equal to */
                group__gte?: number[];
                /** @description Filter: group is empty (null, or an empty string) for true, isn't for false */
                group__empty?: boolean;
                /** @description Filter: driver equal to */
                driver?: string[];
                /** @description Filter: driver not equal to */
                driver__n?: string[];
                /** @description Filter: driver equal to, ignoring the case */
                driver__ie?: string[];
                /** @description Filter: driver not equal to, ignoring the case */
                driver__nie?: string[];
                /** @description Filter: driver contains, ignoring the case */
                driver__ic?: string[];
                /** @description Filter: driver doesn't contain, ignoring the case */
                driver__nic?: string[];
                /** @description Filter: driver starts with, ignoring the case */
                driver__isw?: string[];
                /** @description Filter: driver doesn't start with, ignoring the case */
                driver__nisw?: string[];
                /** @description Filter: driver ends with, ignoring the case */
                driver__iew?: string[];
                /** @description Filter: driver doesn't end with, ignoring the case */
                driver__niew?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression */
                driver__regex?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression, ignoring the case */
                driver__iregex?: string[];
                /** @description Filter: driver is empty (null, or an empty string) for true, isn't for false */
                driver__empty?: boolean;
                /** @description Filter: family equal to */
                family?: string[];
                /** @description Filter: family not equal to */
                family__n?: string[];
                /** @description Filter: family equal to, ignoring the case */
                family__ie?: string[];
                /** @description Filter: family not equal to, ignoring the case */
                family__nie?: string[];
                /** @description Filter: family contains, ignoring the case */
                family__ic?: string[];
                /** @description Filter: family doesn't contain, ignoring the case */
                family__nic?: string[];
                /** @description Filter: family starts with, ignoring the case */
                family__isw?: string[];
                /** @description Filter: family doesn't start with, ignoring the case */
                family__nisw?: string[];
                /** @description Filter: family ends with, ignoring the case */
                family__iew?: string[];
                /** @description Filter: family doesn't end with, ignoring the case */
                family__niew?: string[];
                /** @description Filter: family matches the (POSIX) regular expression */
                family__regex?: string[];
                /** @description Filter: family matches the (POSIX) regular expression, ignoring the case */
                family__iregex?: string[];
                /** @description Filter: family is empty (null, or an empty string) for true, isn't for false */
                family__empty?: boolean;
                /** @description Filter: familyRegExp equal to */
                familyRegExp?: boolean[];
                /** @description Filter: partNumber equal to */
                partNumber?: string[];
                /** @description Filter: partNumber not equal to */
                partNumber__n?: string[];
                /** @description Filter: partNumber equal to, ignoring the case */
                partNumber__ie?: string[];
                /** @description Filter: partNumber not equal to, ignoring the case */
                partNumber__nie?: string[];
                /** @description Filter: partNumber contains, ignoring the case */
                partNumber__ic?: string[];
                /** @description Filter: partNumber doesn't contain, ignoring the case */
                partNumber__nic?: string[];
                /** @description Filter: partNumber starts with, ignoring the case */
                partNumber__isw?: string[];
                /** @description Filter: partNumber doesn't start with, ignoring the case */
                partNumber__nisw?: string[];
                /** @description Filter: partNumber ends with, ignoring the case */
                partNumber__iew?: string[];
                /** @description Filter: partNumber doesn't end with, ignoring the case */
                partNumber__niew?: string[];
                /** @description Filter: partNumber matches the (POSIX) regular expression */
                partNumber__regex?: string[];
                /** @description Filter: partNumber matches the (POSIX) regular expression, ignoring the case */
                partNumber__iregex?: string[];
                /** @description Filter: partNumber is empty (null, or an empty string) for true, isn't for false */
                partNumber__empty?: boolean;
                /** @description Filter: partNumberRegExp equal to */
                partNumberRegExp?: boolean[];
                /** @description Filter: endOfSale equal to */
                endOfSale?: string[];
                /** @description Filter: endOfSale not equal to */
                endOfSale__n?: string[];
                /** @description Filter: endOfSale lower than */
                endOfSale__lt?: string[];
                /** @description Filter: endOfSale lower than or equal to */
                endOfSale__lte?: string[];
                /** @description Filter: endOfSale greater than */
                endOfSale__gt?: string[];
                /** @description Filter: endOfSale greater than or equal to */
                endOfSale__gte?: string[];
                /** @description Filter: endOfSale is empty (null, or an empty string) for true, isn't for false */
                endOfSale__empty?: boolean;
                /** @description Filter: endOfLife equal to */
                endOfLife?: string[];
                /** @description Filter: endOfLife not equal to */
                endOfLife__n?: string[];
                /** @description Filter: endOfLife lower than */
                endOfLife__lt?: string[];
                /** @description Filter: endOfLife lower than or equal to */
                endOfLife__lte?: string[];
                /** @description Filter: endOfLife greater than */
                endOfLife__gt?: string[];
                /** @description Filter: endOfLife greater than or equal to */
                endOfLife__gte?: string[];
                /** @description Filter: endOfLife is empty (null, or an empty string) for true, isn't for false */
                endOfLife__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseHardwareRule"];
                    "application/xml": components["schemas"]["ListResponseHardwareRule"];
                    "application/yaml": components["schemas"]["ListResponseHardwareRule"];
                };
            };
        };
    };
    createHardwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["HardwareRuleRequest"];
                "application/xml": components["schemas"]["HardwareRuleRequest"];
                "application/yaml": components["schemas"]["HardwareRuleRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HardwareRule"];
                    "application/xml": components["schemas"]["HardwareRule"];
                    "application/yaml": components["schemas"]["HardwareRule"];
                };
            };
        };
    };
    getHardwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Hardware rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HardwareRule"];
                    "application/xml": components["schemas"]["HardwareRule"];
                    "application/yaml": components["schemas"]["HardwareRule"];
                };
            };
        };
    };
    replaceHardwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Hardware rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["HardwareRuleRequest"];
                "application/xml": components["schemas"]["HardwareRuleRequest"];
                "application/yaml": components["schemas"]["HardwareRuleRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HardwareRule"];
                    "application/xml": components["schemas"]["HardwareRule"];
                    "application/yaml": components["schemas"]["HardwareRule"];
                };
            };
        };
    };
    deleteHardwareRule: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Hardware rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateHardwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Hardware rule ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HardwareRule"];
                    "application/xml": components["schemas"]["HardwareRule"];
                    "application/yaml": components["schemas"]["HardwareRule"];
                };
            };
        };
    };
    listPolicies: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name */
                sort?: string;
                /** @description Free text search; fields: name */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: targetGroup equal to */
                targetGroup?: number[];
                /** @description Filter: targetGroup not equal to */
                targetGroup__n?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponsePolicy"];
                    "application/xml": components["schemas"]["ListResponsePolicy"];
                    "application/yaml": components["schemas"]["ListResponsePolicy"];
                };
            };
        };
    };
    createPolicy: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PolicyRequest"];
                "application/xml": components["schemas"]["PolicyRequest"];
                "application/yaml": components["schemas"]["PolicyRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Policy"];
                    "application/xml": components["schemas"]["Policy"];
                    "application/yaml": components["schemas"]["Policy"];
                };
            };
        };
    };
    getPolicy: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Policy ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Policy"];
                    "application/xml": components["schemas"]["Policy"];
                    "application/yaml": components["schemas"]["Policy"];
                };
            };
        };
    };
    replacePolicy: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Policy ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PolicyRequest"];
                "application/xml": components["schemas"]["PolicyRequest"];
                "application/yaml": components["schemas"]["PolicyRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Policy"];
                    "application/xml": components["schemas"]["Policy"];
                    "application/yaml": components["schemas"]["Policy"];
                };
            };
        };
    };
    deletePolicy: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Policy ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updatePolicy: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Policy ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Policy"];
                    "application/xml": components["schemas"]["Policy"];
                    "application/yaml": components["schemas"]["Policy"];
                };
            };
        };
    };
    listPolicyRules: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, type, enabled, field, deviceDriver */
                sort?: string;
                /** @description Free text search; fields: name, text */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("JAVASCRIPT" | "PYTHON" | "TEXT")[];
                /** @description Filter: type not equal to */
                type__n?: ("JAVASCRIPT" | "PYTHON" | "TEXT")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: enabled equal to */
                enabled?: boolean[];
                /** @description Filter: policy equal to */
                policy?: number[];
                /** @description Filter: policy not equal to */
                policy__n?: number[];
                /** @description Filter: policy lower than */
                policy__lt?: number[];
                /** @description Filter: policy lower than or equal to */
                policy__lte?: number[];
                /** @description Filter: policy greater than */
                policy__gt?: number[];
                /** @description Filter: policy greater than or equal to */
                policy__gte?: number[];
                /** @description Filter: policy is empty (null, or an empty string) for true, isn't for false */
                policy__empty?: boolean;
                /** @description Filter: field equal to */
                field?: string[];
                /** @description Filter: field not equal to */
                field__n?: string[];
                /** @description Filter: field equal to, ignoring the case */
                field__ie?: string[];
                /** @description Filter: field not equal to, ignoring the case */
                field__nie?: string[];
                /** @description Filter: field contains, ignoring the case */
                field__ic?: string[];
                /** @description Filter: field doesn't contain, ignoring the case */
                field__nic?: string[];
                /** @description Filter: field starts with, ignoring the case */
                field__isw?: string[];
                /** @description Filter: field doesn't start with, ignoring the case */
                field__nisw?: string[];
                /** @description Filter: field ends with, ignoring the case */
                field__iew?: string[];
                /** @description Filter: field doesn't end with, ignoring the case */
                field__niew?: string[];
                /** @description Filter: field matches the (POSIX) regular expression */
                field__regex?: string[];
                /** @description Filter: field matches the (POSIX) regular expression, ignoring the case */
                field__iregex?: string[];
                /** @description Filter: field is empty (null, or an empty string) for true, isn't for false */
                field__empty?: boolean;
                /** @description Filter: deviceDriver equal to */
                deviceDriver?: string[];
                /** @description Filter: deviceDriver not equal to */
                deviceDriver__n?: string[];
                /** @description Filter: deviceDriver equal to, ignoring the case */
                deviceDriver__ie?: string[];
                /** @description Filter: deviceDriver not equal to, ignoring the case */
                deviceDriver__nie?: string[];
                /** @description Filter: deviceDriver contains, ignoring the case */
                deviceDriver__ic?: string[];
                /** @description Filter: deviceDriver doesn't contain, ignoring the case */
                deviceDriver__nic?: string[];
                /** @description Filter: deviceDriver starts with, ignoring the case */
                deviceDriver__isw?: string[];
                /** @description Filter: deviceDriver doesn't start with, ignoring the case */
                deviceDriver__nisw?: string[];
                /** @description Filter: deviceDriver ends with, ignoring the case */
                deviceDriver__iew?: string[];
                /** @description Filter: deviceDriver doesn't end with, ignoring the case */
                deviceDriver__niew?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression */
                deviceDriver__regex?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression, ignoring the case */
                deviceDriver__iregex?: string[];
                /** @description Filter: deviceDriver is empty (null, or an empty string) for true, isn't for false */
                deviceDriver__empty?: boolean;
                /** @description Filter: text equal to */
                text?: string[];
                /** @description Filter: text not equal to */
                text__n?: string[];
                /** @description Filter: text equal to, ignoring the case */
                text__ie?: string[];
                /** @description Filter: text not equal to, ignoring the case */
                text__nie?: string[];
                /** @description Filter: text contains, ignoring the case */
                text__ic?: string[];
                /** @description Filter: text doesn't contain, ignoring the case */
                text__nic?: string[];
                /** @description Filter: text starts with, ignoring the case */
                text__isw?: string[];
                /** @description Filter: text doesn't start with, ignoring the case */
                text__nisw?: string[];
                /** @description Filter: text ends with, ignoring the case */
                text__iew?: string[];
                /** @description Filter: text doesn't end with, ignoring the case */
                text__niew?: string[];
                /** @description Filter: text matches the (POSIX) regular expression */
                text__regex?: string[];
                /** @description Filter: text matches the (POSIX) regular expression, ignoring the case */
                text__iregex?: string[];
                /** @description Filter: text is empty (null, or an empty string) for true, isn't for false */
                text__empty?: boolean;
            };
            header?: never;
            path: {
                /** @description Policy ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseRule"];
                    "application/xml": components["schemas"]["ListResponseRule"];
                    "application/yaml": components["schemas"]["ListResponseRule"];
                };
            };
        };
    };
    listRules: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, type, enabled, field, deviceDriver */
                sort?: string;
                /** @description Free text search; fields: name, text */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("JAVASCRIPT" | "PYTHON" | "TEXT")[];
                /** @description Filter: type not equal to */
                type__n?: ("JAVASCRIPT" | "PYTHON" | "TEXT")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: enabled equal to */
                enabled?: boolean[];
                /** @description Filter: policy equal to */
                policy?: number[];
                /** @description Filter: policy not equal to */
                policy__n?: number[];
                /** @description Filter: policy lower than */
                policy__lt?: number[];
                /** @description Filter: policy lower than or equal to */
                policy__lte?: number[];
                /** @description Filter: policy greater than */
                policy__gt?: number[];
                /** @description Filter: policy greater than or equal to */
                policy__gte?: number[];
                /** @description Filter: policy is empty (null, or an empty string) for true, isn't for false */
                policy__empty?: boolean;
                /** @description Filter: field equal to */
                field?: string[];
                /** @description Filter: field not equal to */
                field__n?: string[];
                /** @description Filter: field equal to, ignoring the case */
                field__ie?: string[];
                /** @description Filter: field not equal to, ignoring the case */
                field__nie?: string[];
                /** @description Filter: field contains, ignoring the case */
                field__ic?: string[];
                /** @description Filter: field doesn't contain, ignoring the case */
                field__nic?: string[];
                /** @description Filter: field starts with, ignoring the case */
                field__isw?: string[];
                /** @description Filter: field doesn't start with, ignoring the case */
                field__nisw?: string[];
                /** @description Filter: field ends with, ignoring the case */
                field__iew?: string[];
                /** @description Filter: field doesn't end with, ignoring the case */
                field__niew?: string[];
                /** @description Filter: field matches the (POSIX) regular expression */
                field__regex?: string[];
                /** @description Filter: field matches the (POSIX) regular expression, ignoring the case */
                field__iregex?: string[];
                /** @description Filter: field is empty (null, or an empty string) for true, isn't for false */
                field__empty?: boolean;
                /** @description Filter: deviceDriver equal to */
                deviceDriver?: string[];
                /** @description Filter: deviceDriver not equal to */
                deviceDriver__n?: string[];
                /** @description Filter: deviceDriver equal to, ignoring the case */
                deviceDriver__ie?: string[];
                /** @description Filter: deviceDriver not equal to, ignoring the case */
                deviceDriver__nie?: string[];
                /** @description Filter: deviceDriver contains, ignoring the case */
                deviceDriver__ic?: string[];
                /** @description Filter: deviceDriver doesn't contain, ignoring the case */
                deviceDriver__nic?: string[];
                /** @description Filter: deviceDriver starts with, ignoring the case */
                deviceDriver__isw?: string[];
                /** @description Filter: deviceDriver doesn't start with, ignoring the case */
                deviceDriver__nisw?: string[];
                /** @description Filter: deviceDriver ends with, ignoring the case */
                deviceDriver__iew?: string[];
                /** @description Filter: deviceDriver doesn't end with, ignoring the case */
                deviceDriver__niew?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression */
                deviceDriver__regex?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression, ignoring the case */
                deviceDriver__iregex?: string[];
                /** @description Filter: deviceDriver is empty (null, or an empty string) for true, isn't for false */
                deviceDriver__empty?: boolean;
                /** @description Filter: text equal to */
                text?: string[];
                /** @description Filter: text not equal to */
                text__n?: string[];
                /** @description Filter: text equal to, ignoring the case */
                text__ie?: string[];
                /** @description Filter: text not equal to, ignoring the case */
                text__nie?: string[];
                /** @description Filter: text contains, ignoring the case */
                text__ic?: string[];
                /** @description Filter: text doesn't contain, ignoring the case */
                text__nic?: string[];
                /** @description Filter: text starts with, ignoring the case */
                text__isw?: string[];
                /** @description Filter: text doesn't start with, ignoring the case */
                text__nisw?: string[];
                /** @description Filter: text ends with, ignoring the case */
                text__iew?: string[];
                /** @description Filter: text doesn't end with, ignoring the case */
                text__niew?: string[];
                /** @description Filter: text matches the (POSIX) regular expression */
                text__regex?: string[];
                /** @description Filter: text matches the (POSIX) regular expression, ignoring the case */
                text__iregex?: string[];
                /** @description Filter: text is empty (null, or an empty string) for true, isn't for false */
                text__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseRule"];
                    "application/xml": components["schemas"]["ListResponseRule"];
                    "application/yaml": components["schemas"]["ListResponseRule"];
                };
            };
        };
    };
    createRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["RuleRequest"];
                "application/xml": components["schemas"]["RuleRequest"];
                "application/yaml": components["schemas"]["RuleRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Rule"];
                    "application/xml": components["schemas"]["Rule"];
                    "application/yaml": components["schemas"]["Rule"];
                };
            };
        };
    };
    getRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Rule"];
                    "application/xml": components["schemas"]["Rule"];
                    "application/yaml": components["schemas"]["Rule"];
                };
            };
        };
    };
    replaceRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["RuleRequest"];
                "application/xml": components["schemas"]["RuleRequest"];
                "application/yaml": components["schemas"]["RuleRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Rule"];
                    "application/xml": components["schemas"]["Rule"];
                    "application/yaml": components["schemas"]["Rule"];
                };
            };
        };
    };
    deleteRule: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Rule ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Rule"];
                    "application/xml": components["schemas"]["Rule"];
                    "application/yaml": components["schemas"]["Rule"];
                };
            };
        };
    };
    listExemptions: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: expirationDate */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: rule equal to */
                rule?: number[];
                /** @description Filter: rule not equal to */
                rule__n?: number[];
                /** @description Filter: rule lower than */
                rule__lt?: number[];
                /** @description Filter: rule lower than or equal to */
                rule__lte?: number[];
                /** @description Filter: rule greater than */
                rule__gt?: number[];
                /** @description Filter: rule greater than or equal to */
                rule__gte?: number[];
                /** @description Filter: rule is empty (null, or an empty string) for true, isn't for false */
                rule__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: expirationDate equal to */
                expirationDate?: string[];
                /** @description Filter: expirationDate not equal to */
                expirationDate__n?: string[];
                /** @description Filter: expirationDate lower than */
                expirationDate__lt?: string[];
                /** @description Filter: expirationDate lower than or equal to */
                expirationDate__lte?: string[];
                /** @description Filter: expirationDate greater than */
                expirationDate__gt?: string[];
                /** @description Filter: expirationDate greater than or equal to */
                expirationDate__gte?: string[];
                /** @description Filter: expirationDate is empty (null, or an empty string) for true, isn't for false */
                expirationDate__empty?: boolean;
            };
            header?: never;
            path: {
                /** @description Rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseExemption"];
                    "application/xml": components["schemas"]["ListResponseExemption"];
                    "application/yaml": components["schemas"]["ListResponseExemption"];
                };
            };
        };
    };
    replaceExemptions: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: expirationDate */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: rule equal to */
                rule?: number[];
                /** @description Filter: rule not equal to */
                rule__n?: number[];
                /** @description Filter: rule lower than */
                rule__lt?: number[];
                /** @description Filter: rule lower than or equal to */
                rule__lte?: number[];
                /** @description Filter: rule greater than */
                rule__gt?: number[];
                /** @description Filter: rule greater than or equal to */
                rule__gte?: number[];
                /** @description Filter: rule is empty (null, or an empty string) for true, isn't for false */
                rule__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: expirationDate equal to */
                expirationDate?: string[];
                /** @description Filter: expirationDate not equal to */
                expirationDate__n?: string[];
                /** @description Filter: expirationDate lower than */
                expirationDate__lt?: string[];
                /** @description Filter: expirationDate lower than or equal to */
                expirationDate__lte?: string[];
                /** @description Filter: expirationDate greater than */
                expirationDate__gt?: string[];
                /** @description Filter: expirationDate greater than or equal to */
                expirationDate__gte?: string[];
                /** @description Filter: expirationDate is empty (null, or an empty string) for true, isn't for false */
                expirationDate__empty?: boolean;
            };
            header?: never;
            path: {
                /** @description Rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ExemptionsRequest"];
                "application/xml": components["schemas"]["ExemptionsRequest"];
                "application/yaml": components["schemas"]["ExemptionsRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseExemption"];
                    "application/xml": components["schemas"]["ListResponseExemption"];
                    "application/yaml": components["schemas"]["ListResponseExemption"];
                };
            };
        };
    };
    testRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["RuleTestRequest"];
                "application/xml": components["schemas"]["RuleTestRequest"];
                "application/yaml": components["schemas"]["RuleTestRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RuleTestResult"];
                    "application/xml": components["schemas"]["RuleTestResult"];
                    "application/yaml": components["schemas"]["RuleTestResult"];
                };
            };
        };
    };
    listSoftwareRules: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, priority, driver, family, softwareVersion, partNumber, level */
                sort?: string;
                /** @description Free text search; fields: family, softwareVersion, partNumber */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: priority equal to */
                priority?: number[];
                /** @description Filter: priority not equal to */
                priority__n?: number[];
                /** @description Filter: priority lower than */
                priority__lt?: number[];
                /** @description Filter: priority lower than or equal to */
                priority__lte?: number[];
                /** @description Filter: priority greater than */
                priority__gt?: number[];
                /** @description Filter: priority greater than or equal to */
                priority__gte?: number[];
                /** @description Filter: priority is empty (null, or an empty string) for true, isn't for false */
                priority__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: group lower than */
                group__lt?: number[];
                /** @description Filter: group lower than or equal to */
                group__lte?: number[];
                /** @description Filter: group greater than */
                group__gt?: number[];
                /** @description Filter: group greater than or equal to */
                group__gte?: number[];
                /** @description Filter: group is empty (null, or an empty string) for true, isn't for false */
                group__empty?: boolean;
                /** @description Filter: driver equal to */
                driver?: string[];
                /** @description Filter: driver not equal to */
                driver__n?: string[];
                /** @description Filter: driver equal to, ignoring the case */
                driver__ie?: string[];
                /** @description Filter: driver not equal to, ignoring the case */
                driver__nie?: string[];
                /** @description Filter: driver contains, ignoring the case */
                driver__ic?: string[];
                /** @description Filter: driver doesn't contain, ignoring the case */
                driver__nic?: string[];
                /** @description Filter: driver starts with, ignoring the case */
                driver__isw?: string[];
                /** @description Filter: driver doesn't start with, ignoring the case */
                driver__nisw?: string[];
                /** @description Filter: driver ends with, ignoring the case */
                driver__iew?: string[];
                /** @description Filter: driver doesn't end with, ignoring the case */
                driver__niew?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression */
                driver__regex?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression, ignoring the case */
                driver__iregex?: string[];
                /** @description Filter: driver is empty (null, or an empty string) for true, isn't for false */
                driver__empty?: boolean;
                /** @description Filter: family equal to */
                family?: string[];
                /** @description Filter: family not equal to */
                family__n?: string[];
                /** @description Filter: family equal to, ignoring the case */
                family__ie?: string[];
                /** @description Filter: family not equal to, ignoring the case */
                family__nie?: string[];
                /** @description Filter: family contains, ignoring the case */
                family__ic?: string[];
                /** @description Filter: family doesn't contain, ignoring the case */
                family__nic?: string[];
                /** @description Filter: family starts with, ignoring the case */
                family__isw?: string[];
                /** @description Filter: family doesn't start with, ignoring the case */
                family__nisw?: string[];
                /** @description Filter: family ends with, ignoring the case */
                family__iew?: string[];
                /** @description Filter: family doesn't end with, ignoring the case */
                family__niew?: string[];
                /** @description Filter: family matches the (POSIX) regular expression */
                family__regex?: string[];
                /** @description Filter: family matches the (POSIX) regular expression, ignoring the case */
                family__iregex?: string[];
                /** @description Filter: family is empty (null, or an empty string) for true, isn't for false */
                family__empty?: boolean;
                /** @description Filter: familyRegExp equal to */
                familyRegExp?: boolean[];
                /** @description Filter: softwareVersion equal to */
                softwareVersion?: string[];
                /** @description Filter: softwareVersion not equal to */
                softwareVersion__n?: string[];
                /** @description Filter: softwareVersion equal to, ignoring the case */
                softwareVersion__ie?: string[];
                /** @description Filter: softwareVersion not equal to, ignoring the case */
                softwareVersion__nie?: string[];
                /** @description Filter: softwareVersion contains, ignoring the case */
                softwareVersion__ic?: string[];
                /** @description Filter: softwareVersion doesn't contain, ignoring the case */
                softwareVersion__nic?: string[];
                /** @description Filter: softwareVersion starts with, ignoring the case */
                softwareVersion__isw?: string[];
                /** @description Filter: softwareVersion doesn't start with, ignoring the case */
                softwareVersion__nisw?: string[];
                /** @description Filter: softwareVersion ends with, ignoring the case */
                softwareVersion__iew?: string[];
                /** @description Filter: softwareVersion doesn't end with, ignoring the case */
                softwareVersion__niew?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression */
                softwareVersion__regex?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression, ignoring the case */
                softwareVersion__iregex?: string[];
                /** @description Filter: softwareVersion is empty (null, or an empty string) for true, isn't for false */
                softwareVersion__empty?: boolean;
                /** @description Filter: softwareVersionRegExp equal to */
                softwareVersionRegExp?: boolean[];
                /** @description Filter: partNumber equal to */
                partNumber?: string[];
                /** @description Filter: partNumber not equal to */
                partNumber__n?: string[];
                /** @description Filter: partNumber equal to, ignoring the case */
                partNumber__ie?: string[];
                /** @description Filter: partNumber not equal to, ignoring the case */
                partNumber__nie?: string[];
                /** @description Filter: partNumber contains, ignoring the case */
                partNumber__ic?: string[];
                /** @description Filter: partNumber doesn't contain, ignoring the case */
                partNumber__nic?: string[];
                /** @description Filter: partNumber starts with, ignoring the case */
                partNumber__isw?: string[];
                /** @description Filter: partNumber doesn't start with, ignoring the case */
                partNumber__nisw?: string[];
                /** @description Filter: partNumber ends with, ignoring the case */
                partNumber__iew?: string[];
                /** @description Filter: partNumber doesn't end with, ignoring the case */
                partNumber__niew?: string[];
                /** @description Filter: partNumber matches the (POSIX) regular expression */
                partNumber__regex?: string[];
                /** @description Filter: partNumber matches the (POSIX) regular expression, ignoring the case */
                partNumber__iregex?: string[];
                /** @description Filter: partNumber is empty (null, or an empty string) for true, isn't for false */
                partNumber__empty?: boolean;
                /** @description Filter: partNumberRegExp equal to */
                partNumberRegExp?: boolean[];
                /** @description Filter: level equal to */
                level?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: level not equal to */
                level__n?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: level is empty (null, or an empty string) for true, isn't for false */
                level__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseSoftwareRule"];
                    "application/xml": components["schemas"]["ListResponseSoftwareRule"];
                    "application/yaml": components["schemas"]["ListResponseSoftwareRule"];
                };
            };
        };
    };
    createSoftwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SoftwareRuleRequest"];
                "application/xml": components["schemas"]["SoftwareRuleRequest"];
                "application/yaml": components["schemas"]["SoftwareRuleRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SoftwareRule"];
                    "application/xml": components["schemas"]["SoftwareRule"];
                    "application/yaml": components["schemas"]["SoftwareRule"];
                };
            };
        };
    };
    getSoftwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Software rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SoftwareRule"];
                    "application/xml": components["schemas"]["SoftwareRule"];
                    "application/yaml": components["schemas"]["SoftwareRule"];
                };
            };
        };
    };
    replaceSoftwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Software rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SoftwareRuleRequest"];
                "application/xml": components["schemas"]["SoftwareRuleRequest"];
                "application/yaml": components["schemas"]["SoftwareRuleRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SoftwareRule"];
                    "application/xml": components["schemas"]["SoftwareRule"];
                    "application/yaml": components["schemas"]["SoftwareRule"];
                };
            };
        };
    };
    deleteSoftwareRule: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Software rule ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateSoftwareRule: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Software rule ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SoftwareRule"];
                    "application/xml": components["schemas"]["SoftwareRule"];
                    "application/yaml": components["schemas"]["SoftwareRule"];
                };
            };
        };
    };
    getConfig: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Configuration ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Config"];
                    "application/xml": components["schemas"]["Config"];
                    "application/yaml": components["schemas"]["Config"];
                };
            };
        };
    };
    getConfigDiff: {
        parameters: {
            query?: {
                /** @description ID of the configuration to compare with (the original one) */
                with?: string;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Configuration ID (the revised one) */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConfigDiff"];
                    "application/xml": components["schemas"]["ConfigDiff"];
                    "application/yaml": components["schemas"]["ConfigDiff"];
                };
            };
        };
    };
    getConfigItem: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Configuration ID */
                id: number;
                /** @description Name of the item (an attribute of the configuration) */
                item: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/plain": unknown;
                    "application/octet-stream": unknown;
                };
            };
        };
    };
    getConfigStats: {
        parameters: {
            query?: {
                /** @description Free text search (as in the list) */
                q?: string;
                /** @description Fields to count by (up to two), comma-separated; fields: device, author */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets; fields: changeDate */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: author matches the (POSIX) regular expression */
                author__regex?: string[];
                /** @description Filter: author matches the (POSIX) regular expression, ignoring the case */
                author__iregex?: string[];
                /** @description Filter: author is empty (null, or an empty string) for true, isn't for false */
                author__empty?: boolean;
                /** @description Filter: domain equal to */
                domain?: number[];
                /** @description Filter: domain not equal to */
                domain__n?: number[];
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Stats"];
                    "application/xml": components["schemas"]["Stats"];
                    "application/yaml": components["schemas"]["Stats"];
                };
            };
        };
    };
    listConfigs: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, changeDate, author */
                sort?: string;
                /** @description Free text search; fields: author */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: author matches the (POSIX) regular expression */
                author__regex?: string[];
                /** @description Filter: author matches the (POSIX) regular expression, ignoring the case */
                author__iregex?: string[];
                /** @description Filter: author is empty (null, or an empty string) for true, isn't for false */
                author__empty?: boolean;
                /** @description Filter: domain equal to */
                domain?: number[];
                /** @description Filter: domain not equal to */
                domain__n?: number[];
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseConfig"];
                    "application/xml": components["schemas"]["ListResponseConfig"];
                    "application/yaml": components["schemas"]["ListResponseConfig"];
                };
            };
        };
    };
    listDeviceConfigs: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, changeDate, author */
                sort?: string;
                /** @description Free text search; fields: author */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: author matches the (POSIX) regular expression */
                author__regex?: string[];
                /** @description Filter: author matches the (POSIX) regular expression, ignoring the case */
                author__iregex?: string[];
                /** @description Filter: author is empty (null, or an empty string) for true, isn't for false */
                author__empty?: boolean;
                /** @description Filter: domain equal to */
                domain?: number[];
                /** @description Filter: domain not equal to */
                domain__n?: number[];
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseConfig"];
                    "application/xml": components["schemas"]["ListResponseConfig"];
                    "application/yaml": components["schemas"]["ListResponseConfig"];
                };
            };
        };
    };
    listCredentialSets: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, type, username, changeDate */
                sort?: string;
                /** @description Free text search; fields: name, username */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("HTTP" | "SNMP_V1" | "SNMP_V2C" | "SNMP_V3" | "SSH" | "SSH_KEY" | "TELNET")[];
                /** @description Filter: type not equal to */
                type__n?: ("HTTP" | "SNMP_V1" | "SNMP_V2C" | "SNMP_V3" | "SSH" | "SSH_KEY" | "TELNET")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: mgmtDomain equal to */
                mgmtDomain?: number[];
                /** @description Filter: mgmtDomain not equal to */
                mgmtDomain__n?: number[];
                /** @description Filter: mgmtDomain lower than */
                mgmtDomain__lt?: number[];
                /** @description Filter: mgmtDomain lower than or equal to */
                mgmtDomain__lte?: number[];
                /** @description Filter: mgmtDomain greater than */
                mgmtDomain__gt?: number[];
                /** @description Filter: mgmtDomain greater than or equal to */
                mgmtDomain__gte?: number[];
                /** @description Filter: mgmtDomain is empty (null, or an empty string) for true, isn't for false */
                mgmtDomain__empty?: boolean;
                /** @description Filter: deviceSpecific equal to */
                deviceSpecific?: boolean[];
                /** @description Filter: username equal to */
                username?: string[];
                /** @description Filter: username not equal to */
                username__n?: string[];
                /** @description Filter: username equal to, ignoring the case */
                username__ie?: string[];
                /** @description Filter: username not equal to, ignoring the case */
                username__nie?: string[];
                /** @description Filter: username contains, ignoring the case */
                username__ic?: string[];
                /** @description Filter: username doesn't contain, ignoring the case */
                username__nic?: string[];
                /** @description Filter: username starts with, ignoring the case */
                username__isw?: string[];
                /** @description Filter: username doesn't start with, ignoring the case */
                username__nisw?: string[];
                /** @description Filter: username ends with, ignoring the case */
                username__iew?: string[];
                /** @description Filter: username doesn't end with, ignoring the case */
                username__niew?: string[];
                /** @description Filter: username matches the (POSIX) regular expression */
                username__regex?: string[];
                /** @description Filter: username matches the (POSIX) regular expression, ignoring the case */
                username__iregex?: string[];
                /** @description Filter: username is empty (null, or an empty string) for true, isn't for false */
                username__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseCredentialSet"];
                    "application/xml": components["schemas"]["ListResponseCredentialSet"];
                    "application/yaml": components["schemas"]["ListResponseCredentialSet"];
                };
            };
        };
    };
    createCredentialSet: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["CredentialSetRequest"];
                "application/xml": components["schemas"]["CredentialSetRequest"];
                "application/yaml": components["schemas"]["CredentialSetRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CredentialSet"];
                    "application/xml": components["schemas"]["CredentialSet"];
                    "application/yaml": components["schemas"]["CredentialSet"];
                };
            };
        };
    };
    getCredentialSet: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Credential set ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CredentialSet"];
                    "application/xml": components["schemas"]["CredentialSet"];
                    "application/yaml": components["schemas"]["CredentialSet"];
                };
            };
        };
    };
    replaceCredentialSet: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Credential set ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["CredentialSetRequest"];
                "application/xml": components["schemas"]["CredentialSetRequest"];
                "application/yaml": components["schemas"]["CredentialSetRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CredentialSet"];
                    "application/xml": components["schemas"]["CredentialSet"];
                    "application/yaml": components["schemas"]["CredentialSet"];
                };
            };
        };
    };
    deleteCredentialSet: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Credential set ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateCredentialSet: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Credential set ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CredentialSet"];
                    "application/xml": components["schemas"]["CredentialSet"];
                    "application/yaml": components["schemas"]["CredentialSet"];
                };
            };
        };
    };
    listDeviceFamilies: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: driver, family, deviceCount */
                sort?: string;
                /** @description Free text search; fields: driver, family */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: driver equal to */
                driver?: string[];
                /** @description Filter: driver not equal to */
                driver__n?: string[];
                /** @description Filter: driver equal to, ignoring the case */
                driver__ie?: string[];
                /** @description Filter: driver not equal to, ignoring the case */
                driver__nie?: string[];
                /** @description Filter: driver contains, ignoring the case */
                driver__ic?: string[];
                /** @description Filter: driver doesn't contain, ignoring the case */
                driver__nic?: string[];
                /** @description Filter: driver starts with, ignoring the case */
                driver__isw?: string[];
                /** @description Filter: driver doesn't start with, ignoring the case */
                driver__nisw?: string[];
                /** @description Filter: driver ends with, ignoring the case */
                driver__iew?: string[];
                /** @description Filter: driver doesn't end with, ignoring the case */
                driver__niew?: string[];
                /** @description Filter: family equal to */
                family?: string[];
                /** @description Filter: family not equal to */
                family__n?: string[];
                /** @description Filter: family equal to, ignoring the case */
                family__ie?: string[];
                /** @description Filter: family not equal to, ignoring the case */
                family__nie?: string[];
                /** @description Filter: family contains, ignoring the case */
                family__ic?: string[];
                /** @description Filter: family doesn't contain, ignoring the case */
                family__nic?: string[];
                /** @description Filter: family starts with, ignoring the case */
                family__isw?: string[];
                /** @description Filter: family doesn't start with, ignoring the case */
                family__nisw?: string[];
                /** @description Filter: family ends with, ignoring the case */
                family__iew?: string[];
                /** @description Filter: family doesn't end with, ignoring the case */
                family__niew?: string[];
                /** @description Filter: deviceCount equal to */
                deviceCount?: number[];
                /** @description Filter: deviceCount not equal to */
                deviceCount__n?: number[];
                /** @description Filter: deviceCount lower than */
                deviceCount__lt?: number[];
                /** @description Filter: deviceCount lower than or equal to */
                deviceCount__lte?: number[];
                /** @description Filter: deviceCount greater than */
                deviceCount__gt?: number[];
                /** @description Filter: deviceCount greater than or equal to */
                deviceCount__gte?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDeviceFamily"];
                    "application/xml": components["schemas"]["ListResponseDeviceFamily"];
                    "application/yaml": components["schemas"]["ListResponseDeviceFamily"];
                };
            };
        };
    };
    listDeviceInterfaces: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, description, vrf, virtualDevice, enabled, level3 */
                sort?: string;
                /** @description Free text search; fields: name, description */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: description equal to */
                description?: string[];
                /** @description Filter: description not equal to */
                description__n?: string[];
                /** @description Filter: description equal to, ignoring the case */
                description__ie?: string[];
                /** @description Filter: description not equal to, ignoring the case */
                description__nie?: string[];
                /** @description Filter: description contains, ignoring the case */
                description__ic?: string[];
                /** @description Filter: description doesn't contain, ignoring the case */
                description__nic?: string[];
                /** @description Filter: description starts with, ignoring the case */
                description__isw?: string[];
                /** @description Filter: description doesn't start with, ignoring the case */
                description__nisw?: string[];
                /** @description Filter: description ends with, ignoring the case */
                description__iew?: string[];
                /** @description Filter: description doesn't end with, ignoring the case */
                description__niew?: string[];
                /** @description Filter: description matches the (POSIX) regular expression */
                description__regex?: string[];
                /** @description Filter: description matches the (POSIX) regular expression, ignoring the case */
                description__iregex?: string[];
                /** @description Filter: description is empty (null, or an empty string) for true, isn't for false */
                description__empty?: boolean;
                /** @description Filter: vrf equal to */
                vrf?: string[];
                /** @description Filter: vrf not equal to */
                vrf__n?: string[];
                /** @description Filter: vrf equal to, ignoring the case */
                vrf__ie?: string[];
                /** @description Filter: vrf not equal to, ignoring the case */
                vrf__nie?: string[];
                /** @description Filter: vrf contains, ignoring the case */
                vrf__ic?: string[];
                /** @description Filter: vrf doesn't contain, ignoring the case */
                vrf__nic?: string[];
                /** @description Filter: vrf starts with, ignoring the case */
                vrf__isw?: string[];
                /** @description Filter: vrf doesn't start with, ignoring the case */
                vrf__nisw?: string[];
                /** @description Filter: vrf ends with, ignoring the case */
                vrf__iew?: string[];
                /** @description Filter: vrf doesn't end with, ignoring the case */
                vrf__niew?: string[];
                /** @description Filter: vrf matches the (POSIX) regular expression */
                vrf__regex?: string[];
                /** @description Filter: vrf matches the (POSIX) regular expression, ignoring the case */
                vrf__iregex?: string[];
                /** @description Filter: vrf is empty (null, or an empty string) for true, isn't for false */
                vrf__empty?: boolean;
                /** @description Filter: virtualDevice equal to */
                virtualDevice?: string[];
                /** @description Filter: virtualDevice not equal to */
                virtualDevice__n?: string[];
                /** @description Filter: virtualDevice equal to, ignoring the case */
                virtualDevice__ie?: string[];
                /** @description Filter: virtualDevice not equal to, ignoring the case */
                virtualDevice__nie?: string[];
                /** @description Filter: virtualDevice contains, ignoring the case */
                virtualDevice__ic?: string[];
                /** @description Filter: virtualDevice doesn't contain, ignoring the case */
                virtualDevice__nic?: string[];
                /** @description Filter: virtualDevice starts with, ignoring the case */
                virtualDevice__isw?: string[];
                /** @description Filter: virtualDevice doesn't start with, ignoring the case */
                virtualDevice__nisw?: string[];
                /** @description Filter: virtualDevice ends with, ignoring the case */
                virtualDevice__iew?: string[];
                /** @description Filter: virtualDevice doesn't end with, ignoring the case */
                virtualDevice__niew?: string[];
                /** @description Filter: virtualDevice matches the (POSIX) regular expression */
                virtualDevice__regex?: string[];
                /** @description Filter: virtualDevice matches the (POSIX) regular expression, ignoring the case */
                virtualDevice__iregex?: string[];
                /** @description Filter: virtualDevice is empty (null, or an empty string) for true, isn't for false */
                virtualDevice__empty?: boolean;
                /** @description Filter: enabled equal to */
                enabled?: boolean[];
                /** @description Filter: level3 equal to */
                level3?: boolean[];
                /** @description Filter: macAddress equal to */
                macAddress?: string[];
                /** @description Filter: macAddress not equal to */
                macAddress__n?: string[];
                /** @description Filter: ipAddress equal to */
                ipAddress?: string[];
                /** @description Filter: ipAddress not equal to */
                ipAddress__n?: string[];
                /** @description Filter: ipAddress IP address within the given subnet (10.0.0.0/8) */
                ipAddress__net?: string[];
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDeviceInterface"];
                    "application/xml": components["schemas"]["ListResponseDeviceInterface"];
                    "application/yaml": components["schemas"]["ListResponseDeviceInterface"];
                };
            };
        };
    };
    listDeviceModules: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, slot, partNumber, serialNumber, firstSeenDate, lastSeenDate, removed */
                sort?: string;
                /** @description Free text search; fields: slot, partNumber, serialNumber */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: slot equal to */
                slot?: string[];
                /** @description Filter: slot not equal to */
                slot__n?: string[];
                /** @description Filter: slot equal to, ignoring the case */
                slot__ie?: string[];
                /** @description Filter: slot not equal to, ignoring the case */
                slot__nie?: string[];
                /** @description Filter: slot contains, ignoring the case */
                slot__ic?: string[];
                /** @description Filter: slot doesn't contain, ignoring the case */
                slot__nic?: string[];
                /** @description Filter: slot starts with, ignoring the case */
                slot__isw?: string[];
                /** @description Filter: slot doesn't start with, ignoring the case */
                slot__nisw?: string[];
                /** @description Filter: slot ends with, ignoring the case */
                slot__iew?: string[];
                /** @description Filter: slot doesn't end with, ignoring the case */
                slot__niew?: string[];
                /** @description Filter: slot matches the (POSIX) regular expression */
                slot__regex?: string[];
                /** @description Filter: slot matches the (POSIX) regular expression, ignoring the case */
                slot__iregex?: string[];
                /** @description Filter: slot is empty (null, or an empty string) for true, isn't for false */
                slot__empty?: boolean;
                /** @description Filter: partNumber equal to */
                partNumber?: string[];
                /** @description Filter: partNumber not equal to */
                partNumber__n?: string[];
                /** @description Filter: partNumber equal to, ignoring the case */
                partNumber__ie?: string[];
                /** @description Filter: partNumber not equal to, ignoring the case */
                partNumber__nie?: string[];
                /** @description Filter: partNumber contains, ignoring the case */
                partNumber__ic?: string[];
                /** @description Filter: partNumber doesn't contain, ignoring the case */
                partNumber__nic?: string[];
                /** @description Filter: partNumber starts with, ignoring the case */
                partNumber__isw?: string[];
                /** @description Filter: partNumber doesn't start with, ignoring the case */
                partNumber__nisw?: string[];
                /** @description Filter: partNumber ends with, ignoring the case */
                partNumber__iew?: string[];
                /** @description Filter: partNumber doesn't end with, ignoring the case */
                partNumber__niew?: string[];
                /** @description Filter: partNumber matches the (POSIX) regular expression */
                partNumber__regex?: string[];
                /** @description Filter: partNumber matches the (POSIX) regular expression, ignoring the case */
                partNumber__iregex?: string[];
                /** @description Filter: partNumber is empty (null, or an empty string) for true, isn't for false */
                partNumber__empty?: boolean;
                /** @description Filter: serialNumber equal to */
                serialNumber?: string[];
                /** @description Filter: serialNumber not equal to */
                serialNumber__n?: string[];
                /** @description Filter: serialNumber equal to, ignoring the case */
                serialNumber__ie?: string[];
                /** @description Filter: serialNumber not equal to, ignoring the case */
                serialNumber__nie?: string[];
                /** @description Filter: serialNumber contains, ignoring the case */
                serialNumber__ic?: string[];
                /** @description Filter: serialNumber doesn't contain, ignoring the case */
                serialNumber__nic?: string[];
                /** @description Filter: serialNumber starts with, ignoring the case */
                serialNumber__isw?: string[];
                /** @description Filter: serialNumber doesn't start with, ignoring the case */
                serialNumber__nisw?: string[];
                /** @description Filter: serialNumber ends with, ignoring the case */
                serialNumber__iew?: string[];
                /** @description Filter: serialNumber doesn't end with, ignoring the case */
                serialNumber__niew?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression */
                serialNumber__regex?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression, ignoring the case */
                serialNumber__iregex?: string[];
                /** @description Filter: serialNumber is empty (null, or an empty string) for true, isn't for false */
                serialNumber__empty?: boolean;
                /** @description Filter: firstSeenDate equal to */
                firstSeenDate?: string[];
                /** @description Filter: firstSeenDate not equal to */
                firstSeenDate__n?: string[];
                /** @description Filter: firstSeenDate lower than */
                firstSeenDate__lt?: string[];
                /** @description Filter: firstSeenDate lower than or equal to */
                firstSeenDate__lte?: string[];
                /** @description Filter: firstSeenDate greater than */
                firstSeenDate__gt?: string[];
                /** @description Filter: firstSeenDate greater than or equal to */
                firstSeenDate__gte?: string[];
                /** @description Filter: firstSeenDate is empty (null, or an empty string) for true, isn't for false */
                firstSeenDate__empty?: boolean;
                /** @description Filter: lastSeenDate equal to */
                lastSeenDate?: string[];
                /** @description Filter: lastSeenDate not equal to */
                lastSeenDate__n?: string[];
                /** @description Filter: lastSeenDate lower than */
                lastSeenDate__lt?: string[];
                /** @description Filter: lastSeenDate lower than or equal to */
                lastSeenDate__lte?: string[];
                /** @description Filter: lastSeenDate greater than */
                lastSeenDate__gt?: string[];
                /** @description Filter: lastSeenDate greater than or equal to */
                lastSeenDate__gte?: string[];
                /** @description Filter: lastSeenDate is empty (null, or an empty string) for true, isn't for false */
                lastSeenDate__empty?: boolean;
                /** @description Filter: removed equal to */
                removed?: boolean[];
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDeviceModule"];
                    "application/xml": components["schemas"]["ListResponseDeviceModule"];
                    "application/yaml": components["schemas"]["ListResponseDeviceModule"];
                };
            };
        };
    };
    listDevices: {
        parameters: {
            query?: {
                /** @description Device search query, in the Netshot query language */
                query?: string;
                /** @description Excel export only: the extra sheets of the workbook, among interfaces, inventory, inventoryHistory (with the removed modules), locations, compliance, groups and driverAttributes */
                include?: string;
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, mgmtAddress, status, family, driver, networkClass, softwareVersion, softwareLevel, serialNumber, location, contact, creator, createdDate, changeDate, eolDate, eosDate, lastSnapshotSuccessDate, lastSnapshotFailureDate */
                sort?: string;
                /** @description Free text search; fields: name, mgmtAddress, family, softwareVersion, serialNumber, location, contact, comments */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: mgmtAddress equal to */
                mgmtAddress?: string[];
                /** @description Filter: mgmtAddress not equal to */
                mgmtAddress__n?: string[];
                /** @description Filter: mgmtAddress equal to, ignoring the case */
                mgmtAddress__ie?: string[];
                /** @description Filter: mgmtAddress not equal to, ignoring the case */
                mgmtAddress__nie?: string[];
                /** @description Filter: mgmtAddress contains, ignoring the case */
                mgmtAddress__ic?: string[];
                /** @description Filter: mgmtAddress doesn't contain, ignoring the case */
                mgmtAddress__nic?: string[];
                /** @description Filter: mgmtAddress starts with, ignoring the case */
                mgmtAddress__isw?: string[];
                /** @description Filter: mgmtAddress doesn't start with, ignoring the case */
                mgmtAddress__nisw?: string[];
                /** @description Filter: mgmtAddress ends with, ignoring the case */
                mgmtAddress__iew?: string[];
                /** @description Filter: mgmtAddress doesn't end with, ignoring the case */
                mgmtAddress__niew?: string[];
                /** @description Filter: mgmtAddress matches the (POSIX) regular expression */
                mgmtAddress__regex?: string[];
                /** @description Filter: mgmtAddress matches the (POSIX) regular expression, ignoring the case */
                mgmtAddress__iregex?: string[];
                /** @description Filter: mgmtAddress is empty (null, or an empty string) for true, isn't for false */
                mgmtAddress__empty?: boolean;
                /** @description Filter: status equal to */
                status?: ("DISABLED" | "INPRODUCTION" | "PREPRODUCTION")[];
                /** @description Filter: status not equal to */
                status__n?: ("DISABLED" | "INPRODUCTION" | "PREPRODUCTION")[];
                /** @description Filter: status is empty (null, or an empty string) for true, isn't for false */
                status__empty?: boolean;
                /** @description Filter: mgmtDomain equal to */
                mgmtDomain?: number[];
                /** @description Filter: mgmtDomain not equal to */
                mgmtDomain__n?: number[];
                /** @description Filter: mgmtDomain lower than */
                mgmtDomain__lt?: number[];
                /** @description Filter: mgmtDomain lower than or equal to */
                mgmtDomain__lte?: number[];
                /** @description Filter: mgmtDomain greater than */
                mgmtDomain__gt?: number[];
                /** @description Filter: mgmtDomain greater than or equal to */
                mgmtDomain__gte?: number[];
                /** @description Filter: mgmtDomain is empty (null, or an empty string) for true, isn't for false */
                mgmtDomain__empty?: boolean;
                /** @description Filter: family equal to */
                family?: string[];
                /** @description Filter: family not equal to */
                family__n?: string[];
                /** @description Filter: family equal to, ignoring the case */
                family__ie?: string[];
                /** @description Filter: family not equal to, ignoring the case */
                family__nie?: string[];
                /** @description Filter: family contains, ignoring the case */
                family__ic?: string[];
                /** @description Filter: family doesn't contain, ignoring the case */
                family__nic?: string[];
                /** @description Filter: family starts with, ignoring the case */
                family__isw?: string[];
                /** @description Filter: family doesn't start with, ignoring the case */
                family__nisw?: string[];
                /** @description Filter: family ends with, ignoring the case */
                family__iew?: string[];
                /** @description Filter: family doesn't end with, ignoring the case */
                family__niew?: string[];
                /** @description Filter: family matches the (POSIX) regular expression */
                family__regex?: string[];
                /** @description Filter: family matches the (POSIX) regular expression, ignoring the case */
                family__iregex?: string[];
                /** @description Filter: family is empty (null, or an empty string) for true, isn't for false */
                family__empty?: boolean;
                /** @description Filter: driver equal to */
                driver?: string[];
                /** @description Filter: driver not equal to */
                driver__n?: string[];
                /** @description Filter: driver equal to, ignoring the case */
                driver__ie?: string[];
                /** @description Filter: driver not equal to, ignoring the case */
                driver__nie?: string[];
                /** @description Filter: driver contains, ignoring the case */
                driver__ic?: string[];
                /** @description Filter: driver doesn't contain, ignoring the case */
                driver__nic?: string[];
                /** @description Filter: driver starts with, ignoring the case */
                driver__isw?: string[];
                /** @description Filter: driver doesn't start with, ignoring the case */
                driver__nisw?: string[];
                /** @description Filter: driver ends with, ignoring the case */
                driver__iew?: string[];
                /** @description Filter: driver doesn't end with, ignoring the case */
                driver__niew?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression */
                driver__regex?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression, ignoring the case */
                driver__iregex?: string[];
                /** @description Filter: driver is empty (null, or an empty string) for true, isn't for false */
                driver__empty?: boolean;
                /** @description Filter: networkClass equal to */
                networkClass?: ("FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER" | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY")[];
                /** @description Filter: networkClass not equal to */
                networkClass__n?: ("FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER" | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY")[];
                /** @description Filter: networkClass is empty (null, or an empty string) for true, isn't for false */
                networkClass__empty?: boolean;
                /** @description Filter: softwareVersion equal to */
                softwareVersion?: string[];
                /** @description Filter: softwareVersion not equal to */
                softwareVersion__n?: string[];
                /** @description Filter: softwareVersion equal to, ignoring the case */
                softwareVersion__ie?: string[];
                /** @description Filter: softwareVersion not equal to, ignoring the case */
                softwareVersion__nie?: string[];
                /** @description Filter: softwareVersion contains, ignoring the case */
                softwareVersion__ic?: string[];
                /** @description Filter: softwareVersion doesn't contain, ignoring the case */
                softwareVersion__nic?: string[];
                /** @description Filter: softwareVersion starts with, ignoring the case */
                softwareVersion__isw?: string[];
                /** @description Filter: softwareVersion doesn't start with, ignoring the case */
                softwareVersion__nisw?: string[];
                /** @description Filter: softwareVersion ends with, ignoring the case */
                softwareVersion__iew?: string[];
                /** @description Filter: softwareVersion doesn't end with, ignoring the case */
                softwareVersion__niew?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression */
                softwareVersion__regex?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression, ignoring the case */
                softwareVersion__iregex?: string[];
                /** @description Filter: softwareVersion is empty (null, or an empty string) for true, isn't for false */
                softwareVersion__empty?: boolean;
                /** @description Filter: softwareLevel equal to */
                softwareLevel?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: softwareLevel not equal to */
                softwareLevel__n?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: softwareLevel is empty (null, or an empty string) for true, isn't for false */
                softwareLevel__empty?: boolean;
                /** @description Filter: serialNumber equal to */
                serialNumber?: string[];
                /** @description Filter: serialNumber not equal to */
                serialNumber__n?: string[];
                /** @description Filter: serialNumber equal to, ignoring the case */
                serialNumber__ie?: string[];
                /** @description Filter: serialNumber not equal to, ignoring the case */
                serialNumber__nie?: string[];
                /** @description Filter: serialNumber contains, ignoring the case */
                serialNumber__ic?: string[];
                /** @description Filter: serialNumber doesn't contain, ignoring the case */
                serialNumber__nic?: string[];
                /** @description Filter: serialNumber starts with, ignoring the case */
                serialNumber__isw?: string[];
                /** @description Filter: serialNumber doesn't start with, ignoring the case */
                serialNumber__nisw?: string[];
                /** @description Filter: serialNumber ends with, ignoring the case */
                serialNumber__iew?: string[];
                /** @description Filter: serialNumber doesn't end with, ignoring the case */
                serialNumber__niew?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression */
                serialNumber__regex?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression, ignoring the case */
                serialNumber__iregex?: string[];
                /** @description Filter: serialNumber is empty (null, or an empty string) for true, isn't for false */
                serialNumber__empty?: boolean;
                /** @description Filter: location equal to */
                location?: string[];
                /** @description Filter: location not equal to */
                location__n?: string[];
                /** @description Filter: location equal to, ignoring the case */
                location__ie?: string[];
                /** @description Filter: location not equal to, ignoring the case */
                location__nie?: string[];
                /** @description Filter: location contains, ignoring the case */
                location__ic?: string[];
                /** @description Filter: location doesn't contain, ignoring the case */
                location__nic?: string[];
                /** @description Filter: location starts with, ignoring the case */
                location__isw?: string[];
                /** @description Filter: location doesn't start with, ignoring the case */
                location__nisw?: string[];
                /** @description Filter: location ends with, ignoring the case */
                location__iew?: string[];
                /** @description Filter: location doesn't end with, ignoring the case */
                location__niew?: string[];
                /** @description Filter: location matches the (POSIX) regular expression */
                location__regex?: string[];
                /** @description Filter: location matches the (POSIX) regular expression, ignoring the case */
                location__iregex?: string[];
                /** @description Filter: location is empty (null, or an empty string) for true, isn't for false */
                location__empty?: boolean;
                /** @description Filter: contact equal to */
                contact?: string[];
                /** @description Filter: contact not equal to */
                contact__n?: string[];
                /** @description Filter: contact equal to, ignoring the case */
                contact__ie?: string[];
                /** @description Filter: contact not equal to, ignoring the case */
                contact__nie?: string[];
                /** @description Filter: contact contains, ignoring the case */
                contact__ic?: string[];
                /** @description Filter: contact doesn't contain, ignoring the case */
                contact__nic?: string[];
                /** @description Filter: contact starts with, ignoring the case */
                contact__isw?: string[];
                /** @description Filter: contact doesn't start with, ignoring the case */
                contact__nisw?: string[];
                /** @description Filter: contact ends with, ignoring the case */
                contact__iew?: string[];
                /** @description Filter: contact doesn't end with, ignoring the case */
                contact__niew?: string[];
                /** @description Filter: contact matches the (POSIX) regular expression */
                contact__regex?: string[];
                /** @description Filter: contact matches the (POSIX) regular expression, ignoring the case */
                contact__iregex?: string[];
                /** @description Filter: contact is empty (null, or an empty string) for true, isn't for false */
                contact__empty?: boolean;
                /** @description Filter: comments equal to */
                comments?: string[];
                /** @description Filter: comments not equal to */
                comments__n?: string[];
                /** @description Filter: comments equal to, ignoring the case */
                comments__ie?: string[];
                /** @description Filter: comments not equal to, ignoring the case */
                comments__nie?: string[];
                /** @description Filter: comments contains, ignoring the case */
                comments__ic?: string[];
                /** @description Filter: comments doesn't contain, ignoring the case */
                comments__nic?: string[];
                /** @description Filter: comments starts with, ignoring the case */
                comments__isw?: string[];
                /** @description Filter: comments doesn't start with, ignoring the case */
                comments__nisw?: string[];
                /** @description Filter: comments ends with, ignoring the case */
                comments__iew?: string[];
                /** @description Filter: comments doesn't end with, ignoring the case */
                comments__niew?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression */
                comments__regex?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression, ignoring the case */
                comments__iregex?: string[];
                /** @description Filter: comments is empty (null, or an empty string) for true, isn't for false */
                comments__empty?: boolean;
                /** @description Filter: creator equal to */
                creator?: string[];
                /** @description Filter: creator not equal to */
                creator__n?: string[];
                /** @description Filter: creator equal to, ignoring the case */
                creator__ie?: string[];
                /** @description Filter: creator not equal to, ignoring the case */
                creator__nie?: string[];
                /** @description Filter: creator contains, ignoring the case */
                creator__ic?: string[];
                /** @description Filter: creator doesn't contain, ignoring the case */
                creator__nic?: string[];
                /** @description Filter: creator starts with, ignoring the case */
                creator__isw?: string[];
                /** @description Filter: creator doesn't start with, ignoring the case */
                creator__nisw?: string[];
                /** @description Filter: creator ends with, ignoring the case */
                creator__iew?: string[];
                /** @description Filter: creator doesn't end with, ignoring the case */
                creator__niew?: string[];
                /** @description Filter: creator matches the (POSIX) regular expression */
                creator__regex?: string[];
                /** @description Filter: creator matches the (POSIX) regular expression, ignoring the case */
                creator__iregex?: string[];
                /** @description Filter: creator is empty (null, or an empty string) for true, isn't for false */
                creator__empty?: boolean;
                /** @description Filter: cachedIpAddress equal to */
                cachedIpAddress?: string[];
                /** @description Filter: cachedIpAddress not equal to */
                cachedIpAddress__n?: string[];
                /** @description Filter: cachedIpAddress is empty (null, or an empty string) for true, isn't for false */
                cachedIpAddress__empty?: boolean;
                /** @description Filter: cachedIpAddress IP address within the given subnet (10.0.0.0/8) */
                cachedIpAddress__net?: string[];
                /** @description Filter: createdDate equal to */
                createdDate?: string[];
                /** @description Filter: createdDate not equal to */
                createdDate__n?: string[];
                /** @description Filter: createdDate lower than */
                createdDate__lt?: string[];
                /** @description Filter: createdDate lower than or equal to */
                createdDate__lte?: string[];
                /** @description Filter: createdDate greater than */
                createdDate__gt?: string[];
                /** @description Filter: createdDate greater than or equal to */
                createdDate__gte?: string[];
                /** @description Filter: createdDate is empty (null, or an empty string) for true, isn't for false */
                createdDate__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: eolDate equal to */
                eolDate?: string[];
                /** @description Filter: eolDate not equal to */
                eolDate__n?: string[];
                /** @description Filter: eolDate lower than */
                eolDate__lt?: string[];
                /** @description Filter: eolDate lower than or equal to */
                eolDate__lte?: string[];
                /** @description Filter: eolDate greater than */
                eolDate__gt?: string[];
                /** @description Filter: eolDate greater than or equal to */
                eolDate__gte?: string[];
                /** @description Filter: eolDate is empty (null, or an empty string) for true, isn't for false */
                eolDate__empty?: boolean;
                /** @description Filter: eosDate equal to */
                eosDate?: string[];
                /** @description Filter: eosDate not equal to */
                eosDate__n?: string[];
                /** @description Filter: eosDate lower than */
                eosDate__lt?: string[];
                /** @description Filter: eosDate lower than or equal to */
                eosDate__lte?: string[];
                /** @description Filter: eosDate greater than */
                eosDate__gt?: string[];
                /** @description Filter: eosDate greater than or equal to */
                eosDate__gte?: string[];
                /** @description Filter: eosDate is empty (null, or an empty string) for true, isn't for false */
                eosDate__empty?: boolean;
                /** @description Filter: lastSnapshotSuccessDate equal to */
                lastSnapshotSuccessDate?: string[];
                /** @description Filter: lastSnapshotSuccessDate not equal to */
                lastSnapshotSuccessDate__n?: string[];
                /** @description Filter: lastSnapshotSuccessDate lower than */
                lastSnapshotSuccessDate__lt?: string[];
                /** @description Filter: lastSnapshotSuccessDate lower than or equal to */
                lastSnapshotSuccessDate__lte?: string[];
                /** @description Filter: lastSnapshotSuccessDate greater than */
                lastSnapshotSuccessDate__gt?: string[];
                /** @description Filter: lastSnapshotSuccessDate greater than or equal to */
                lastSnapshotSuccessDate__gte?: string[];
                /** @description Filter: lastSnapshotSuccessDate is empty (null, or an empty string) for true, isn't for false */
                lastSnapshotSuccessDate__empty?: boolean;
                /** @description Filter: lastSnapshotFailureDate equal to */
                lastSnapshotFailureDate?: string[];
                /** @description Filter: lastSnapshotFailureDate not equal to */
                lastSnapshotFailureDate__n?: string[];
                /** @description Filter: lastSnapshotFailureDate lower than */
                lastSnapshotFailureDate__lt?: string[];
                /** @description Filter: lastSnapshotFailureDate lower than or equal to */
                lastSnapshotFailureDate__lte?: string[];
                /** @description Filter: lastSnapshotFailureDate greater than */
                lastSnapshotFailureDate__gt?: string[];
                /** @description Filter: lastSnapshotFailureDate greater than or equal to */
                lastSnapshotFailureDate__gte?: string[];
                /** @description Filter: lastSnapshotFailureDate is empty (null, or an empty string) for true, isn't for false */
                lastSnapshotFailureDate__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: tag equal to */
                tag?: string[];
                /** @description Filter: tag not equal to */
                tag__n?: string[];
                /** @description Filter: tag equal to, ignoring the case */
                tag__ie?: string[];
                /** @description Filter: tag not equal to, ignoring the case */
                tag__nie?: string[];
                /** @description Filter: tag contains, ignoring the case */
                tag__ic?: string[];
                /** @description Filter: tag doesn't contain, ignoring the case */
                tag__nic?: string[];
                /** @description Filter: tag starts with, ignoring the case */
                tag__isw?: string[];
                /** @description Filter: tag doesn't start with, ignoring the case */
                tag__nisw?: string[];
                /** @description Filter: tag is empty (null, or an empty string) for true, isn't for false */
                tag__empty?: boolean;
                /** @description Filter: compliant equal to */
                compliant?: boolean[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDevice"];
                    "application/xml": components["schemas"]["ListResponseDevice"];
                    "application/yaml": components["schemas"]["ListResponseDevice"];
                };
            };
        };
    };
    addDevice: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DeviceCreationRequest"];
                "application/xml": components["schemas"]["DeviceCreationRequest"];
                "application/yaml": components["schemas"]["DeviceCreationRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    checkCompliance: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ComplianceCheckRequest"];
                "application/xml": components["schemas"]["ComplianceCheckRequest"];
                "application/yaml": components["schemas"]["ComplianceCheckRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    getDevice: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Device"];
                    "application/xml": components["schemas"]["Device"];
                    "application/yaml": components["schemas"]["Device"];
                };
            };
        };
    };
    replaceDevice: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DeviceRequest"];
                "application/xml": components["schemas"]["DeviceRequest"];
                "application/yaml": components["schemas"]["DeviceRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Device"];
                    "application/xml": components["schemas"]["Device"];
                    "application/yaml": components["schemas"]["Device"];
                };
            };
        };
    };
    deleteDevice: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateDevice: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Device"];
                    "application/xml": components["schemas"]["Device"];
                    "application/yaml": components["schemas"]["Device"];
                };
            };
        };
    };
    getDeviceStats: {
        parameters: {
            query?: {
                /** @description Device search query, in the Netshot query language */
                query?: string;
                /** @description Free text search (as in the list) */
                q?: string;
                /** @description Fields to count by (up to two), comma-separated; fields: status, mgmtDomain, family, driver, networkClass, softwareVersion, softwareLevel, location, eolDate, eosDate, group, tag, compliant */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets; fields: createdDate, changeDate, eolDate, eosDate, lastSnapshotSuccessDate, lastSnapshotFailureDate */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: mgmtAddress equal to */
                mgmtAddress?: string[];
                /** @description Filter: mgmtAddress not equal to */
                mgmtAddress__n?: string[];
                /** @description Filter: mgmtAddress equal to, ignoring the case */
                mgmtAddress__ie?: string[];
                /** @description Filter: mgmtAddress not equal to, ignoring the case */
                mgmtAddress__nie?: string[];
                /** @description Filter: mgmtAddress contains, ignoring the case */
                mgmtAddress__ic?: string[];
                /** @description Filter: mgmtAddress doesn't contain, ignoring the case */
                mgmtAddress__nic?: string[];
                /** @description Filter: mgmtAddress starts with, ignoring the case */
                mgmtAddress__isw?: string[];
                /** @description Filter: mgmtAddress doesn't start with, ignoring the case */
                mgmtAddress__nisw?: string[];
                /** @description Filter: mgmtAddress ends with, ignoring the case */
                mgmtAddress__iew?: string[];
                /** @description Filter: mgmtAddress doesn't end with, ignoring the case */
                mgmtAddress__niew?: string[];
                /** @description Filter: mgmtAddress matches the (POSIX) regular expression */
                mgmtAddress__regex?: string[];
                /** @description Filter: mgmtAddress matches the (POSIX) regular expression, ignoring the case */
                mgmtAddress__iregex?: string[];
                /** @description Filter: mgmtAddress is empty (null, or an empty string) for true, isn't for false */
                mgmtAddress__empty?: boolean;
                /** @description Filter: status equal to */
                status?: ("DISABLED" | "INPRODUCTION" | "PREPRODUCTION")[];
                /** @description Filter: status not equal to */
                status__n?: ("DISABLED" | "INPRODUCTION" | "PREPRODUCTION")[];
                /** @description Filter: status is empty (null, or an empty string) for true, isn't for false */
                status__empty?: boolean;
                /** @description Filter: mgmtDomain equal to */
                mgmtDomain?: number[];
                /** @description Filter: mgmtDomain not equal to */
                mgmtDomain__n?: number[];
                /** @description Filter: mgmtDomain lower than */
                mgmtDomain__lt?: number[];
                /** @description Filter: mgmtDomain lower than or equal to */
                mgmtDomain__lte?: number[];
                /** @description Filter: mgmtDomain greater than */
                mgmtDomain__gt?: number[];
                /** @description Filter: mgmtDomain greater than or equal to */
                mgmtDomain__gte?: number[];
                /** @description Filter: mgmtDomain is empty (null, or an empty string) for true, isn't for false */
                mgmtDomain__empty?: boolean;
                /** @description Filter: family equal to */
                family?: string[];
                /** @description Filter: family not equal to */
                family__n?: string[];
                /** @description Filter: family equal to, ignoring the case */
                family__ie?: string[];
                /** @description Filter: family not equal to, ignoring the case */
                family__nie?: string[];
                /** @description Filter: family contains, ignoring the case */
                family__ic?: string[];
                /** @description Filter: family doesn't contain, ignoring the case */
                family__nic?: string[];
                /** @description Filter: family starts with, ignoring the case */
                family__isw?: string[];
                /** @description Filter: family doesn't start with, ignoring the case */
                family__nisw?: string[];
                /** @description Filter: family ends with, ignoring the case */
                family__iew?: string[];
                /** @description Filter: family doesn't end with, ignoring the case */
                family__niew?: string[];
                /** @description Filter: family matches the (POSIX) regular expression */
                family__regex?: string[];
                /** @description Filter: family matches the (POSIX) regular expression, ignoring the case */
                family__iregex?: string[];
                /** @description Filter: family is empty (null, or an empty string) for true, isn't for false */
                family__empty?: boolean;
                /** @description Filter: driver equal to */
                driver?: string[];
                /** @description Filter: driver not equal to */
                driver__n?: string[];
                /** @description Filter: driver equal to, ignoring the case */
                driver__ie?: string[];
                /** @description Filter: driver not equal to, ignoring the case */
                driver__nie?: string[];
                /** @description Filter: driver contains, ignoring the case */
                driver__ic?: string[];
                /** @description Filter: driver doesn't contain, ignoring the case */
                driver__nic?: string[];
                /** @description Filter: driver starts with, ignoring the case */
                driver__isw?: string[];
                /** @description Filter: driver doesn't start with, ignoring the case */
                driver__nisw?: string[];
                /** @description Filter: driver ends with, ignoring the case */
                driver__iew?: string[];
                /** @description Filter: driver doesn't end with, ignoring the case */
                driver__niew?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression */
                driver__regex?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression, ignoring the case */
                driver__iregex?: string[];
                /** @description Filter: driver is empty (null, or an empty string) for true, isn't for false */
                driver__empty?: boolean;
                /** @description Filter: networkClass equal to */
                networkClass?: ("FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER" | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY")[];
                /** @description Filter: networkClass not equal to */
                networkClass__n?: ("FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER" | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY")[];
                /** @description Filter: networkClass is empty (null, or an empty string) for true, isn't for false */
                networkClass__empty?: boolean;
                /** @description Filter: softwareVersion equal to */
                softwareVersion?: string[];
                /** @description Filter: softwareVersion not equal to */
                softwareVersion__n?: string[];
                /** @description Filter: softwareVersion equal to, ignoring the case */
                softwareVersion__ie?: string[];
                /** @description Filter: softwareVersion not equal to, ignoring the case */
                softwareVersion__nie?: string[];
                /** @description Filter: softwareVersion contains, ignoring the case */
                softwareVersion__ic?: string[];
                /** @description Filter: softwareVersion doesn't contain, ignoring the case */
                softwareVersion__nic?: string[];
                /** @description Filter: softwareVersion starts with, ignoring the case */
                softwareVersion__isw?: string[];
                /** @description Filter: softwareVersion doesn't start with, ignoring the case */
                softwareVersion__nisw?: string[];
                /** @description Filter: softwareVersion ends with, ignoring the case */
                softwareVersion__iew?: string[];
                /** @description Filter: softwareVersion doesn't end with, ignoring the case */
                softwareVersion__niew?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression */
                softwareVersion__regex?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression, ignoring the case */
                softwareVersion__iregex?: string[];
                /** @description Filter: softwareVersion is empty (null, or an empty string) for true, isn't for false */
                softwareVersion__empty?: boolean;
                /** @description Filter: softwareLevel equal to */
                softwareLevel?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: softwareLevel not equal to */
                softwareLevel__n?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: softwareLevel is empty (null, or an empty string) for true, isn't for false */
                softwareLevel__empty?: boolean;
                /** @description Filter: serialNumber equal to */
                serialNumber?: string[];
                /** @description Filter: serialNumber not equal to */
                serialNumber__n?: string[];
                /** @description Filter: serialNumber equal to, ignoring the case */
                serialNumber__ie?: string[];
                /** @description Filter: serialNumber not equal to, ignoring the case */
                serialNumber__nie?: string[];
                /** @description Filter: serialNumber contains, ignoring the case */
                serialNumber__ic?: string[];
                /** @description Filter: serialNumber doesn't contain, ignoring the case */
                serialNumber__nic?: string[];
                /** @description Filter: serialNumber starts with, ignoring the case */
                serialNumber__isw?: string[];
                /** @description Filter: serialNumber doesn't start with, ignoring the case */
                serialNumber__nisw?: string[];
                /** @description Filter: serialNumber ends with, ignoring the case */
                serialNumber__iew?: string[];
                /** @description Filter: serialNumber doesn't end with, ignoring the case */
                serialNumber__niew?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression */
                serialNumber__regex?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression, ignoring the case */
                serialNumber__iregex?: string[];
                /** @description Filter: serialNumber is empty (null, or an empty string) for true, isn't for false */
                serialNumber__empty?: boolean;
                /** @description Filter: location equal to */
                location?: string[];
                /** @description Filter: location not equal to */
                location__n?: string[];
                /** @description Filter: location equal to, ignoring the case */
                location__ie?: string[];
                /** @description Filter: location not equal to, ignoring the case */
                location__nie?: string[];
                /** @description Filter: location contains, ignoring the case */
                location__ic?: string[];
                /** @description Filter: location doesn't contain, ignoring the case */
                location__nic?: string[];
                /** @description Filter: location starts with, ignoring the case */
                location__isw?: string[];
                /** @description Filter: location doesn't start with, ignoring the case */
                location__nisw?: string[];
                /** @description Filter: location ends with, ignoring the case */
                location__iew?: string[];
                /** @description Filter: location doesn't end with, ignoring the case */
                location__niew?: string[];
                /** @description Filter: location matches the (POSIX) regular expression */
                location__regex?: string[];
                /** @description Filter: location matches the (POSIX) regular expression, ignoring the case */
                location__iregex?: string[];
                /** @description Filter: location is empty (null, or an empty string) for true, isn't for false */
                location__empty?: boolean;
                /** @description Filter: contact equal to */
                contact?: string[];
                /** @description Filter: contact not equal to */
                contact__n?: string[];
                /** @description Filter: contact equal to, ignoring the case */
                contact__ie?: string[];
                /** @description Filter: contact not equal to, ignoring the case */
                contact__nie?: string[];
                /** @description Filter: contact contains, ignoring the case */
                contact__ic?: string[];
                /** @description Filter: contact doesn't contain, ignoring the case */
                contact__nic?: string[];
                /** @description Filter: contact starts with, ignoring the case */
                contact__isw?: string[];
                /** @description Filter: contact doesn't start with, ignoring the case */
                contact__nisw?: string[];
                /** @description Filter: contact ends with, ignoring the case */
                contact__iew?: string[];
                /** @description Filter: contact doesn't end with, ignoring the case */
                contact__niew?: string[];
                /** @description Filter: contact matches the (POSIX) regular expression */
                contact__regex?: string[];
                /** @description Filter: contact matches the (POSIX) regular expression, ignoring the case */
                contact__iregex?: string[];
                /** @description Filter: contact is empty (null, or an empty string) for true, isn't for false */
                contact__empty?: boolean;
                /** @description Filter: comments equal to */
                comments?: string[];
                /** @description Filter: comments not equal to */
                comments__n?: string[];
                /** @description Filter: comments equal to, ignoring the case */
                comments__ie?: string[];
                /** @description Filter: comments not equal to, ignoring the case */
                comments__nie?: string[];
                /** @description Filter: comments contains, ignoring the case */
                comments__ic?: string[];
                /** @description Filter: comments doesn't contain, ignoring the case */
                comments__nic?: string[];
                /** @description Filter: comments starts with, ignoring the case */
                comments__isw?: string[];
                /** @description Filter: comments doesn't start with, ignoring the case */
                comments__nisw?: string[];
                /** @description Filter: comments ends with, ignoring the case */
                comments__iew?: string[];
                /** @description Filter: comments doesn't end with, ignoring the case */
                comments__niew?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression */
                comments__regex?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression, ignoring the case */
                comments__iregex?: string[];
                /** @description Filter: comments is empty (null, or an empty string) for true, isn't for false */
                comments__empty?: boolean;
                /** @description Filter: creator equal to */
                creator?: string[];
                /** @description Filter: creator not equal to */
                creator__n?: string[];
                /** @description Filter: creator equal to, ignoring the case */
                creator__ie?: string[];
                /** @description Filter: creator not equal to, ignoring the case */
                creator__nie?: string[];
                /** @description Filter: creator contains, ignoring the case */
                creator__ic?: string[];
                /** @description Filter: creator doesn't contain, ignoring the case */
                creator__nic?: string[];
                /** @description Filter: creator starts with, ignoring the case */
                creator__isw?: string[];
                /** @description Filter: creator doesn't start with, ignoring the case */
                creator__nisw?: string[];
                /** @description Filter: creator ends with, ignoring the case */
                creator__iew?: string[];
                /** @description Filter: creator doesn't end with, ignoring the case */
                creator__niew?: string[];
                /** @description Filter: creator matches the (POSIX) regular expression */
                creator__regex?: string[];
                /** @description Filter: creator matches the (POSIX) regular expression, ignoring the case */
                creator__iregex?: string[];
                /** @description Filter: creator is empty (null, or an empty string) for true, isn't for false */
                creator__empty?: boolean;
                /** @description Filter: cachedIpAddress equal to */
                cachedIpAddress?: string[];
                /** @description Filter: cachedIpAddress not equal to */
                cachedIpAddress__n?: string[];
                /** @description Filter: cachedIpAddress is empty (null, or an empty string) for true, isn't for false */
                cachedIpAddress__empty?: boolean;
                /** @description Filter: cachedIpAddress IP address within the given subnet (10.0.0.0/8) */
                cachedIpAddress__net?: string[];
                /** @description Filter: createdDate equal to */
                createdDate?: string[];
                /** @description Filter: createdDate not equal to */
                createdDate__n?: string[];
                /** @description Filter: createdDate lower than */
                createdDate__lt?: string[];
                /** @description Filter: createdDate lower than or equal to */
                createdDate__lte?: string[];
                /** @description Filter: createdDate greater than */
                createdDate__gt?: string[];
                /** @description Filter: createdDate greater than or equal to */
                createdDate__gte?: string[];
                /** @description Filter: createdDate is empty (null, or an empty string) for true, isn't for false */
                createdDate__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: eolDate equal to */
                eolDate?: string[];
                /** @description Filter: eolDate not equal to */
                eolDate__n?: string[];
                /** @description Filter: eolDate lower than */
                eolDate__lt?: string[];
                /** @description Filter: eolDate lower than or equal to */
                eolDate__lte?: string[];
                /** @description Filter: eolDate greater than */
                eolDate__gt?: string[];
                /** @description Filter: eolDate greater than or equal to */
                eolDate__gte?: string[];
                /** @description Filter: eolDate is empty (null, or an empty string) for true, isn't for false */
                eolDate__empty?: boolean;
                /** @description Filter: eosDate equal to */
                eosDate?: string[];
                /** @description Filter: eosDate not equal to */
                eosDate__n?: string[];
                /** @description Filter: eosDate lower than */
                eosDate__lt?: string[];
                /** @description Filter: eosDate lower than or equal to */
                eosDate__lte?: string[];
                /** @description Filter: eosDate greater than */
                eosDate__gt?: string[];
                /** @description Filter: eosDate greater than or equal to */
                eosDate__gte?: string[];
                /** @description Filter: eosDate is empty (null, or an empty string) for true, isn't for false */
                eosDate__empty?: boolean;
                /** @description Filter: lastSnapshotSuccessDate equal to */
                lastSnapshotSuccessDate?: string[];
                /** @description Filter: lastSnapshotSuccessDate not equal to */
                lastSnapshotSuccessDate__n?: string[];
                /** @description Filter: lastSnapshotSuccessDate lower than */
                lastSnapshotSuccessDate__lt?: string[];
                /** @description Filter: lastSnapshotSuccessDate lower than or equal to */
                lastSnapshotSuccessDate__lte?: string[];
                /** @description Filter: lastSnapshotSuccessDate greater than */
                lastSnapshotSuccessDate__gt?: string[];
                /** @description Filter: lastSnapshotSuccessDate greater than or equal to */
                lastSnapshotSuccessDate__gte?: string[];
                /** @description Filter: lastSnapshotSuccessDate is empty (null, or an empty string) for true, isn't for false */
                lastSnapshotSuccessDate__empty?: boolean;
                /** @description Filter: lastSnapshotFailureDate equal to */
                lastSnapshotFailureDate?: string[];
                /** @description Filter: lastSnapshotFailureDate not equal to */
                lastSnapshotFailureDate__n?: string[];
                /** @description Filter: lastSnapshotFailureDate lower than */
                lastSnapshotFailureDate__lt?: string[];
                /** @description Filter: lastSnapshotFailureDate lower than or equal to */
                lastSnapshotFailureDate__lte?: string[];
                /** @description Filter: lastSnapshotFailureDate greater than */
                lastSnapshotFailureDate__gt?: string[];
                /** @description Filter: lastSnapshotFailureDate greater than or equal to */
                lastSnapshotFailureDate__gte?: string[];
                /** @description Filter: lastSnapshotFailureDate is empty (null, or an empty string) for true, isn't for false */
                lastSnapshotFailureDate__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: tag equal to */
                tag?: string[];
                /** @description Filter: tag not equal to */
                tag__n?: string[];
                /** @description Filter: tag equal to, ignoring the case */
                tag__ie?: string[];
                /** @description Filter: tag not equal to, ignoring the case */
                tag__nie?: string[];
                /** @description Filter: tag contains, ignoring the case */
                tag__ic?: string[];
                /** @description Filter: tag doesn't contain, ignoring the case */
                tag__nic?: string[];
                /** @description Filter: tag starts with, ignoring the case */
                tag__isw?: string[];
                /** @description Filter: tag doesn't start with, ignoring the case */
                tag__nisw?: string[];
                /** @description Filter: tag is empty (null, or an empty string) for true, isn't for false */
                tag__empty?: boolean;
                /** @description Filter: compliant equal to */
                compliant?: boolean[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Stats"];
                    "application/xml": components["schemas"]["Stats"];
                    "application/yaml": components["schemas"]["Stats"];
                };
            };
        };
    };
    runDiagnostics: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DiagnosticsRunRequest"];
                "application/xml": components["schemas"]["DiagnosticsRunRequest"];
                "application/yaml": components["schemas"]["DiagnosticsRunRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    runScript: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ScriptRunRequest"];
                "application/xml": components["schemas"]["ScriptRunRequest"];
                "application/yaml": components["schemas"]["ScriptRunRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    takeSnapshot: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SnapshotRequest"];
                "application/xml": components["schemas"]["SnapshotRequest"];
                "application/yaml": components["schemas"]["SnapshotRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    listDeviceTags: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: name, deviceCount */
                sort?: string;
                /** @description Free text search; fields: name */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: deviceCount equal to */
                deviceCount?: number[];
                /** @description Filter: deviceCount not equal to */
                deviceCount__n?: number[];
                /** @description Filter: deviceCount lower than */
                deviceCount__lt?: number[];
                /** @description Filter: deviceCount lower than or equal to */
                deviceCount__lte?: number[];
                /** @description Filter: deviceCount greater than */
                deviceCount__gt?: number[];
                /** @description Filter: deviceCount greater than or equal to */
                deviceCount__gte?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDeviceTag"];
                    "application/xml": components["schemas"]["ListResponseDeviceTag"];
                    "application/yaml": components["schemas"]["ListResponseDeviceTag"];
                };
            };
        };
    };
    listDeviceTypes: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: name, description, author, version, priority */
                sort?: string;
                /** @description Free text search; fields: name, description, author */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: description equal to */
                description?: string[];
                /** @description Filter: description not equal to */
                description__n?: string[];
                /** @description Filter: description equal to, ignoring the case */
                description__ie?: string[];
                /** @description Filter: description not equal to, ignoring the case */
                description__nie?: string[];
                /** @description Filter: description contains, ignoring the case */
                description__ic?: string[];
                /** @description Filter: description doesn't contain, ignoring the case */
                description__nic?: string[];
                /** @description Filter: description starts with, ignoring the case */
                description__isw?: string[];
                /** @description Filter: description doesn't start with, ignoring the case */
                description__nisw?: string[];
                /** @description Filter: description ends with, ignoring the case */
                description__iew?: string[];
                /** @description Filter: description doesn't end with, ignoring the case */
                description__niew?: string[];
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: version equal to */
                version?: string[];
                /** @description Filter: version not equal to */
                version__n?: string[];
                /** @description Filter: version equal to, ignoring the case */
                version__ie?: string[];
                /** @description Filter: version not equal to, ignoring the case */
                version__nie?: string[];
                /** @description Filter: version contains, ignoring the case */
                version__ic?: string[];
                /** @description Filter: version doesn't contain, ignoring the case */
                version__nic?: string[];
                /** @description Filter: version starts with, ignoring the case */
                version__isw?: string[];
                /** @description Filter: version doesn't start with, ignoring the case */
                version__nisw?: string[];
                /** @description Filter: version ends with, ignoring the case */
                version__iew?: string[];
                /** @description Filter: version doesn't end with, ignoring the case */
                version__niew?: string[];
                /** @description Filter: priority equal to */
                priority?: number[];
                /** @description Filter: priority not equal to */
                priority__n?: number[];
                /** @description Filter: priority lower than */
                priority__lt?: number[];
                /** @description Filter: priority lower than or equal to */
                priority__lte?: number[];
                /** @description Filter: priority greater than */
                priority__gt?: number[];
                /** @description Filter: priority greater than or equal to */
                priority__gte?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDeviceType"];
                    "application/xml": components["schemas"]["ListResponseDeviceType"];
                    "application/yaml": components["schemas"]["ListResponseDeviceType"];
                };
            };
        };
    };
    reloadDeviceTypes: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    listPartNumbers: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: partNumber, moduleCount */
                sort?: string;
                /** @description Free text search; fields: partNumber */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: partNumber equal to */
                partNumber?: string[];
                /** @description Filter: partNumber not equal to */
                partNumber__n?: string[];
                /** @description Filter: partNumber equal to, ignoring the case */
                partNumber__ie?: string[];
                /** @description Filter: partNumber not equal to, ignoring the case */
                partNumber__nie?: string[];
                /** @description Filter: partNumber contains, ignoring the case */
                partNumber__ic?: string[];
                /** @description Filter: partNumber doesn't contain, ignoring the case */
                partNumber__nic?: string[];
                /** @description Filter: partNumber starts with, ignoring the case */
                partNumber__isw?: string[];
                /** @description Filter: partNumber doesn't start with, ignoring the case */
                partNumber__nisw?: string[];
                /** @description Filter: partNumber ends with, ignoring the case */
                partNumber__iew?: string[];
                /** @description Filter: partNumber doesn't end with, ignoring the case */
                partNumber__niew?: string[];
                /** @description Filter: moduleCount equal to */
                moduleCount?: number[];
                /** @description Filter: moduleCount not equal to */
                moduleCount__n?: number[];
                /** @description Filter: moduleCount lower than */
                moduleCount__lt?: number[];
                /** @description Filter: moduleCount lower than or equal to */
                moduleCount__lte?: number[];
                /** @description Filter: moduleCount greater than */
                moduleCount__gt?: number[];
                /** @description Filter: moduleCount greater than or equal to */
                moduleCount__gte?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponsePartNumber"];
                    "application/xml": components["schemas"]["ListResponsePartNumber"];
                    "application/yaml": components["schemas"]["ListResponsePartNumber"];
                };
            };
        };
    };
    listSoftwareVersions: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: version, deviceCount */
                sort?: string;
                /** @description Free text search; fields: version */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: version equal to */
                version?: string[];
                /** @description Filter: version not equal to */
                version__n?: string[];
                /** @description Filter: version equal to, ignoring the case */
                version__ie?: string[];
                /** @description Filter: version not equal to, ignoring the case */
                version__nie?: string[];
                /** @description Filter: version contains, ignoring the case */
                version__ic?: string[];
                /** @description Filter: version doesn't contain, ignoring the case */
                version__nic?: string[];
                /** @description Filter: version starts with, ignoring the case */
                version__isw?: string[];
                /** @description Filter: version doesn't start with, ignoring the case */
                version__nisw?: string[];
                /** @description Filter: version ends with, ignoring the case */
                version__iew?: string[];
                /** @description Filter: version doesn't end with, ignoring the case */
                version__niew?: string[];
                /** @description Filter: deviceCount equal to */
                deviceCount?: number[];
                /** @description Filter: deviceCount not equal to */
                deviceCount__n?: number[];
                /** @description Filter: deviceCount lower than */
                deviceCount__lt?: number[];
                /** @description Filter: deviceCount lower than or equal to */
                deviceCount__lte?: number[];
                /** @description Filter: deviceCount greater than */
                deviceCount__gt?: number[];
                /** @description Filter: deviceCount greater than or equal to */
                deviceCount__gte?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseSoftwareVersion"];
                    "application/xml": components["schemas"]["ListResponseSoftwareVersion"];
                    "application/yaml": components["schemas"]["ListResponseSoftwareVersion"];
                };
            };
        };
    };
    listDiagnostics: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, type, resultType, enabled, deviceDriver */
                sort?: string;
                /** @description Free text search; fields: name */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("JAVASCRIPT" | "PYTHON" | "SIMPLE")[];
                /** @description Filter: type not equal to */
                type__n?: ("JAVASCRIPT" | "PYTHON" | "SIMPLE")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: resultType equal to */
                resultType?: ("BINARY" | "LONGTEXT" | "NUMERIC" | "TEXT")[];
                /** @description Filter: resultType not equal to */
                resultType__n?: ("BINARY" | "LONGTEXT" | "NUMERIC" | "TEXT")[];
                /** @description Filter: resultType is empty (null, or an empty string) for true, isn't for false */
                resultType__empty?: boolean;
                /** @description Filter: enabled equal to */
                enabled?: boolean[];
                /** @description Filter: targetGroup equal to */
                targetGroup?: number[];
                /** @description Filter: targetGroup not equal to */
                targetGroup__n?: number[];
                /** @description Filter: targetGroup lower than */
                targetGroup__lt?: number[];
                /** @description Filter: targetGroup lower than or equal to */
                targetGroup__lte?: number[];
                /** @description Filter: targetGroup greater than */
                targetGroup__gt?: number[];
                /** @description Filter: targetGroup greater than or equal to */
                targetGroup__gte?: number[];
                /** @description Filter: targetGroup is empty (null, or an empty string) for true, isn't for false */
                targetGroup__empty?: boolean;
                /** @description Filter: deviceDriver equal to */
                deviceDriver?: string[];
                /** @description Filter: deviceDriver not equal to */
                deviceDriver__n?: string[];
                /** @description Filter: deviceDriver equal to, ignoring the case */
                deviceDriver__ie?: string[];
                /** @description Filter: deviceDriver not equal to, ignoring the case */
                deviceDriver__nie?: string[];
                /** @description Filter: deviceDriver contains, ignoring the case */
                deviceDriver__ic?: string[];
                /** @description Filter: deviceDriver doesn't contain, ignoring the case */
                deviceDriver__nic?: string[];
                /** @description Filter: deviceDriver starts with, ignoring the case */
                deviceDriver__isw?: string[];
                /** @description Filter: deviceDriver doesn't start with, ignoring the case */
                deviceDriver__nisw?: string[];
                /** @description Filter: deviceDriver ends with, ignoring the case */
                deviceDriver__iew?: string[];
                /** @description Filter: deviceDriver doesn't end with, ignoring the case */
                deviceDriver__niew?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression */
                deviceDriver__regex?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression, ignoring the case */
                deviceDriver__iregex?: string[];
                /** @description Filter: deviceDriver is empty (null, or an empty string) for true, isn't for false */
                deviceDriver__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDiagnostic"];
                    "application/xml": components["schemas"]["ListResponseDiagnostic"];
                    "application/yaml": components["schemas"]["ListResponseDiagnostic"];
                };
            };
        };
    };
    createDiagnostic: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DiagnosticRequest"];
                "application/xml": components["schemas"]["DiagnosticRequest"];
                "application/yaml": components["schemas"]["DiagnosticRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Diagnostic"];
                    "application/xml": components["schemas"]["Diagnostic"];
                    "application/yaml": components["schemas"]["Diagnostic"];
                };
            };
        };
    };
    getDiagnostic: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Diagnostic ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Diagnostic"];
                    "application/xml": components["schemas"]["Diagnostic"];
                    "application/yaml": components["schemas"]["Diagnostic"];
                };
            };
        };
    };
    replaceDiagnostic: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Diagnostic ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DiagnosticRequest"];
                "application/xml": components["schemas"]["DiagnosticRequest"];
                "application/yaml": components["schemas"]["DiagnosticRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Diagnostic"];
                    "application/xml": components["schemas"]["Diagnostic"];
                    "application/yaml": components["schemas"]["Diagnostic"];
                };
            };
        };
    };
    deleteDiagnostic: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Diagnostic ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateDiagnostic: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Diagnostic ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Diagnostic"];
                    "application/xml": components["schemas"]["Diagnostic"];
                    "application/yaml": components["schemas"]["Diagnostic"];
                };
            };
        };
    };
    listDiagnosticResults: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, diagnosticName, type, creationDate, lastCheckDate */
                sort?: string;
                /** @description Free text search; fields: diagnosticName */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: diagnostic equal to */
                diagnostic?: number[];
                /** @description Filter: diagnostic not equal to */
                diagnostic__n?: number[];
                /** @description Filter: diagnostic lower than */
                diagnostic__lt?: number[];
                /** @description Filter: diagnostic lower than or equal to */
                diagnostic__lte?: number[];
                /** @description Filter: diagnostic greater than */
                diagnostic__gt?: number[];
                /** @description Filter: diagnostic greater than or equal to */
                diagnostic__gte?: number[];
                /** @description Filter: diagnostic is empty (null, or an empty string) for true, isn't for false */
                diagnostic__empty?: boolean;
                /** @description Filter: diagnosticName equal to */
                diagnosticName?: string[];
                /** @description Filter: diagnosticName not equal to */
                diagnosticName__n?: string[];
                /** @description Filter: diagnosticName equal to, ignoring the case */
                diagnosticName__ie?: string[];
                /** @description Filter: diagnosticName not equal to, ignoring the case */
                diagnosticName__nie?: string[];
                /** @description Filter: diagnosticName contains, ignoring the case */
                diagnosticName__ic?: string[];
                /** @description Filter: diagnosticName doesn't contain, ignoring the case */
                diagnosticName__nic?: string[];
                /** @description Filter: diagnosticName starts with, ignoring the case */
                diagnosticName__isw?: string[];
                /** @description Filter: diagnosticName doesn't start with, ignoring the case */
                diagnosticName__nisw?: string[];
                /** @description Filter: diagnosticName ends with, ignoring the case */
                diagnosticName__iew?: string[];
                /** @description Filter: diagnosticName doesn't end with, ignoring the case */
                diagnosticName__niew?: string[];
                /** @description Filter: diagnosticName matches the (POSIX) regular expression */
                diagnosticName__regex?: string[];
                /** @description Filter: diagnosticName matches the (POSIX) regular expression, ignoring the case */
                diagnosticName__iregex?: string[];
                /** @description Filter: diagnosticName is empty (null, or an empty string) for true, isn't for false */
                diagnosticName__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("BINARY" | "LONGTEXT" | "NUMERIC" | "TEXT")[];
                /** @description Filter: type not equal to */
                type__n?: ("BINARY" | "LONGTEXT" | "NUMERIC" | "TEXT")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: creationDate equal to */
                creationDate?: string[];
                /** @description Filter: creationDate not equal to */
                creationDate__n?: string[];
                /** @description Filter: creationDate lower than */
                creationDate__lt?: string[];
                /** @description Filter: creationDate lower than or equal to */
                creationDate__lte?: string[];
                /** @description Filter: creationDate greater than */
                creationDate__gt?: string[];
                /** @description Filter: creationDate greater than or equal to */
                creationDate__gte?: string[];
                /** @description Filter: creationDate is empty (null, or an empty string) for true, isn't for false */
                creationDate__empty?: boolean;
                /** @description Filter: lastCheckDate equal to */
                lastCheckDate?: string[];
                /** @description Filter: lastCheckDate not equal to */
                lastCheckDate__n?: string[];
                /** @description Filter: lastCheckDate lower than */
                lastCheckDate__lt?: string[];
                /** @description Filter: lastCheckDate lower than or equal to */
                lastCheckDate__lte?: string[];
                /** @description Filter: lastCheckDate greater than */
                lastCheckDate__gt?: string[];
                /** @description Filter: lastCheckDate greater than or equal to */
                lastCheckDate__gte?: string[];
                /** @description Filter: lastCheckDate is empty (null, or an empty string) for true, isn't for false */
                lastCheckDate__empty?: boolean;
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDiagnosticResult"];
                    "application/xml": components["schemas"]["ListResponseDiagnosticResult"];
                    "application/yaml": components["schemas"]["ListResponseDiagnosticResult"];
                };
            };
        };
    };
    listDomains: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, changeDate */
                sort?: string;
                /** @description Free text search; fields: name, description */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: description equal to */
                description?: string[];
                /** @description Filter: description not equal to */
                description__n?: string[];
                /** @description Filter: description equal to, ignoring the case */
                description__ie?: string[];
                /** @description Filter: description not equal to, ignoring the case */
                description__nie?: string[];
                /** @description Filter: description contains, ignoring the case */
                description__ic?: string[];
                /** @description Filter: description doesn't contain, ignoring the case */
                description__nic?: string[];
                /** @description Filter: description starts with, ignoring the case */
                description__isw?: string[];
                /** @description Filter: description doesn't start with, ignoring the case */
                description__nisw?: string[];
                /** @description Filter: description ends with, ignoring the case */
                description__iew?: string[];
                /** @description Filter: description doesn't end with, ignoring the case */
                description__niew?: string[];
                /** @description Filter: description matches the (POSIX) regular expression */
                description__regex?: string[];
                /** @description Filter: description matches the (POSIX) regular expression, ignoring the case */
                description__iregex?: string[];
                /** @description Filter: description is empty (null, or an empty string) for true, isn't for false */
                description__empty?: boolean;
                /** @description Filter: server4Address equal to */
                server4Address?: string[];
                /** @description Filter: server4Address not equal to */
                server4Address__n?: string[];
                /** @description Filter: server4Address is empty (null, or an empty string) for true, isn't for false */
                server4Address__empty?: boolean;
                /** @description Filter: server4Address IP address within the given subnet (10.0.0.0/8) */
                server4Address__net?: string[];
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDomain"];
                    "application/xml": components["schemas"]["ListResponseDomain"];
                    "application/yaml": components["schemas"]["ListResponseDomain"];
                };
            };
        };
    };
    createDomain: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DomainRequest"];
                "application/xml": components["schemas"]["DomainRequest"];
                "application/yaml": components["schemas"]["DomainRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Domain"];
                    "application/xml": components["schemas"]["Domain"];
                    "application/yaml": components["schemas"]["Domain"];
                };
            };
        };
    };
    getDomain: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Domain ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Domain"];
                    "application/xml": components["schemas"]["Domain"];
                    "application/yaml": components["schemas"]["Domain"];
                };
            };
        };
    };
    replaceDomain: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Domain ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DomainRequest"];
                "application/xml": components["schemas"]["DomainRequest"];
                "application/yaml": components["schemas"]["DomainRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Domain"];
                    "application/xml": components["schemas"]["Domain"];
                    "application/yaml": components["schemas"]["Domain"];
                };
            };
        };
    };
    deleteDomain: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Domain ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateDomain: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Domain ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Domain"];
                    "application/xml": components["schemas"]["Domain"];
                    "application/yaml": components["schemas"]["Domain"];
                };
            };
        };
    };
    listGroups: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, folder, type, changeDate */
                sort?: string;
                /** @description Free text search; fields: name, folder, query */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: folder equal to */
                folder?: string[];
                /** @description Filter: folder not equal to */
                folder__n?: string[];
                /** @description Filter: folder equal to, ignoring the case */
                folder__ie?: string[];
                /** @description Filter: folder not equal to, ignoring the case */
                folder__nie?: string[];
                /** @description Filter: folder contains, ignoring the case */
                folder__ic?: string[];
                /** @description Filter: folder doesn't contain, ignoring the case */
                folder__nic?: string[];
                /** @description Filter: folder starts with, ignoring the case */
                folder__isw?: string[];
                /** @description Filter: folder doesn't start with, ignoring the case */
                folder__nisw?: string[];
                /** @description Filter: folder ends with, ignoring the case */
                folder__iew?: string[];
                /** @description Filter: folder doesn't end with, ignoring the case */
                folder__niew?: string[];
                /** @description Filter: folder matches the (POSIX) regular expression */
                folder__regex?: string[];
                /** @description Filter: folder matches the (POSIX) regular expression, ignoring the case */
                folder__iregex?: string[];
                /** @description Filter: folder is empty (null, or an empty string) for true, isn't for false */
                folder__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("STATIC" | "DYNAMIC")[];
                /** @description Filter: type not equal to */
                type__n?: ("STATIC" | "DYNAMIC")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: hiddenFromReports equal to */
                hiddenFromReports?: boolean[];
                /** @description Filter: query equal to */
                query?: string[];
                /** @description Filter: query not equal to */
                query__n?: string[];
                /** @description Filter: query equal to, ignoring the case */
                query__ie?: string[];
                /** @description Filter: query not equal to, ignoring the case */
                query__nie?: string[];
                /** @description Filter: query contains, ignoring the case */
                query__ic?: string[];
                /** @description Filter: query doesn't contain, ignoring the case */
                query__nic?: string[];
                /** @description Filter: query starts with, ignoring the case */
                query__isw?: string[];
                /** @description Filter: query doesn't start with, ignoring the case */
                query__nisw?: string[];
                /** @description Filter: query ends with, ignoring the case */
                query__iew?: string[];
                /** @description Filter: query doesn't end with, ignoring the case */
                query__niew?: string[];
                /** @description Filter: query matches the (POSIX) regular expression */
                query__regex?: string[];
                /** @description Filter: query matches the (POSIX) regular expression, ignoring the case */
                query__iregex?: string[];
                /** @description Filter: query is empty (null, or an empty string) for true, isn't for false */
                query__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseGroup"];
                    "application/xml": components["schemas"]["ListResponseGroup"];
                    "application/yaml": components["schemas"]["ListResponseGroup"];
                };
            };
        };
    };
    createGroup: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["GroupRequest"];
                "application/xml": components["schemas"]["GroupRequest"];
                "application/yaml": components["schemas"]["GroupRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Group"];
                    "application/xml": components["schemas"]["Group"];
                    "application/yaml": components["schemas"]["Group"];
                };
            };
        };
    };
    getGroup: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Group ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Group"];
                    "application/xml": components["schemas"]["Group"];
                    "application/yaml": components["schemas"]["Group"];
                };
            };
        };
    };
    replaceGroup: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Group ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["GroupRequest"];
                "application/xml": components["schemas"]["GroupRequest"];
                "application/yaml": components["schemas"]["GroupRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Group"];
                    "application/xml": components["schemas"]["Group"];
                    "application/yaml": components["schemas"]["Group"];
                };
            };
        };
    };
    deleteGroup: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Group ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateGroup: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Group ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Group"];
                    "application/xml": components["schemas"]["Group"];
                    "application/yaml": components["schemas"]["Group"];
                };
            };
        };
    };
    listGroupDevices: {
        parameters: {
            query?: {
                /** @description Device search query, in the Netshot query language */
                query?: string;
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, mgmtAddress, status, family, driver, networkClass, softwareVersion, softwareLevel, serialNumber, location, contact, creator, createdDate, changeDate, eolDate, eosDate, lastSnapshotSuccessDate, lastSnapshotFailureDate */
                sort?: string;
                /** @description Free text search; fields: name, mgmtAddress, family, softwareVersion, serialNumber, location, contact, comments */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: mgmtAddress equal to */
                mgmtAddress?: string[];
                /** @description Filter: mgmtAddress not equal to */
                mgmtAddress__n?: string[];
                /** @description Filter: mgmtAddress equal to, ignoring the case */
                mgmtAddress__ie?: string[];
                /** @description Filter: mgmtAddress not equal to, ignoring the case */
                mgmtAddress__nie?: string[];
                /** @description Filter: mgmtAddress contains, ignoring the case */
                mgmtAddress__ic?: string[];
                /** @description Filter: mgmtAddress doesn't contain, ignoring the case */
                mgmtAddress__nic?: string[];
                /** @description Filter: mgmtAddress starts with, ignoring the case */
                mgmtAddress__isw?: string[];
                /** @description Filter: mgmtAddress doesn't start with, ignoring the case */
                mgmtAddress__nisw?: string[];
                /** @description Filter: mgmtAddress ends with, ignoring the case */
                mgmtAddress__iew?: string[];
                /** @description Filter: mgmtAddress doesn't end with, ignoring the case */
                mgmtAddress__niew?: string[];
                /** @description Filter: mgmtAddress matches the (POSIX) regular expression */
                mgmtAddress__regex?: string[];
                /** @description Filter: mgmtAddress matches the (POSIX) regular expression, ignoring the case */
                mgmtAddress__iregex?: string[];
                /** @description Filter: mgmtAddress is empty (null, or an empty string) for true, isn't for false */
                mgmtAddress__empty?: boolean;
                /** @description Filter: status equal to */
                status?: ("DISABLED" | "INPRODUCTION" | "PREPRODUCTION")[];
                /** @description Filter: status not equal to */
                status__n?: ("DISABLED" | "INPRODUCTION" | "PREPRODUCTION")[];
                /** @description Filter: status is empty (null, or an empty string) for true, isn't for false */
                status__empty?: boolean;
                /** @description Filter: mgmtDomain equal to */
                mgmtDomain?: number[];
                /** @description Filter: mgmtDomain not equal to */
                mgmtDomain__n?: number[];
                /** @description Filter: mgmtDomain lower than */
                mgmtDomain__lt?: number[];
                /** @description Filter: mgmtDomain lower than or equal to */
                mgmtDomain__lte?: number[];
                /** @description Filter: mgmtDomain greater than */
                mgmtDomain__gt?: number[];
                /** @description Filter: mgmtDomain greater than or equal to */
                mgmtDomain__gte?: number[];
                /** @description Filter: mgmtDomain is empty (null, or an empty string) for true, isn't for false */
                mgmtDomain__empty?: boolean;
                /** @description Filter: family equal to */
                family?: string[];
                /** @description Filter: family not equal to */
                family__n?: string[];
                /** @description Filter: family equal to, ignoring the case */
                family__ie?: string[];
                /** @description Filter: family not equal to, ignoring the case */
                family__nie?: string[];
                /** @description Filter: family contains, ignoring the case */
                family__ic?: string[];
                /** @description Filter: family doesn't contain, ignoring the case */
                family__nic?: string[];
                /** @description Filter: family starts with, ignoring the case */
                family__isw?: string[];
                /** @description Filter: family doesn't start with, ignoring the case */
                family__nisw?: string[];
                /** @description Filter: family ends with, ignoring the case */
                family__iew?: string[];
                /** @description Filter: family doesn't end with, ignoring the case */
                family__niew?: string[];
                /** @description Filter: family matches the (POSIX) regular expression */
                family__regex?: string[];
                /** @description Filter: family matches the (POSIX) regular expression, ignoring the case */
                family__iregex?: string[];
                /** @description Filter: family is empty (null, or an empty string) for true, isn't for false */
                family__empty?: boolean;
                /** @description Filter: driver equal to */
                driver?: string[];
                /** @description Filter: driver not equal to */
                driver__n?: string[];
                /** @description Filter: driver equal to, ignoring the case */
                driver__ie?: string[];
                /** @description Filter: driver not equal to, ignoring the case */
                driver__nie?: string[];
                /** @description Filter: driver contains, ignoring the case */
                driver__ic?: string[];
                /** @description Filter: driver doesn't contain, ignoring the case */
                driver__nic?: string[];
                /** @description Filter: driver starts with, ignoring the case */
                driver__isw?: string[];
                /** @description Filter: driver doesn't start with, ignoring the case */
                driver__nisw?: string[];
                /** @description Filter: driver ends with, ignoring the case */
                driver__iew?: string[];
                /** @description Filter: driver doesn't end with, ignoring the case */
                driver__niew?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression */
                driver__regex?: string[];
                /** @description Filter: driver matches the (POSIX) regular expression, ignoring the case */
                driver__iregex?: string[];
                /** @description Filter: driver is empty (null, or an empty string) for true, isn't for false */
                driver__empty?: boolean;
                /** @description Filter: networkClass equal to */
                networkClass?: ("FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER" | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY")[];
                /** @description Filter: networkClass not equal to */
                networkClass__n?: ("FIREWALL" | "LOADBALANCER" | "ROUTER" | "SERVER" | "SWITCH" | "SWITCHROUTER" | "ACCESSPOINT" | "WIRELESSCONTROLLER" | "CONSOLESERVER" | "UNKNOWN" | "VOICEGATEWAY")[];
                /** @description Filter: networkClass is empty (null, or an empty string) for true, isn't for false */
                networkClass__empty?: boolean;
                /** @description Filter: softwareVersion equal to */
                softwareVersion?: string[];
                /** @description Filter: softwareVersion not equal to */
                softwareVersion__n?: string[];
                /** @description Filter: softwareVersion equal to, ignoring the case */
                softwareVersion__ie?: string[];
                /** @description Filter: softwareVersion not equal to, ignoring the case */
                softwareVersion__nie?: string[];
                /** @description Filter: softwareVersion contains, ignoring the case */
                softwareVersion__ic?: string[];
                /** @description Filter: softwareVersion doesn't contain, ignoring the case */
                softwareVersion__nic?: string[];
                /** @description Filter: softwareVersion starts with, ignoring the case */
                softwareVersion__isw?: string[];
                /** @description Filter: softwareVersion doesn't start with, ignoring the case */
                softwareVersion__nisw?: string[];
                /** @description Filter: softwareVersion ends with, ignoring the case */
                softwareVersion__iew?: string[];
                /** @description Filter: softwareVersion doesn't end with, ignoring the case */
                softwareVersion__niew?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression */
                softwareVersion__regex?: string[];
                /** @description Filter: softwareVersion matches the (POSIX) regular expression, ignoring the case */
                softwareVersion__iregex?: string[];
                /** @description Filter: softwareVersion is empty (null, or an empty string) for true, isn't for false */
                softwareVersion__empty?: boolean;
                /** @description Filter: softwareLevel equal to */
                softwareLevel?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: softwareLevel not equal to */
                softwareLevel__n?: ("GOLD" | "SILVER" | "BRONZE" | "UNKNOWN")[];
                /** @description Filter: softwareLevel is empty (null, or an empty string) for true, isn't for false */
                softwareLevel__empty?: boolean;
                /** @description Filter: serialNumber equal to */
                serialNumber?: string[];
                /** @description Filter: serialNumber not equal to */
                serialNumber__n?: string[];
                /** @description Filter: serialNumber equal to, ignoring the case */
                serialNumber__ie?: string[];
                /** @description Filter: serialNumber not equal to, ignoring the case */
                serialNumber__nie?: string[];
                /** @description Filter: serialNumber contains, ignoring the case */
                serialNumber__ic?: string[];
                /** @description Filter: serialNumber doesn't contain, ignoring the case */
                serialNumber__nic?: string[];
                /** @description Filter: serialNumber starts with, ignoring the case */
                serialNumber__isw?: string[];
                /** @description Filter: serialNumber doesn't start with, ignoring the case */
                serialNumber__nisw?: string[];
                /** @description Filter: serialNumber ends with, ignoring the case */
                serialNumber__iew?: string[];
                /** @description Filter: serialNumber doesn't end with, ignoring the case */
                serialNumber__niew?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression */
                serialNumber__regex?: string[];
                /** @description Filter: serialNumber matches the (POSIX) regular expression, ignoring the case */
                serialNumber__iregex?: string[];
                /** @description Filter: serialNumber is empty (null, or an empty string) for true, isn't for false */
                serialNumber__empty?: boolean;
                /** @description Filter: location equal to */
                location?: string[];
                /** @description Filter: location not equal to */
                location__n?: string[];
                /** @description Filter: location equal to, ignoring the case */
                location__ie?: string[];
                /** @description Filter: location not equal to, ignoring the case */
                location__nie?: string[];
                /** @description Filter: location contains, ignoring the case */
                location__ic?: string[];
                /** @description Filter: location doesn't contain, ignoring the case */
                location__nic?: string[];
                /** @description Filter: location starts with, ignoring the case */
                location__isw?: string[];
                /** @description Filter: location doesn't start with, ignoring the case */
                location__nisw?: string[];
                /** @description Filter: location ends with, ignoring the case */
                location__iew?: string[];
                /** @description Filter: location doesn't end with, ignoring the case */
                location__niew?: string[];
                /** @description Filter: location matches the (POSIX) regular expression */
                location__regex?: string[];
                /** @description Filter: location matches the (POSIX) regular expression, ignoring the case */
                location__iregex?: string[];
                /** @description Filter: location is empty (null, or an empty string) for true, isn't for false */
                location__empty?: boolean;
                /** @description Filter: contact equal to */
                contact?: string[];
                /** @description Filter: contact not equal to */
                contact__n?: string[];
                /** @description Filter: contact equal to, ignoring the case */
                contact__ie?: string[];
                /** @description Filter: contact not equal to, ignoring the case */
                contact__nie?: string[];
                /** @description Filter: contact contains, ignoring the case */
                contact__ic?: string[];
                /** @description Filter: contact doesn't contain, ignoring the case */
                contact__nic?: string[];
                /** @description Filter: contact starts with, ignoring the case */
                contact__isw?: string[];
                /** @description Filter: contact doesn't start with, ignoring the case */
                contact__nisw?: string[];
                /** @description Filter: contact ends with, ignoring the case */
                contact__iew?: string[];
                /** @description Filter: contact doesn't end with, ignoring the case */
                contact__niew?: string[];
                /** @description Filter: contact matches the (POSIX) regular expression */
                contact__regex?: string[];
                /** @description Filter: contact matches the (POSIX) regular expression, ignoring the case */
                contact__iregex?: string[];
                /** @description Filter: contact is empty (null, or an empty string) for true, isn't for false */
                contact__empty?: boolean;
                /** @description Filter: comments equal to */
                comments?: string[];
                /** @description Filter: comments not equal to */
                comments__n?: string[];
                /** @description Filter: comments equal to, ignoring the case */
                comments__ie?: string[];
                /** @description Filter: comments not equal to, ignoring the case */
                comments__nie?: string[];
                /** @description Filter: comments contains, ignoring the case */
                comments__ic?: string[];
                /** @description Filter: comments doesn't contain, ignoring the case */
                comments__nic?: string[];
                /** @description Filter: comments starts with, ignoring the case */
                comments__isw?: string[];
                /** @description Filter: comments doesn't start with, ignoring the case */
                comments__nisw?: string[];
                /** @description Filter: comments ends with, ignoring the case */
                comments__iew?: string[];
                /** @description Filter: comments doesn't end with, ignoring the case */
                comments__niew?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression */
                comments__regex?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression, ignoring the case */
                comments__iregex?: string[];
                /** @description Filter: comments is empty (null, or an empty string) for true, isn't for false */
                comments__empty?: boolean;
                /** @description Filter: creator equal to */
                creator?: string[];
                /** @description Filter: creator not equal to */
                creator__n?: string[];
                /** @description Filter: creator equal to, ignoring the case */
                creator__ie?: string[];
                /** @description Filter: creator not equal to, ignoring the case */
                creator__nie?: string[];
                /** @description Filter: creator contains, ignoring the case */
                creator__ic?: string[];
                /** @description Filter: creator doesn't contain, ignoring the case */
                creator__nic?: string[];
                /** @description Filter: creator starts with, ignoring the case */
                creator__isw?: string[];
                /** @description Filter: creator doesn't start with, ignoring the case */
                creator__nisw?: string[];
                /** @description Filter: creator ends with, ignoring the case */
                creator__iew?: string[];
                /** @description Filter: creator doesn't end with, ignoring the case */
                creator__niew?: string[];
                /** @description Filter: creator matches the (POSIX) regular expression */
                creator__regex?: string[];
                /** @description Filter: creator matches the (POSIX) regular expression, ignoring the case */
                creator__iregex?: string[];
                /** @description Filter: creator is empty (null, or an empty string) for true, isn't for false */
                creator__empty?: boolean;
                /** @description Filter: cachedIpAddress equal to */
                cachedIpAddress?: string[];
                /** @description Filter: cachedIpAddress not equal to */
                cachedIpAddress__n?: string[];
                /** @description Filter: cachedIpAddress is empty (null, or an empty string) for true, isn't for false */
                cachedIpAddress__empty?: boolean;
                /** @description Filter: cachedIpAddress IP address within the given subnet (10.0.0.0/8) */
                cachedIpAddress__net?: string[];
                /** @description Filter: createdDate equal to */
                createdDate?: string[];
                /** @description Filter: createdDate not equal to */
                createdDate__n?: string[];
                /** @description Filter: createdDate lower than */
                createdDate__lt?: string[];
                /** @description Filter: createdDate lower than or equal to */
                createdDate__lte?: string[];
                /** @description Filter: createdDate greater than */
                createdDate__gt?: string[];
                /** @description Filter: createdDate greater than or equal to */
                createdDate__gte?: string[];
                /** @description Filter: createdDate is empty (null, or an empty string) for true, isn't for false */
                createdDate__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: eolDate equal to */
                eolDate?: string[];
                /** @description Filter: eolDate not equal to */
                eolDate__n?: string[];
                /** @description Filter: eolDate lower than */
                eolDate__lt?: string[];
                /** @description Filter: eolDate lower than or equal to */
                eolDate__lte?: string[];
                /** @description Filter: eolDate greater than */
                eolDate__gt?: string[];
                /** @description Filter: eolDate greater than or equal to */
                eolDate__gte?: string[];
                /** @description Filter: eolDate is empty (null, or an empty string) for true, isn't for false */
                eolDate__empty?: boolean;
                /** @description Filter: eosDate equal to */
                eosDate?: string[];
                /** @description Filter: eosDate not equal to */
                eosDate__n?: string[];
                /** @description Filter: eosDate lower than */
                eosDate__lt?: string[];
                /** @description Filter: eosDate lower than or equal to */
                eosDate__lte?: string[];
                /** @description Filter: eosDate greater than */
                eosDate__gt?: string[];
                /** @description Filter: eosDate greater than or equal to */
                eosDate__gte?: string[];
                /** @description Filter: eosDate is empty (null, or an empty string) for true, isn't for false */
                eosDate__empty?: boolean;
                /** @description Filter: lastSnapshotSuccessDate equal to */
                lastSnapshotSuccessDate?: string[];
                /** @description Filter: lastSnapshotSuccessDate not equal to */
                lastSnapshotSuccessDate__n?: string[];
                /** @description Filter: lastSnapshotSuccessDate lower than */
                lastSnapshotSuccessDate__lt?: string[];
                /** @description Filter: lastSnapshotSuccessDate lower than or equal to */
                lastSnapshotSuccessDate__lte?: string[];
                /** @description Filter: lastSnapshotSuccessDate greater than */
                lastSnapshotSuccessDate__gt?: string[];
                /** @description Filter: lastSnapshotSuccessDate greater than or equal to */
                lastSnapshotSuccessDate__gte?: string[];
                /** @description Filter: lastSnapshotSuccessDate is empty (null, or an empty string) for true, isn't for false */
                lastSnapshotSuccessDate__empty?: boolean;
                /** @description Filter: lastSnapshotFailureDate equal to */
                lastSnapshotFailureDate?: string[];
                /** @description Filter: lastSnapshotFailureDate not equal to */
                lastSnapshotFailureDate__n?: string[];
                /** @description Filter: lastSnapshotFailureDate lower than */
                lastSnapshotFailureDate__lt?: string[];
                /** @description Filter: lastSnapshotFailureDate lower than or equal to */
                lastSnapshotFailureDate__lte?: string[];
                /** @description Filter: lastSnapshotFailureDate greater than */
                lastSnapshotFailureDate__gt?: string[];
                /** @description Filter: lastSnapshotFailureDate greater than or equal to */
                lastSnapshotFailureDate__gte?: string[];
                /** @description Filter: lastSnapshotFailureDate is empty (null, or an empty string) for true, isn't for false */
                lastSnapshotFailureDate__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: tag equal to */
                tag?: string[];
                /** @description Filter: tag not equal to */
                tag__n?: string[];
                /** @description Filter: tag equal to, ignoring the case */
                tag__ie?: string[];
                /** @description Filter: tag not equal to, ignoring the case */
                tag__nie?: string[];
                /** @description Filter: tag contains, ignoring the case */
                tag__ic?: string[];
                /** @description Filter: tag doesn't contain, ignoring the case */
                tag__nic?: string[];
                /** @description Filter: tag starts with, ignoring the case */
                tag__isw?: string[];
                /** @description Filter: tag doesn't start with, ignoring the case */
                tag__nisw?: string[];
                /** @description Filter: tag is empty (null, or an empty string) for true, isn't for false */
                tag__empty?: boolean;
                /** @description Filter: compliant equal to */
                compliant?: boolean[];
            };
            header?: never;
            path: {
                /** @description Group ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseDevice"];
                    "application/xml": components["schemas"]["ListResponseDevice"];
                    "application/yaml": components["schemas"]["ListResponseDevice"];
                };
            };
        };
    };
    listHooks: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, type, enabled */
                sort?: string;
                /** @description Free text search; fields: name, url */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: type equal to */
                type?: string[];
                /** @description Filter: type not equal to */
                type__n?: string[];
                /** @description Filter: type equal to, ignoring the case */
                type__ie?: string[];
                /** @description Filter: type not equal to, ignoring the case */
                type__nie?: string[];
                /** @description Filter: type contains, ignoring the case */
                type__ic?: string[];
                /** @description Filter: type doesn't contain, ignoring the case */
                type__nic?: string[];
                /** @description Filter: type starts with, ignoring the case */
                type__isw?: string[];
                /** @description Filter: type doesn't start with, ignoring the case */
                type__nisw?: string[];
                /** @description Filter: type ends with, ignoring the case */
                type__iew?: string[];
                /** @description Filter: type doesn't end with, ignoring the case */
                type__niew?: string[];
                /** @description Filter: type matches the (POSIX) regular expression */
                type__regex?: string[];
                /** @description Filter: type matches the (POSIX) regular expression, ignoring the case */
                type__iregex?: string[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: enabled equal to */
                enabled?: boolean[];
                /** @description Filter: url equal to */
                url?: string[];
                /** @description Filter: url not equal to */
                url__n?: string[];
                /** @description Filter: url equal to, ignoring the case */
                url__ie?: string[];
                /** @description Filter: url not equal to, ignoring the case */
                url__nie?: string[];
                /** @description Filter: url contains, ignoring the case */
                url__ic?: string[];
                /** @description Filter: url doesn't contain, ignoring the case */
                url__nic?: string[];
                /** @description Filter: url starts with, ignoring the case */
                url__isw?: string[];
                /** @description Filter: url doesn't start with, ignoring the case */
                url__nisw?: string[];
                /** @description Filter: url ends with, ignoring the case */
                url__iew?: string[];
                /** @description Filter: url doesn't end with, ignoring the case */
                url__niew?: string[];
                /** @description Filter: url matches the (POSIX) regular expression */
                url__regex?: string[];
                /** @description Filter: url matches the (POSIX) regular expression, ignoring the case */
                url__iregex?: string[];
                /** @description Filter: url is empty (null, or an empty string) for true, isn't for false */
                url__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseHook"];
                    "application/xml": components["schemas"]["ListResponseHook"];
                    "application/yaml": components["schemas"]["ListResponseHook"];
                };
            };
        };
    };
    createHook: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["HookRequest"];
                "application/xml": components["schemas"]["HookRequest"];
                "application/yaml": components["schemas"]["HookRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Hook"];
                    "application/xml": components["schemas"]["Hook"];
                    "application/yaml": components["schemas"]["Hook"];
                };
            };
        };
    };
    getHook: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Hook ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Hook"];
                    "application/xml": components["schemas"]["Hook"];
                    "application/yaml": components["schemas"]["Hook"];
                };
            };
        };
    };
    replaceHook: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Hook ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["HookRequest"];
                "application/xml": components["schemas"]["HookRequest"];
                "application/yaml": components["schemas"]["HookRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Hook"];
                    "application/xml": components["schemas"]["Hook"];
                    "application/yaml": components["schemas"]["Hook"];
                };
            };
        };
    };
    deleteHook: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Hook ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateHook: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Hook ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Hook"];
                    "application/xml": components["schemas"]["Hook"];
                    "application/yaml": components["schemas"]["Hook"];
                };
            };
        };
    };
    listScripts: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, folder, deviceDriver, author */
                sort?: string;
                /** @description Free text search; fields: name, folder, author */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: folder equal to */
                folder?: string[];
                /** @description Filter: folder not equal to */
                folder__n?: string[];
                /** @description Filter: folder equal to, ignoring the case */
                folder__ie?: string[];
                /** @description Filter: folder not equal to, ignoring the case */
                folder__nie?: string[];
                /** @description Filter: folder contains, ignoring the case */
                folder__ic?: string[];
                /** @description Filter: folder doesn't contain, ignoring the case */
                folder__nic?: string[];
                /** @description Filter: folder starts with, ignoring the case */
                folder__isw?: string[];
                /** @description Filter: folder doesn't start with, ignoring the case */
                folder__nisw?: string[];
                /** @description Filter: folder ends with, ignoring the case */
                folder__iew?: string[];
                /** @description Filter: folder doesn't end with, ignoring the case */
                folder__niew?: string[];
                /** @description Filter: folder matches the (POSIX) regular expression */
                folder__regex?: string[];
                /** @description Filter: folder matches the (POSIX) regular expression, ignoring the case */
                folder__iregex?: string[];
                /** @description Filter: folder is empty (null, or an empty string) for true, isn't for false */
                folder__empty?: boolean;
                /** @description Filter: deviceDriver equal to */
                deviceDriver?: string[];
                /** @description Filter: deviceDriver not equal to */
                deviceDriver__n?: string[];
                /** @description Filter: deviceDriver equal to, ignoring the case */
                deviceDriver__ie?: string[];
                /** @description Filter: deviceDriver not equal to, ignoring the case */
                deviceDriver__nie?: string[];
                /** @description Filter: deviceDriver contains, ignoring the case */
                deviceDriver__ic?: string[];
                /** @description Filter: deviceDriver doesn't contain, ignoring the case */
                deviceDriver__nic?: string[];
                /** @description Filter: deviceDriver starts with, ignoring the case */
                deviceDriver__isw?: string[];
                /** @description Filter: deviceDriver doesn't start with, ignoring the case */
                deviceDriver__nisw?: string[];
                /** @description Filter: deviceDriver ends with, ignoring the case */
                deviceDriver__iew?: string[];
                /** @description Filter: deviceDriver doesn't end with, ignoring the case */
                deviceDriver__niew?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression */
                deviceDriver__regex?: string[];
                /** @description Filter: deviceDriver matches the (POSIX) regular expression, ignoring the case */
                deviceDriver__iregex?: string[];
                /** @description Filter: deviceDriver is empty (null, or an empty string) for true, isn't for false */
                deviceDriver__empty?: boolean;
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: author matches the (POSIX) regular expression */
                author__regex?: string[];
                /** @description Filter: author matches the (POSIX) regular expression, ignoring the case */
                author__iregex?: string[];
                /** @description Filter: author is empty (null, or an empty string) for true, isn't for false */
                author__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseScript"];
                    "application/xml": components["schemas"]["ListResponseScript"];
                    "application/yaml": components["schemas"]["ListResponseScript"];
                };
            };
        };
    };
    createScript: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ScriptRequest"];
                "application/xml": components["schemas"]["ScriptRequest"];
                "application/yaml": components["schemas"]["ScriptRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Script"];
                    "application/xml": components["schemas"]["Script"];
                    "application/yaml": components["schemas"]["Script"];
                };
            };
        };
    };
    getScript: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Script ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Script"];
                    "application/xml": components["schemas"]["Script"];
                    "application/yaml": components["schemas"]["Script"];
                };
            };
        };
    };
    replaceScript: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Script ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ScriptRequest"];
                "application/xml": components["schemas"]["ScriptRequest"];
                "application/yaml": components["schemas"]["ScriptRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Script"];
                    "application/xml": components["schemas"]["Script"];
                    "application/yaml": components["schemas"]["Script"];
                };
            };
        };
    };
    deleteScript: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Script ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateScript: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Script ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Script"];
                    "application/xml": components["schemas"]["Script"];
                    "application/yaml": components["schemas"]["Script"];
                };
            };
        };
    };
    validateScript: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ScriptRequest"];
                "application/xml": components["schemas"]["ScriptRequest"];
                "application/yaml": components["schemas"]["ScriptRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Script"];
                    "application/xml": components["schemas"]["Script"];
                    "application/yaml": components["schemas"]["Script"];
                };
            };
        };
    };
    getHealth: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Health"];
                    "application/xml": components["schemas"]["Health"];
                    "application/yaml": components["schemas"]["Health"];
                };
            };
        };
    };
    getClusterMaster: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClusterMaster"];
                    "application/xml": components["schemas"]["ClusterMaster"];
                    "application/yaml": components["schemas"]["ClusterMaster"];
                };
            };
        };
    };
    getInfo: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SystemInfo"];
                    "application/xml": components["schemas"]["SystemInfo"];
                    "application/yaml": components["schemas"]["SystemInfo"];
                };
            };
        };
    };
    listClusterMembers: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseClusterMember"];
                    "application/xml": components["schemas"]["ListResponseClusterMember"];
                    "application/yaml": components["schemas"]["ListResponseClusterMember"];
                };
            };
        };
    };
    listServices: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseService"];
                    "application/xml": components["schemas"]["ListResponseService"];
                    "application/yaml": components["schemas"]["ListResponseService"];
                };
            };
        };
    };
    listDeviceTasks: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, type, status, author, target, priority, scheduleType, scheduleReference, creationDate, changeDate, executionDate, childOrder */
                sort?: string;
                /** @description Free text search; fields: author, target, comments */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("CheckComplianceTask" | "CheckGroupComplianceTask" | "CheckGroupSoftwareTask" | "DiscoverDeviceTypeTask" | "PurgeDatabaseTask" | "RunDeviceGroupScriptTask" | "RunDeviceScriptTask" | "RunDiagnosticsTask" | "RunGroupDiagnosticsTask" | "ScanSubnetsTask" | "TakeGroupSnapshotTask" | "TakeSnapshotTask")[];
                /** @description Filter: type not equal to */
                type__n?: ("CheckComplianceTask" | "CheckGroupComplianceTask" | "CheckGroupSoftwareTask" | "DiscoverDeviceTypeTask" | "PurgeDatabaseTask" | "RunDeviceGroupScriptTask" | "RunDeviceScriptTask" | "RunDiagnosticsTask" | "RunGroupDiagnosticsTask" | "ScanSubnetsTask" | "TakeGroupSnapshotTask" | "TakeSnapshotTask")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: status equal to */
                status?: ("CANCELLED" | "FAILURE" | "NEW" | "RUNNING" | "SCHEDULED" | "SUCCESS" | "WAITING" | "DELAYED")[];
                /** @description Filter: status not equal to */
                status__n?: ("CANCELLED" | "FAILURE" | "NEW" | "RUNNING" | "SCHEDULED" | "SUCCESS" | "WAITING" | "DELAYED")[];
                /** @description Filter: status is empty (null, or an empty string) for true, isn't for false */
                status__empty?: boolean;
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: author matches the (POSIX) regular expression */
                author__regex?: string[];
                /** @description Filter: author matches the (POSIX) regular expression, ignoring the case */
                author__iregex?: string[];
                /** @description Filter: author is empty (null, or an empty string) for true, isn't for false */
                author__empty?: boolean;
                /** @description Filter: target equal to */
                target?: string[];
                /** @description Filter: target not equal to */
                target__n?: string[];
                /** @description Filter: target equal to, ignoring the case */
                target__ie?: string[];
                /** @description Filter: target not equal to, ignoring the case */
                target__nie?: string[];
                /** @description Filter: target contains, ignoring the case */
                target__ic?: string[];
                /** @description Filter: target doesn't contain, ignoring the case */
                target__nic?: string[];
                /** @description Filter: target starts with, ignoring the case */
                target__isw?: string[];
                /** @description Filter: target doesn't start with, ignoring the case */
                target__nisw?: string[];
                /** @description Filter: target ends with, ignoring the case */
                target__iew?: string[];
                /** @description Filter: target doesn't end with, ignoring the case */
                target__niew?: string[];
                /** @description Filter: target matches the (POSIX) regular expression */
                target__regex?: string[];
                /** @description Filter: target matches the (POSIX) regular expression, ignoring the case */
                target__iregex?: string[];
                /** @description Filter: target is empty (null, or an empty string) for true, isn't for false */
                target__empty?: boolean;
                /** @description Filter: comments equal to */
                comments?: string[];
                /** @description Filter: comments not equal to */
                comments__n?: string[];
                /** @description Filter: comments equal to, ignoring the case */
                comments__ie?: string[];
                /** @description Filter: comments not equal to, ignoring the case */
                comments__nie?: string[];
                /** @description Filter: comments contains, ignoring the case */
                comments__ic?: string[];
                /** @description Filter: comments doesn't contain, ignoring the case */
                comments__nic?: string[];
                /** @description Filter: comments starts with, ignoring the case */
                comments__isw?: string[];
                /** @description Filter: comments doesn't start with, ignoring the case */
                comments__nisw?: string[];
                /** @description Filter: comments ends with, ignoring the case */
                comments__iew?: string[];
                /** @description Filter: comments doesn't end with, ignoring the case */
                comments__niew?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression */
                comments__regex?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression, ignoring the case */
                comments__iregex?: string[];
                /** @description Filter: comments is empty (null, or an empty string) for true, isn't for false */
                comments__empty?: boolean;
                /** @description Filter: priority equal to */
                priority?: number[];
                /** @description Filter: priority not equal to */
                priority__n?: number[];
                /** @description Filter: priority lower than */
                priority__lt?: number[];
                /** @description Filter: priority lower than or equal to */
                priority__lte?: number[];
                /** @description Filter: priority greater than */
                priority__gt?: number[];
                /** @description Filter: priority greater than or equal to */
                priority__gte?: number[];
                /** @description Filter: priority is empty (null, or an empty string) for true, isn't for false */
                priority__empty?: boolean;
                /** @description Filter: scheduleType equal to */
                scheduleType?: ("ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY")[];
                /** @description Filter: scheduleType not equal to */
                scheduleType__n?: ("ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY")[];
                /** @description Filter: scheduleType is empty (null, or an empty string) for true, isn't for false */
                scheduleType__empty?: boolean;
                /** @description Filter: scheduleReference equal to */
                scheduleReference?: string[];
                /** @description Filter: scheduleReference not equal to */
                scheduleReference__n?: string[];
                /** @description Filter: scheduleReference lower than */
                scheduleReference__lt?: string[];
                /** @description Filter: scheduleReference lower than or equal to */
                scheduleReference__lte?: string[];
                /** @description Filter: scheduleReference greater than */
                scheduleReference__gt?: string[];
                /** @description Filter: scheduleReference greater than or equal to */
                scheduleReference__gte?: string[];
                /** @description Filter: scheduleReference is empty (null, or an empty string) for true, isn't for false */
                scheduleReference__empty?: boolean;
                /** @description Filter: creationDate equal to */
                creationDate?: string[];
                /** @description Filter: creationDate not equal to */
                creationDate__n?: string[];
                /** @description Filter: creationDate lower than */
                creationDate__lt?: string[];
                /** @description Filter: creationDate lower than or equal to */
                creationDate__lte?: string[];
                /** @description Filter: creationDate greater than */
                creationDate__gt?: string[];
                /** @description Filter: creationDate greater than or equal to */
                creationDate__gte?: string[];
                /** @description Filter: creationDate is empty (null, or an empty string) for true, isn't for false */
                creationDate__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: executionDate equal to */
                executionDate?: string[];
                /** @description Filter: executionDate not equal to */
                executionDate__n?: string[];
                /** @description Filter: executionDate lower than */
                executionDate__lt?: string[];
                /** @description Filter: executionDate lower than or equal to */
                executionDate__lte?: string[];
                /** @description Filter: executionDate greater than */
                executionDate__gt?: string[];
                /** @description Filter: executionDate greater than or equal to */
                executionDate__gte?: string[];
                /** @description Filter: executionDate is empty (null, or an empty string) for true, isn't for false */
                executionDate__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: group lower than */
                group__lt?: number[];
                /** @description Filter: group lower than or equal to */
                group__lte?: number[];
                /** @description Filter: group greater than */
                group__gt?: number[];
                /** @description Filter: group greater than or equal to */
                group__gte?: number[];
                /** @description Filter: group is empty (null, or an empty string) for true, isn't for false */
                group__empty?: boolean;
                /** @description Filter: parentTask equal to */
                parentTask?: number[];
                /** @description Filter: parentTask not equal to */
                parentTask__n?: number[];
                /** @description Filter: parentTask lower than */
                parentTask__lt?: number[];
                /** @description Filter: parentTask lower than or equal to */
                parentTask__lte?: number[];
                /** @description Filter: parentTask greater than */
                parentTask__gt?: number[];
                /** @description Filter: parentTask greater than or equal to */
                parentTask__gte?: number[];
                /** @description Filter: parentTask is empty (null, or an empty string) for true, isn't for false */
                parentTask__empty?: boolean;
                /** @description Filter: childOrder equal to */
                childOrder?: number[];
                /** @description Filter: childOrder not equal to */
                childOrder__n?: number[];
                /** @description Filter: childOrder lower than */
                childOrder__lt?: number[];
                /** @description Filter: childOrder lower than or equal to */
                childOrder__lte?: number[];
                /** @description Filter: childOrder greater than */
                childOrder__gt?: number[];
                /** @description Filter: childOrder greater than or equal to */
                childOrder__gte?: number[];
                /** @description Filter: childOrder is empty (null, or an empty string) for true, isn't for false */
                childOrder__empty?: boolean;
                /** @description Filter: runnerId equal to */
                runnerId?: string[];
                /** @description Filter: runnerId not equal to */
                runnerId__n?: string[];
                /** @description Filter: runnerId equal to, ignoring the case */
                runnerId__ie?: string[];
                /** @description Filter: runnerId not equal to, ignoring the case */
                runnerId__nie?: string[];
                /** @description Filter: runnerId contains, ignoring the case */
                runnerId__ic?: string[];
                /** @description Filter: runnerId doesn't contain, ignoring the case */
                runnerId__nic?: string[];
                /** @description Filter: runnerId starts with, ignoring the case */
                runnerId__isw?: string[];
                /** @description Filter: runnerId doesn't start with, ignoring the case */
                runnerId__nisw?: string[];
                /** @description Filter: runnerId ends with, ignoring the case */
                runnerId__iew?: string[];
                /** @description Filter: runnerId doesn't end with, ignoring the case */
                runnerId__niew?: string[];
                /** @description Filter: runnerId matches the (POSIX) regular expression */
                runnerId__regex?: string[];
                /** @description Filter: runnerId matches the (POSIX) regular expression, ignoring the case */
                runnerId__iregex?: string[];
                /** @description Filter: runnerId is empty (null, or an empty string) for true, isn't for false */
                runnerId__empty?: boolean;
                /** @description Filter: debugEnabled equal to */
                debugEnabled?: boolean[];
            };
            header?: never;
            path: {
                /** @description Device ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseTask"];
                    "application/xml": components["schemas"]["ListResponseTask"];
                    "application/yaml": components["schemas"]["ListResponseTask"];
                };
            };
        };
    };
    cancelTask: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Task ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    listTasks: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, type, status, author, target, priority, scheduleType, scheduleReference, creationDate, changeDate, executionDate, childOrder */
                sort?: string;
                /** @description Free text search; fields: author, target, comments */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("CheckComplianceTask" | "CheckGroupComplianceTask" | "CheckGroupSoftwareTask" | "DiscoverDeviceTypeTask" | "PurgeDatabaseTask" | "RunDeviceGroupScriptTask" | "RunDeviceScriptTask" | "RunDiagnosticsTask" | "RunGroupDiagnosticsTask" | "ScanSubnetsTask" | "TakeGroupSnapshotTask" | "TakeSnapshotTask")[];
                /** @description Filter: type not equal to */
                type__n?: ("CheckComplianceTask" | "CheckGroupComplianceTask" | "CheckGroupSoftwareTask" | "DiscoverDeviceTypeTask" | "PurgeDatabaseTask" | "RunDeviceGroupScriptTask" | "RunDeviceScriptTask" | "RunDiagnosticsTask" | "RunGroupDiagnosticsTask" | "ScanSubnetsTask" | "TakeGroupSnapshotTask" | "TakeSnapshotTask")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: status equal to */
                status?: ("CANCELLED" | "FAILURE" | "NEW" | "RUNNING" | "SCHEDULED" | "SUCCESS" | "WAITING" | "DELAYED")[];
                /** @description Filter: status not equal to */
                status__n?: ("CANCELLED" | "FAILURE" | "NEW" | "RUNNING" | "SCHEDULED" | "SUCCESS" | "WAITING" | "DELAYED")[];
                /** @description Filter: status is empty (null, or an empty string) for true, isn't for false */
                status__empty?: boolean;
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: author matches the (POSIX) regular expression */
                author__regex?: string[];
                /** @description Filter: author matches the (POSIX) regular expression, ignoring the case */
                author__iregex?: string[];
                /** @description Filter: author is empty (null, or an empty string) for true, isn't for false */
                author__empty?: boolean;
                /** @description Filter: target equal to */
                target?: string[];
                /** @description Filter: target not equal to */
                target__n?: string[];
                /** @description Filter: target equal to, ignoring the case */
                target__ie?: string[];
                /** @description Filter: target not equal to, ignoring the case */
                target__nie?: string[];
                /** @description Filter: target contains, ignoring the case */
                target__ic?: string[];
                /** @description Filter: target doesn't contain, ignoring the case */
                target__nic?: string[];
                /** @description Filter: target starts with, ignoring the case */
                target__isw?: string[];
                /** @description Filter: target doesn't start with, ignoring the case */
                target__nisw?: string[];
                /** @description Filter: target ends with, ignoring the case */
                target__iew?: string[];
                /** @description Filter: target doesn't end with, ignoring the case */
                target__niew?: string[];
                /** @description Filter: target matches the (POSIX) regular expression */
                target__regex?: string[];
                /** @description Filter: target matches the (POSIX) regular expression, ignoring the case */
                target__iregex?: string[];
                /** @description Filter: target is empty (null, or an empty string) for true, isn't for false */
                target__empty?: boolean;
                /** @description Filter: comments equal to */
                comments?: string[];
                /** @description Filter: comments not equal to */
                comments__n?: string[];
                /** @description Filter: comments equal to, ignoring the case */
                comments__ie?: string[];
                /** @description Filter: comments not equal to, ignoring the case */
                comments__nie?: string[];
                /** @description Filter: comments contains, ignoring the case */
                comments__ic?: string[];
                /** @description Filter: comments doesn't contain, ignoring the case */
                comments__nic?: string[];
                /** @description Filter: comments starts with, ignoring the case */
                comments__isw?: string[];
                /** @description Filter: comments doesn't start with, ignoring the case */
                comments__nisw?: string[];
                /** @description Filter: comments ends with, ignoring the case */
                comments__iew?: string[];
                /** @description Filter: comments doesn't end with, ignoring the case */
                comments__niew?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression */
                comments__regex?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression, ignoring the case */
                comments__iregex?: string[];
                /** @description Filter: comments is empty (null, or an empty string) for true, isn't for false */
                comments__empty?: boolean;
                /** @description Filter: priority equal to */
                priority?: number[];
                /** @description Filter: priority not equal to */
                priority__n?: number[];
                /** @description Filter: priority lower than */
                priority__lt?: number[];
                /** @description Filter: priority lower than or equal to */
                priority__lte?: number[];
                /** @description Filter: priority greater than */
                priority__gt?: number[];
                /** @description Filter: priority greater than or equal to */
                priority__gte?: number[];
                /** @description Filter: priority is empty (null, or an empty string) for true, isn't for false */
                priority__empty?: boolean;
                /** @description Filter: scheduleType equal to */
                scheduleType?: ("ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY")[];
                /** @description Filter: scheduleType not equal to */
                scheduleType__n?: ("ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY")[];
                /** @description Filter: scheduleType is empty (null, or an empty string) for true, isn't for false */
                scheduleType__empty?: boolean;
                /** @description Filter: scheduleReference equal to */
                scheduleReference?: string[];
                /** @description Filter: scheduleReference not equal to */
                scheduleReference__n?: string[];
                /** @description Filter: scheduleReference lower than */
                scheduleReference__lt?: string[];
                /** @description Filter: scheduleReference lower than or equal to */
                scheduleReference__lte?: string[];
                /** @description Filter: scheduleReference greater than */
                scheduleReference__gt?: string[];
                /** @description Filter: scheduleReference greater than or equal to */
                scheduleReference__gte?: string[];
                /** @description Filter: scheduleReference is empty (null, or an empty string) for true, isn't for false */
                scheduleReference__empty?: boolean;
                /** @description Filter: creationDate equal to */
                creationDate?: string[];
                /** @description Filter: creationDate not equal to */
                creationDate__n?: string[];
                /** @description Filter: creationDate lower than */
                creationDate__lt?: string[];
                /** @description Filter: creationDate lower than or equal to */
                creationDate__lte?: string[];
                /** @description Filter: creationDate greater than */
                creationDate__gt?: string[];
                /** @description Filter: creationDate greater than or equal to */
                creationDate__gte?: string[];
                /** @description Filter: creationDate is empty (null, or an empty string) for true, isn't for false */
                creationDate__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: executionDate equal to */
                executionDate?: string[];
                /** @description Filter: executionDate not equal to */
                executionDate__n?: string[];
                /** @description Filter: executionDate lower than */
                executionDate__lt?: string[];
                /** @description Filter: executionDate lower than or equal to */
                executionDate__lte?: string[];
                /** @description Filter: executionDate greater than */
                executionDate__gt?: string[];
                /** @description Filter: executionDate greater than or equal to */
                executionDate__gte?: string[];
                /** @description Filter: executionDate is empty (null, or an empty string) for true, isn't for false */
                executionDate__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: group lower than */
                group__lt?: number[];
                /** @description Filter: group lower than or equal to */
                group__lte?: number[];
                /** @description Filter: group greater than */
                group__gt?: number[];
                /** @description Filter: group greater than or equal to */
                group__gte?: number[];
                /** @description Filter: group is empty (null, or an empty string) for true, isn't for false */
                group__empty?: boolean;
                /** @description Filter: parentTask equal to */
                parentTask?: number[];
                /** @description Filter: parentTask not equal to */
                parentTask__n?: number[];
                /** @description Filter: parentTask lower than */
                parentTask__lt?: number[];
                /** @description Filter: parentTask lower than or equal to */
                parentTask__lte?: number[];
                /** @description Filter: parentTask greater than */
                parentTask__gt?: number[];
                /** @description Filter: parentTask greater than or equal to */
                parentTask__gte?: number[];
                /** @description Filter: parentTask is empty (null, or an empty string) for true, isn't for false */
                parentTask__empty?: boolean;
                /** @description Filter: childOrder equal to */
                childOrder?: number[];
                /** @description Filter: childOrder not equal to */
                childOrder__n?: number[];
                /** @description Filter: childOrder lower than */
                childOrder__lt?: number[];
                /** @description Filter: childOrder lower than or equal to */
                childOrder__lte?: number[];
                /** @description Filter: childOrder greater than */
                childOrder__gt?: number[];
                /** @description Filter: childOrder greater than or equal to */
                childOrder__gte?: number[];
                /** @description Filter: childOrder is empty (null, or an empty string) for true, isn't for false */
                childOrder__empty?: boolean;
                /** @description Filter: runnerId equal to */
                runnerId?: string[];
                /** @description Filter: runnerId not equal to */
                runnerId__n?: string[];
                /** @description Filter: runnerId equal to, ignoring the case */
                runnerId__ie?: string[];
                /** @description Filter: runnerId not equal to, ignoring the case */
                runnerId__nie?: string[];
                /** @description Filter: runnerId contains, ignoring the case */
                runnerId__ic?: string[];
                /** @description Filter: runnerId doesn't contain, ignoring the case */
                runnerId__nic?: string[];
                /** @description Filter: runnerId starts with, ignoring the case */
                runnerId__isw?: string[];
                /** @description Filter: runnerId doesn't start with, ignoring the case */
                runnerId__nisw?: string[];
                /** @description Filter: runnerId ends with, ignoring the case */
                runnerId__iew?: string[];
                /** @description Filter: runnerId doesn't end with, ignoring the case */
                runnerId__niew?: string[];
                /** @description Filter: runnerId matches the (POSIX) regular expression */
                runnerId__regex?: string[];
                /** @description Filter: runnerId matches the (POSIX) regular expression, ignoring the case */
                runnerId__iregex?: string[];
                /** @description Filter: runnerId is empty (null, or an empty string) for true, isn't for false */
                runnerId__empty?: boolean;
                /** @description Filter: debugEnabled equal to */
                debugEnabled?: boolean[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseTask"];
                    "application/xml": components["schemas"]["ListResponseTask"];
                    "application/yaml": components["schemas"]["ListResponseTask"];
                };
            };
        };
    };
    createTask: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["TaskRequest"];
                "application/xml": components["schemas"]["TaskRequest"];
                "application/yaml": components["schemas"]["TaskRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    getTask: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Task ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Task"];
                    "application/xml": components["schemas"]["Task"];
                    "application/yaml": components["schemas"]["Task"];
                };
            };
        };
    };
    getTaskDebugLog: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Task ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/plain": string;
                };
            };
        };
    };
    getTaskStats: {
        parameters: {
            query?: {
                /** @description Free text search (as in the list) */
                q?: string;
                /** @description Fields to count by (up to two), comma-separated; fields: type, status, author, scheduleType, device, group, runnerId */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets; fields: scheduleReference, creationDate, changeDate, executionDate */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: type equal to */
                type?: ("CheckComplianceTask" | "CheckGroupComplianceTask" | "CheckGroupSoftwareTask" | "DiscoverDeviceTypeTask" | "PurgeDatabaseTask" | "RunDeviceGroupScriptTask" | "RunDeviceScriptTask" | "RunDiagnosticsTask" | "RunGroupDiagnosticsTask" | "ScanSubnetsTask" | "TakeGroupSnapshotTask" | "TakeSnapshotTask")[];
                /** @description Filter: type not equal to */
                type__n?: ("CheckComplianceTask" | "CheckGroupComplianceTask" | "CheckGroupSoftwareTask" | "DiscoverDeviceTypeTask" | "PurgeDatabaseTask" | "RunDeviceGroupScriptTask" | "RunDeviceScriptTask" | "RunDiagnosticsTask" | "RunGroupDiagnosticsTask" | "ScanSubnetsTask" | "TakeGroupSnapshotTask" | "TakeSnapshotTask")[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: status equal to */
                status?: ("CANCELLED" | "FAILURE" | "NEW" | "RUNNING" | "SCHEDULED" | "SUCCESS" | "WAITING" | "DELAYED")[];
                /** @description Filter: status not equal to */
                status__n?: ("CANCELLED" | "FAILURE" | "NEW" | "RUNNING" | "SCHEDULED" | "SUCCESS" | "WAITING" | "DELAYED")[];
                /** @description Filter: status is empty (null, or an empty string) for true, isn't for false */
                status__empty?: boolean;
                /** @description Filter: author equal to */
                author?: string[];
                /** @description Filter: author not equal to */
                author__n?: string[];
                /** @description Filter: author equal to, ignoring the case */
                author__ie?: string[];
                /** @description Filter: author not equal to, ignoring the case */
                author__nie?: string[];
                /** @description Filter: author contains, ignoring the case */
                author__ic?: string[];
                /** @description Filter: author doesn't contain, ignoring the case */
                author__nic?: string[];
                /** @description Filter: author starts with, ignoring the case */
                author__isw?: string[];
                /** @description Filter: author doesn't start with, ignoring the case */
                author__nisw?: string[];
                /** @description Filter: author ends with, ignoring the case */
                author__iew?: string[];
                /** @description Filter: author doesn't end with, ignoring the case */
                author__niew?: string[];
                /** @description Filter: author matches the (POSIX) regular expression */
                author__regex?: string[];
                /** @description Filter: author matches the (POSIX) regular expression, ignoring the case */
                author__iregex?: string[];
                /** @description Filter: author is empty (null, or an empty string) for true, isn't for false */
                author__empty?: boolean;
                /** @description Filter: target equal to */
                target?: string[];
                /** @description Filter: target not equal to */
                target__n?: string[];
                /** @description Filter: target equal to, ignoring the case */
                target__ie?: string[];
                /** @description Filter: target not equal to, ignoring the case */
                target__nie?: string[];
                /** @description Filter: target contains, ignoring the case */
                target__ic?: string[];
                /** @description Filter: target doesn't contain, ignoring the case */
                target__nic?: string[];
                /** @description Filter: target starts with, ignoring the case */
                target__isw?: string[];
                /** @description Filter: target doesn't start with, ignoring the case */
                target__nisw?: string[];
                /** @description Filter: target ends with, ignoring the case */
                target__iew?: string[];
                /** @description Filter: target doesn't end with, ignoring the case */
                target__niew?: string[];
                /** @description Filter: target matches the (POSIX) regular expression */
                target__regex?: string[];
                /** @description Filter: target matches the (POSIX) regular expression, ignoring the case */
                target__iregex?: string[];
                /** @description Filter: target is empty (null, or an empty string) for true, isn't for false */
                target__empty?: boolean;
                /** @description Filter: comments equal to */
                comments?: string[];
                /** @description Filter: comments not equal to */
                comments__n?: string[];
                /** @description Filter: comments equal to, ignoring the case */
                comments__ie?: string[];
                /** @description Filter: comments not equal to, ignoring the case */
                comments__nie?: string[];
                /** @description Filter: comments contains, ignoring the case */
                comments__ic?: string[];
                /** @description Filter: comments doesn't contain, ignoring the case */
                comments__nic?: string[];
                /** @description Filter: comments starts with, ignoring the case */
                comments__isw?: string[];
                /** @description Filter: comments doesn't start with, ignoring the case */
                comments__nisw?: string[];
                /** @description Filter: comments ends with, ignoring the case */
                comments__iew?: string[];
                /** @description Filter: comments doesn't end with, ignoring the case */
                comments__niew?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression */
                comments__regex?: string[];
                /** @description Filter: comments matches the (POSIX) regular expression, ignoring the case */
                comments__iregex?: string[];
                /** @description Filter: comments is empty (null, or an empty string) for true, isn't for false */
                comments__empty?: boolean;
                /** @description Filter: priority equal to */
                priority?: number[];
                /** @description Filter: priority not equal to */
                priority__n?: number[];
                /** @description Filter: priority lower than */
                priority__lt?: number[];
                /** @description Filter: priority lower than or equal to */
                priority__lte?: number[];
                /** @description Filter: priority greater than */
                priority__gt?: number[];
                /** @description Filter: priority greater than or equal to */
                priority__gte?: number[];
                /** @description Filter: priority is empty (null, or an empty string) for true, isn't for false */
                priority__empty?: boolean;
                /** @description Filter: scheduleType equal to */
                scheduleType?: ("ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY")[];
                /** @description Filter: scheduleType not equal to */
                scheduleType__n?: ("ASAP" | "AT" | "DAILY" | "MONTHLY" | "WEEKLY" | "HOURLY")[];
                /** @description Filter: scheduleType is empty (null, or an empty string) for true, isn't for false */
                scheduleType__empty?: boolean;
                /** @description Filter: scheduleReference equal to */
                scheduleReference?: string[];
                /** @description Filter: scheduleReference not equal to */
                scheduleReference__n?: string[];
                /** @description Filter: scheduleReference lower than */
                scheduleReference__lt?: string[];
                /** @description Filter: scheduleReference lower than or equal to */
                scheduleReference__lte?: string[];
                /** @description Filter: scheduleReference greater than */
                scheduleReference__gt?: string[];
                /** @description Filter: scheduleReference greater than or equal to */
                scheduleReference__gte?: string[];
                /** @description Filter: scheduleReference is empty (null, or an empty string) for true, isn't for false */
                scheduleReference__empty?: boolean;
                /** @description Filter: creationDate equal to */
                creationDate?: string[];
                /** @description Filter: creationDate not equal to */
                creationDate__n?: string[];
                /** @description Filter: creationDate lower than */
                creationDate__lt?: string[];
                /** @description Filter: creationDate lower than or equal to */
                creationDate__lte?: string[];
                /** @description Filter: creationDate greater than */
                creationDate__gt?: string[];
                /** @description Filter: creationDate greater than or equal to */
                creationDate__gte?: string[];
                /** @description Filter: creationDate is empty (null, or an empty string) for true, isn't for false */
                creationDate__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
                /** @description Filter: executionDate equal to */
                executionDate?: string[];
                /** @description Filter: executionDate not equal to */
                executionDate__n?: string[];
                /** @description Filter: executionDate lower than */
                executionDate__lt?: string[];
                /** @description Filter: executionDate lower than or equal to */
                executionDate__lte?: string[];
                /** @description Filter: executionDate greater than */
                executionDate__gt?: string[];
                /** @description Filter: executionDate greater than or equal to */
                executionDate__gte?: string[];
                /** @description Filter: executionDate is empty (null, or an empty string) for true, isn't for false */
                executionDate__empty?: boolean;
                /** @description Filter: device equal to */
                device?: number[];
                /** @description Filter: device not equal to */
                device__n?: number[];
                /** @description Filter: device lower than */
                device__lt?: number[];
                /** @description Filter: device lower than or equal to */
                device__lte?: number[];
                /** @description Filter: device greater than */
                device__gt?: number[];
                /** @description Filter: device greater than or equal to */
                device__gte?: number[];
                /** @description Filter: device is empty (null, or an empty string) for true, isn't for false */
                device__empty?: boolean;
                /** @description Filter: group equal to */
                group?: number[];
                /** @description Filter: group not equal to */
                group__n?: number[];
                /** @description Filter: group lower than */
                group__lt?: number[];
                /** @description Filter: group lower than or equal to */
                group__lte?: number[];
                /** @description Filter: group greater than */
                group__gt?: number[];
                /** @description Filter: group greater than or equal to */
                group__gte?: number[];
                /** @description Filter: group is empty (null, or an empty string) for true, isn't for false */
                group__empty?: boolean;
                /** @description Filter: parentTask equal to */
                parentTask?: number[];
                /** @description Filter: parentTask not equal to */
                parentTask__n?: number[];
                /** @description Filter: parentTask lower than */
                parentTask__lt?: number[];
                /** @description Filter: parentTask lower than or equal to */
                parentTask__lte?: number[];
                /** @description Filter: parentTask greater than */
                parentTask__gt?: number[];
                /** @description Filter: parentTask greater than or equal to */
                parentTask__gte?: number[];
                /** @description Filter: parentTask is empty (null, or an empty string) for true, isn't for false */
                parentTask__empty?: boolean;
                /** @description Filter: childOrder equal to */
                childOrder?: number[];
                /** @description Filter: childOrder not equal to */
                childOrder__n?: number[];
                /** @description Filter: childOrder lower than */
                childOrder__lt?: number[];
                /** @description Filter: childOrder lower than or equal to */
                childOrder__lte?: number[];
                /** @description Filter: childOrder greater than */
                childOrder__gt?: number[];
                /** @description Filter: childOrder greater than or equal to */
                childOrder__gte?: number[];
                /** @description Filter: childOrder is empty (null, or an empty string) for true, isn't for false */
                childOrder__empty?: boolean;
                /** @description Filter: runnerId equal to */
                runnerId?: string[];
                /** @description Filter: runnerId not equal to */
                runnerId__n?: string[];
                /** @description Filter: runnerId equal to, ignoring the case */
                runnerId__ie?: string[];
                /** @description Filter: runnerId not equal to, ignoring the case */
                runnerId__nie?: string[];
                /** @description Filter: runnerId contains, ignoring the case */
                runnerId__ic?: string[];
                /** @description Filter: runnerId doesn't contain, ignoring the case */
                runnerId__nic?: string[];
                /** @description Filter: runnerId starts with, ignoring the case */
                runnerId__isw?: string[];
                /** @description Filter: runnerId doesn't start with, ignoring the case */
                runnerId__nisw?: string[];
                /** @description Filter: runnerId ends with, ignoring the case */
                runnerId__iew?: string[];
                /** @description Filter: runnerId doesn't end with, ignoring the case */
                runnerId__niew?: string[];
                /** @description Filter: runnerId matches the (POSIX) regular expression */
                runnerId__regex?: string[];
                /** @description Filter: runnerId matches the (POSIX) regular expression, ignoring the case */
                runnerId__iregex?: string[];
                /** @description Filter: runnerId is empty (null, or an empty string) for true, isn't for false */
                runnerId__empty?: boolean;
                /** @description Filter: debugEnabled equal to */
                debugEnabled?: boolean[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Stats"];
                    "application/xml": components["schemas"]["Stats"];
                    "application/yaml": components["schemas"]["Stats"];
                };
            };
        };
    };
    getTaskStatusCounts: {
        parameters: {
            query?: {
                /** @description Only count the child tasks of this parent task ID */
                parentTask?: string;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TaskStatusCounts"];
                    "application/xml": components["schemas"]["TaskStatusCounts"];
                    "application/yaml": components["schemas"]["TaskStatusCounts"];
                };
            };
        };
    };
    listUsers: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, username, local, level */
                sort?: string;
                /** @description Free text search; fields: username */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: username equal to */
                username?: string[];
                /** @description Filter: username not equal to */
                username__n?: string[];
                /** @description Filter: username equal to, ignoring the case */
                username__ie?: string[];
                /** @description Filter: username not equal to, ignoring the case */
                username__nie?: string[];
                /** @description Filter: username contains, ignoring the case */
                username__ic?: string[];
                /** @description Filter: username doesn't contain, ignoring the case */
                username__nic?: string[];
                /** @description Filter: username starts with, ignoring the case */
                username__isw?: string[];
                /** @description Filter: username doesn't start with, ignoring the case */
                username__nisw?: string[];
                /** @description Filter: username ends with, ignoring the case */
                username__iew?: string[];
                /** @description Filter: username doesn't end with, ignoring the case */
                username__niew?: string[];
                /** @description Filter: username matches the (POSIX) regular expression */
                username__regex?: string[];
                /** @description Filter: username matches the (POSIX) regular expression, ignoring the case */
                username__iregex?: string[];
                /** @description Filter: username is empty (null, or an empty string) for true, isn't for false */
                username__empty?: boolean;
                /** @description Filter: local equal to */
                local?: boolean[];
                /** @description Filter: level equal to */
                level?: number[];
                /** @description Filter: level not equal to */
                level__n?: number[];
                /** @description Filter: level lower than */
                level__lt?: number[];
                /** @description Filter: level lower than or equal to */
                level__lte?: number[];
                /** @description Filter: level greater than */
                level__gt?: number[];
                /** @description Filter: level greater than or equal to */
                level__gte?: number[];
                /** @description Filter: level is empty (null, or an empty string) for true, isn't for false */
                level__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseUser"];
                    "application/xml": components["schemas"]["ListResponseUser"];
                    "application/yaml": components["schemas"]["ListResponseUser"];
                };
            };
        };
    };
    createUser: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["UserRequest"];
                "application/xml": components["schemas"]["UserRequest"];
                "application/yaml": components["schemas"]["UserRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["User"];
                    "application/xml": components["schemas"]["User"];
                    "application/yaml": components["schemas"]["User"];
                };
            };
        };
    };
    getUser: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description User ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["User"];
                    "application/xml": components["schemas"]["User"];
                    "application/yaml": components["schemas"]["User"];
                };
            };
        };
    };
    replaceUser: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description User ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["UserRequest"];
                "application/xml": components["schemas"]["UserRequest"];
                "application/yaml": components["schemas"]["UserRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["User"];
                    "application/xml": components["schemas"]["User"];
                    "application/yaml": components["schemas"]["User"];
                };
            };
        };
    };
    deleteUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateUser: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description User ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["User"];
                    "application/xml": components["schemas"]["User"];
                    "application/yaml": components["schemas"]["User"];
                };
            };
        };
    };
    listVaultInstances: {
        parameters: {
            query?: {
                /** @description Number of items to skip (paging) */
                offset?: number;
                /** @description Number of items of the page (0: all the items, unless a maximum page size is set) */
                limit?: number;
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate); fields: id, name, type, baseUrl, authMethod, changeDate */
                sort?: string;
                /** @description Free text search; fields: name, baseUrl, namespace */
                q?: string;
                /** @description true to count the matching items (in count; not counted by default) */
                count?: boolean;
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
                /** @description Filter: id equal to */
                id?: number[];
                /** @description Filter: id not equal to */
                id__n?: number[];
                /** @description Filter: id lower than */
                id__lt?: number[];
                /** @description Filter: id lower than or equal to */
                id__lte?: number[];
                /** @description Filter: id greater than */
                id__gt?: number[];
                /** @description Filter: id greater than or equal to */
                id__gte?: number[];
                /** @description Filter: id is empty (null, or an empty string) for true, isn't for false */
                id__empty?: boolean;
                /** @description Filter: name equal to */
                name?: string[];
                /** @description Filter: name not equal to */
                name__n?: string[];
                /** @description Filter: name equal to, ignoring the case */
                name__ie?: string[];
                /** @description Filter: name not equal to, ignoring the case */
                name__nie?: string[];
                /** @description Filter: name contains, ignoring the case */
                name__ic?: string[];
                /** @description Filter: name doesn't contain, ignoring the case */
                name__nic?: string[];
                /** @description Filter: name starts with, ignoring the case */
                name__isw?: string[];
                /** @description Filter: name doesn't start with, ignoring the case */
                name__nisw?: string[];
                /** @description Filter: name ends with, ignoring the case */
                name__iew?: string[];
                /** @description Filter: name doesn't end with, ignoring the case */
                name__niew?: string[];
                /** @description Filter: name matches the (POSIX) regular expression */
                name__regex?: string[];
                /** @description Filter: name matches the (POSIX) regular expression, ignoring the case */
                name__iregex?: string[];
                /** @description Filter: name is empty (null, or an empty string) for true, isn't for false */
                name__empty?: boolean;
                /** @description Filter: type equal to */
                type?: "HASHICORP_KV2"[];
                /** @description Filter: type not equal to */
                type__n?: "HASHICORP_KV2"[];
                /** @description Filter: type is empty (null, or an empty string) for true, isn't for false */
                type__empty?: boolean;
                /** @description Filter: baseUrl equal to */
                baseUrl?: string[];
                /** @description Filter: baseUrl not equal to */
                baseUrl__n?: string[];
                /** @description Filter: baseUrl equal to, ignoring the case */
                baseUrl__ie?: string[];
                /** @description Filter: baseUrl not equal to, ignoring the case */
                baseUrl__nie?: string[];
                /** @description Filter: baseUrl contains, ignoring the case */
                baseUrl__ic?: string[];
                /** @description Filter: baseUrl doesn't contain, ignoring the case */
                baseUrl__nic?: string[];
                /** @description Filter: baseUrl starts with, ignoring the case */
                baseUrl__isw?: string[];
                /** @description Filter: baseUrl doesn't start with, ignoring the case */
                baseUrl__nisw?: string[];
                /** @description Filter: baseUrl ends with, ignoring the case */
                baseUrl__iew?: string[];
                /** @description Filter: baseUrl doesn't end with, ignoring the case */
                baseUrl__niew?: string[];
                /** @description Filter: baseUrl matches the (POSIX) regular expression */
                baseUrl__regex?: string[];
                /** @description Filter: baseUrl matches the (POSIX) regular expression, ignoring the case */
                baseUrl__iregex?: string[];
                /** @description Filter: baseUrl is empty (null, or an empty string) for true, isn't for false */
                baseUrl__empty?: boolean;
                /** @description Filter: namespace equal to */
                namespace?: string[];
                /** @description Filter: namespace not equal to */
                namespace__n?: string[];
                /** @description Filter: namespace equal to, ignoring the case */
                namespace__ie?: string[];
                /** @description Filter: namespace not equal to, ignoring the case */
                namespace__nie?: string[];
                /** @description Filter: namespace contains, ignoring the case */
                namespace__ic?: string[];
                /** @description Filter: namespace doesn't contain, ignoring the case */
                namespace__nic?: string[];
                /** @description Filter: namespace starts with, ignoring the case */
                namespace__isw?: string[];
                /** @description Filter: namespace doesn't start with, ignoring the case */
                namespace__nisw?: string[];
                /** @description Filter: namespace ends with, ignoring the case */
                namespace__iew?: string[];
                /** @description Filter: namespace doesn't end with, ignoring the case */
                namespace__niew?: string[];
                /** @description Filter: namespace matches the (POSIX) regular expression */
                namespace__regex?: string[];
                /** @description Filter: namespace matches the (POSIX) regular expression, ignoring the case */
                namespace__iregex?: string[];
                /** @description Filter: namespace is empty (null, or an empty string) for true, isn't for false */
                namespace__empty?: boolean;
                /** @description Filter: authMethod equal to */
                authMethod?: ("APPROLE" | "JWT")[];
                /** @description Filter: authMethod not equal to */
                authMethod__n?: ("APPROLE" | "JWT")[];
                /** @description Filter: authMethod is empty (null, or an empty string) for true, isn't for false */
                authMethod__empty?: boolean;
                /** @description Filter: changeDate equal to */
                changeDate?: string[];
                /** @description Filter: changeDate not equal to */
                changeDate__n?: string[];
                /** @description Filter: changeDate lower than */
                changeDate__lt?: string[];
                /** @description Filter: changeDate lower than or equal to */
                changeDate__lte?: string[];
                /** @description Filter: changeDate greater than */
                changeDate__gt?: string[];
                /** @description Filter: changeDate greater than or equal to */
                changeDate__gte?: string[];
                /** @description Filter: changeDate is empty (null, or an empty string) for true, isn't for false */
                changeDate__empty?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListResponseVaultInstance"];
                    "application/xml": components["schemas"]["ListResponseVaultInstance"];
                    "application/yaml": components["schemas"]["ListResponseVaultInstance"];
                };
            };
        };
    };
    createVaultInstance: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["VaultInstanceRequest"];
                "application/xml": components["schemas"]["VaultInstanceRequest"];
                "application/yaml": components["schemas"]["VaultInstanceRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VaultInstance"];
                    "application/xml": components["schemas"]["VaultInstance"];
                    "application/yaml": components["schemas"]["VaultInstance"];
                };
            };
        };
    };
    getVaultInstance: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Vault instance ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VaultInstance"];
                    "application/xml": components["schemas"]["VaultInstance"];
                    "application/yaml": components["schemas"]["VaultInstance"];
                };
            };
        };
    };
    replaceVaultInstance: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Vault instance ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["VaultInstanceRequest"];
                "application/xml": components["schemas"]["VaultInstanceRequest"];
                "application/yaml": components["schemas"]["VaultInstanceRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VaultInstance"];
                    "application/xml": components["schemas"]["VaultInstance"];
                    "application/yaml": components["schemas"]["VaultInstance"];
                };
            };
        };
    };
    deleteVaultInstance: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Vault instance ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": unknown;
                };
            };
        };
    };
    updateVaultInstance: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Vault instance ID */
                id: number;
            };
            cookie?: never;
        };
        /** @description The properties to change */
        requestBody?: {
            content: {
                "application/merge-patch+json": components["schemas"]["JsonNode"];
                "application/json": components["schemas"]["JsonNode"];
                "application/xml": components["schemas"]["JsonNode"];
                "application/yaml": components["schemas"]["JsonNode"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VaultInstance"];
                    "application/xml": components["schemas"]["VaultInstance"];
                    "application/yaml": components["schemas"]["VaultInstance"];
                };
            };
        };
    };
    testVaultInstance: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path: {
                /** @description Vault instance ID */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VaultConnectionTest"];
                    "application/xml": components["schemas"]["VaultConnectionTest"];
                    "application/yaml": components["schemas"]["VaultConnectionTest"];
                };
            };
        };
    };
    testVaultInstanceDefinition: {
        parameters: {
            query?: {
                /** @description View of the items: brief or full */
                view?: "brief" | "full";
                /** @description Properties to write, comma-separated (dotted paths for nested ones) */
                fields?: string;
                /** @description Properties not to write, comma-separated */
                omit?: string;
                /** @description References to write in their full view (written even when the view or the fields don't include them), comma-separated */
                expand?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["VaultInstanceRequest"];
                "application/xml": components["schemas"]["VaultInstanceRequest"];
                "application/yaml": components["schemas"]["VaultInstanceRequest"];
            };
        };
        responses: {
            /** @description default response */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VaultConnectionTest"];
                    "application/xml": components["schemas"]["VaultConnectionTest"];
                    "application/yaml": components["schemas"]["VaultConnectionTest"];
                };
            };
        };
    };
}

/** The brief view of each representation (the default view of the lists). */
export interface briefs {
    ApiTokenCreated: Pick<components["schemas"]["ApiTokenCreated"], "id" | "url" | "display">;
    ApiToken: Pick<components["schemas"]["ApiToken"], "id" | "url" | "display" | "description" | "level">;
    CurrentUser: Pick<components["schemas"]["CurrentUser"], "id" | "url" | "display">;
    Session: Pick<components["schemas"]["Session"], "url">;
    CredentialSet: Pick<components["schemas"]["CredentialSet"], "id" | "url" | "display" | "name" | "type" | "mgmtDomain">;
    Device: Pick<components["schemas"]["Device"], "id" | "url" | "display" | "name" | "mgmtAddress" | "status" | "family" | "driver" | "driverDescription" | "networkClass" | "softwareLevel" | "compliant" | "endOfLife" | "endOfSale">;
    DeviceComplianceResult: Pick<components["schemas"]["DeviceComplianceResult"], "device" | "rule" | "policy" | "result" | "checkDate">;
    Domain: Pick<components["schemas"]["Domain"], "id" | "url" | "display" | "name" | "description" | "server4Address">;
    Group: Pick<components["schemas"]["Group"], "id" | "url" | "display" | "name" | "folder" | "type">;
    JavaScriptRule: Pick<components["schemas"]["JavaScriptRule"], "id" | "url" | "display" | "type" | "name" | "enabled" | "policy">;
    Module: Pick<components["schemas"]["Module"], "id">;
    Policy: Pick<components["schemas"]["Policy"], "id" | "url" | "display" | "name">;
    PythonRule: Pick<components["schemas"]["PythonRule"], "id" | "url" | "display" | "type" | "name" | "enabled" | "policy">;
    TextRule: Pick<components["schemas"]["TextRule"], "id" | "url" | "display" | "type" | "name" | "enabled" | "policy">;
    VaultInstance: Pick<components["schemas"]["VaultInstance"], "id" | "url" | "display" | "name" | "type" | "baseUrl" | "httpsCaTrustMode" | "authMethod">;
    HardwareRule: Pick<components["schemas"]["HardwareRule"], "id" | "url" | "display" | "partNumber" | "endOfSale" | "endOfLife" | "group" | "driver" | "family" | "familyRegExp" | "partNumberRegExp">;
    Exemption: Pick<components["schemas"]["Exemption"], "device" | "expirationDate">;
    SoftwareRule: Pick<components["schemas"]["SoftwareRule"], "id" | "url" | "display" | "priority" | "level" | "group" | "driver" | "family" | "familyRegExp" | "softwareVersion" | "softwareVersionRegExp" | "partNumber" | "partNumberRegExp">;
    BinaryFile: Pick<components["schemas"]["BinaryFile"], "url">;
    Config: Pick<components["schemas"]["Config"], "id" | "url" | "display" | "device" | "changeDate" | "author">;
    LongText: Pick<components["schemas"]["LongText"], "url">;
    DeviceFamily: Pick<components["schemas"]["DeviceFamily"], "driver" | "family" | "deviceCount">;
    DeviceInterface: Pick<components["schemas"]["DeviceInterface"], "id" | "display" | "name" | "description" | "enabled">;
    DeviceModule: Pick<components["schemas"]["DeviceModule"], "id" | "display" | "slot" | "partNumber" | "serialNumber">;
    Task: Pick<components["schemas"]["Task"], "id" | "url" | "display" | "type" | "status">;
    DeviceTag: Pick<components["schemas"]["DeviceTag"], "name" | "deviceCount">;
    DeviceType: Pick<components["schemas"]["DeviceType"], "name" | "description" | "version">;
    PartNumber: Pick<components["schemas"]["PartNumber"], "partNumber" | "moduleCount">;
    SoftwareVersion: Pick<components["schemas"]["SoftwareVersion"], "version" | "deviceCount">;
    Diagnostic: Pick<components["schemas"]["Diagnostic"], "id" | "url" | "display" | "name" | "type" | "resultType" | "enabled" | "targetGroup">;
    DiagnosticResult: Pick<components["schemas"]["DiagnosticResult"], "display" | "diagnostic" | "type" | "value">;
    WebHook: Pick<components["schemas"]["WebHook"], "id" | "url" | "display" | "type" | "name" | "enabled" | "targetUrl" | "httpsCaTrustMode">;
    Script: Pick<components["schemas"]["Script"], "id" | "url" | "display" | "name" | "folder" | "deviceDriver" | "deviceDriverDescription" | "author">;
    ClusterMember: Pick<components["schemas"]["ClusterMember"], "display">;
    Service: Pick<components["schemas"]["Service"], "display">;
    User: Pick<components["schemas"]["User"], "id" | "url" | "display" | "username" | "local" | "level">;
}
