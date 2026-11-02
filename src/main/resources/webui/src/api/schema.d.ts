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
         * @description Lists the API tokens (never their values), in their brief view by default. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Counts the compliance results matching the filters of the list, by the values of up to two fields (groupBy: result, policy, rule, device, group, domain) and/or by time bucket (interval: hour, day, week or month, on checkDate, from/to, timeZone). A result is counted once per group of its device. Any other parameter is a filter of the list.
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
         * @description Lists the results of the last compliance checks, one per device and rule (a rule never checked on a device has no result here), with the exemption of the device from the rule. Filters: device, deviceName, domain and group (of the device), rule, ruleName, policy, policyName, result, checkDate, exemptionExpirationDate; q searches the device, rule and policy names. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the rules of the policies applying to a device (through its groups) with the result of their last check (none if never checked) and the exemption of the device. (unlike the list across devices, which only has the checked ones). Filters: rule, ruleName, policy, policyName, result, checkDate, exemptionExpirationDate. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the hardware compliance rules, in their brief view by default. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Replaces the definition of a hardware rule.
         */
        put: operations["replaceHardwareRule"];
        post?: never;
        /** Delete a hardware rule */
        delete: operations["deleteHardwareRule"];
        options?: never;
        head?: never;
        /**
         * Update a hardware rule
         * @description Changes some properties of a hardware rule (JSON merge patch, RFC 7396).
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
         * @description Lists the compliance policies, in their brief view by default. The targetGroup filter gives the policies applying to a group. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Replaces the name and the target groups of a policy. The compliance results of the devices of a group removed from the policy are deleted.
         */
        put: operations["replacePolicy"];
        post?: never;
        /**
         * Delete a compliance policy
         * @description Deletes a policy, with its rules, their exemptions and results.
         */
        delete: operations["deletePolicy"];
        options?: never;
        head?: never;
        /**
         * Update a compliance policy
         * @description Changes some properties of a policy (JSON merge patch, RFC 7396).
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
         * @description Lists the rules of a policy, as the rule list filtered on the policy (same parameters). Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the compliance rules of all the policies, in their brief view by default (the type, TEXT, JAVASCRIPT or PYTHON, tells which other properties a rule has). Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Replaces the definition of a rule (its type and its policy can't change; its exemptions are unchanged).
         */
        put: operations["replaceRule"];
        post?: never;
        /**
         * Delete a compliance rule
         * @description Deletes a rule, with its exemptions and results.
         */
        delete: operations["deleteRule"];
        options?: never;
        head?: never;
        /**
         * Update a compliance rule
         * @description Changes some properties of a rule (JSON merge patch, RFC 7396).
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
         * @description Lists the devices exempted from a rule, with the date their exemption ends. Filters: device, expirationDate. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
         */
        get: operations["listExemptions"];
        /**
         * Replace the exemptions of a rule
         * @description Replaces the whole set of devices exempted from a rule (an empty list removes them all), and answers the new set. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the software compliance rules, in the order they are checked in by default (priority is the position of the rule, from 1). Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Replaces the definition of a software rule. A priority moves the rule to that position (the other rules shift); without priority the rule keeps its position.
         */
        put: operations["replaceSoftwareRule"];
        post?: never;
        /**
         * Delete a software rule
         * @description Deletes a software rule (the rules after it move up).
         */
        delete: operations["deleteSoftwareRule"];
        options?: never;
        head?: never;
        /**
         * Update a software rule
         * @description Changes some properties of a software rule (JSON merge patch, RFC 7396), possibly its priority (position).
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
         * @description Counts the configurations (the configuration changes) matching the filters of the configuration list, by device and/or author (groupBy) and/or by time bucket (interval: hour, day, week or month, on changeDate, from/to, timeZone). Any other parameter is a filter of the list.
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
         * @description Lists the configurations of all the devices, in their brief view by default (device, date, author), the latest first. The attributes are only there in the full view (or with fields=attributes), and never hold the long texts and files: download them from their url. The filters on domain and group apply to the device of the configuration. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the configurations of a device, as the configuration list filtered on the device (same parameters), the latest first. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the global credential sets, in their brief view by default. The credential sets specific to a device access are left out, unless the deviceSpecific filter is given (deviceSpecific=true lists only those, deviceSpecific=false only the global ones). The secrets are never returned. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the distinct device families (driver and family) of the devices, with their number of devices. An aggregate: items have no id nor url. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the network interfaces of a device, in their brief view by default (name, description, enabled; view=full adds the addresses). Besides the filters on the fields, ipAddress (exact or net lookup) and macAddress filter on the addresses. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the hardware modules of a device, in their brief view by default (slot, part number, serial number): the current ones only, unless removed=true, which lists the modules removed from the device (kept for the history) too, flagged removed. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the devices, in their brief view by default. Besides the filters on the fields, the query parameter takes a device search query (such as [Family] is "Cisco IOS" and [Location] contains "Paris"), and group, tag and compliant filter on the group membership, the tags and the configuration compliance. Like any list, it can be exported as CSV (Accept: text/csv) or Excel (Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet). With the Excel format, the include parameter writes a workbook with more sheets for the matching devices. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Counts the devices matching the filters of the device list (and the query), by the values of up to two fields (groupBy: status, mgmtDomain, family, driver, networkClass, softwareVersion, softwareLevel, location, eolDate, eosDate, group, tag, compliant) and/or by time bucket (interval: hour, day, week or month, on createdDate, changeDate, eolDate or eosDate, from/to, timeZone). Any other parameter is a filter of the list.
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
         * @description Lists the distinct tags attached to at least one device, with their number of devices. An aggregate: items have no id nor url. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the device types: the device drivers loaded by the server, in their brief view by default (name, description, version). View full adds the attributes, options, accesses... Items have no id nor url: the name is what the devices refer to. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the distinct part numbers of the modules, with their number of modules. An aggregate: items have no id nor url. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the distinct (non empty) software versions of the devices, with their number of devices. An aggregate: items have no id nor url. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the diagnostics, in their brief view by default. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Replaces the definition of a diagnostic (its type can't change). The results of the diagnostic are deleted when its target group changes.
         */
        put: operations["replaceDiagnostic"];
        post?: never;
        /**
         * Delete a diagnostic
         * @description Deletes a diagnostic, with its results.
         */
        delete: operations["deleteDiagnostic"];
        options?: never;
        head?: never;
        /**
         * Update a diagnostic
         * @description Changes some properties of a diagnostic (JSON merge patch, RFC 7396).
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
         * @description Lists the last result of each diagnostic run on a device, with the diagnostic as a reference. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the device domains, in their brief view by default. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the device groups, in their brief view by default. The device filter gives the groups a device is a member of. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the devices of a group, as the device list filtered on the group (same parameters). Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the hooks, in their brief view by default. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Replaces the definition of a hook (its type can't change). Hooks have no version: there is no ETag.
         */
        put: operations["replaceHook"];
        post?: never;
        /** Delete a hook */
        delete: operations["deleteHook"];
        options?: never;
        head?: never;
        /**
         * Update a hook
         * @description Changes some properties of a hook (JSON merge patch, RFC 7396; the triggers are replaced as a whole).
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
         * @description Lists the command scripts, in their brief view by default (without the code). The user inputs are only extracted (by evaluating the scripts) when they are asked for. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
        put?: never;
        post?: never;
        /** Delete a script */
        delete: operations["deleteScript"];
        options?: never;
        head?: never;
        patch?: never;
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
         * @description Lists the tasks targeting a device, as the task list filtered on the device (same parameters). Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Lists the tasks, in their brief view by default, the latest first. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Counts the tasks matching the filters of the task list, by the values of up to two fields (groupBy: type, status, author, scheduleType, device, group, runnerId) and/or by time bucket (interval: hour, day, week or month, on executionDate (default), creationDate, changeDate or scheduleReference, from/to, timeZone). Without execution date, a task isn't counted by executionDate. Any other parameter is a filter of the list.
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
         * @description Lists the Netshot users, in their brief view by default. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
         * @description Replaces the definition of a user. An absent password is unchanged (it is required to make a remote user a local one). A user can't edit itself (403). Users have no version: there is no ETag.
         */
        put: operations["replaceUser"];
        post?: never;
        /**
         * Delete a user
         * @description Deletes a user. A user can't delete itself (403).
         */
        delete: operations["deleteUser"];
        options?: never;
        head?: never;
        /**
         * Update a user
         * @description Changes some properties of a user (JSON merge patch, RFC 7396). The password is changed by giving one. A user can't edit itself (403).
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
         * @description Lists the Vault instances, in their brief view by default. The secrets are never returned. Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).
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
            description: string | null;
            /** Format: int32 */
            level: number;
            readonly role: string | null;
        };
        ListResponseApiToken: {
            /**
             * Format: int64
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
            name: string | null;
            targetGroups: components["schemas"]["Group"][] | null;
        };
        /** @description Compliance rule checked by a Python script */
        PythonRule: {
            /** Format: int64 */
            id: number;
            url: string | null;
            display: string | null;
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
            /** Format: int32 */
            priority: number;
            /** @enum {string|null} */
            level: "GOLD" | "SILVER" | "BRONZE" | "UNKNOWN" | null;
            group: components["schemas"]["Group"] | null;
            driver: string | null;
            family: string | null;
            familyRegExp: boolean;
            version: string | null;
            versionRegExp: boolean;
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
            version?: string | null;
            versionRegExp?: boolean | null;
            partNumber?: string | null;
            partNumberRegExp?: boolean | null;
        };
        ListResponseSoftwareRule: {
            /**
             * Format: int64
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
             * @description Total number of matching items (null if count=false)
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseApiToken"];
                    "application/xml": components["schemas"]["ListResponseApiToken"];
                    "application/yaml": components["schemas"]["ListResponseApiToken"];
                };
            };
        };
    };
    createApiToken: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["CurrentUser"];
                    "application/xml": components["schemas"]["CurrentUser"];
                    "application/yaml": components["schemas"]["CurrentUser"];
                };
            };
        };
    };
    changePassword: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Session"];
                    "application/xml": components["schemas"]["Session"];
                    "application/yaml": components["schemas"]["Session"];
                };
            };
        };
    };
    login: {
        parameters: {
            query?: never;
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
                /** @description Fields to count by (up to two), comma-separated */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseHardwareRule"];
                    "application/xml": components["schemas"]["ListResponseHardwareRule"];
                    "application/yaml": components["schemas"]["ListResponseHardwareRule"];
                };
            };
        };
    };
    createHardwareRule: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["HardwareRule"];
                    "application/xml": components["schemas"]["HardwareRule"];
                    "application/yaml": components["schemas"]["HardwareRule"];
                };
            };
        };
    };
    replaceHardwareRule: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponsePolicy"];
                    "application/xml": components["schemas"]["ListResponsePolicy"];
                    "application/yaml": components["schemas"]["ListResponsePolicy"];
                };
            };
        };
    };
    createPolicy: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Policy"];
                    "application/xml": components["schemas"]["Policy"];
                    "application/yaml": components["schemas"]["Policy"];
                };
            };
        };
    };
    replacePolicy: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseRule"];
                    "application/xml": components["schemas"]["ListResponseRule"];
                    "application/yaml": components["schemas"]["ListResponseRule"];
                };
            };
        };
    };
    createRule: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Rule"];
                    "application/xml": components["schemas"]["Rule"];
                    "application/yaml": components["schemas"]["Rule"];
                };
            };
        };
    };
    replaceRule: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseSoftwareRule"];
                    "application/xml": components["schemas"]["ListResponseSoftwareRule"];
                    "application/yaml": components["schemas"]["ListResponseSoftwareRule"];
                };
            };
        };
    };
    createSoftwareRule: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["SoftwareRule"];
                    "application/xml": components["schemas"]["SoftwareRule"];
                    "application/yaml": components["schemas"]["SoftwareRule"];
                };
            };
        };
    };
    replaceSoftwareRule: {
        parameters: {
            query?: never;
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
            query?: never;
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
            query?: never;
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
                /** @description Fields to count by (up to two), comma-separated */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseCredentialSet"];
                    "application/xml": components["schemas"]["ListResponseCredentialSet"];
                    "application/yaml": components["schemas"]["ListResponseCredentialSet"];
                };
            };
        };
    };
    createCredentialSet: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["CredentialSet"];
                    "application/xml": components["schemas"]["CredentialSet"];
                    "application/yaml": components["schemas"]["CredentialSet"];
                };
            };
        };
    };
    replaceCredentialSet: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseDevice"];
                    "application/xml": components["schemas"]["ListResponseDevice"];
                    "application/yaml": components["schemas"]["ListResponseDevice"];
                };
            };
        };
    };
    addDevice: {
        parameters: {
            query?: never;
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
            query?: never;
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
                    "application/json": components["schemas"]["Device"];
                    "application/xml": components["schemas"]["Device"];
                    "application/yaml": components["schemas"]["Device"];
                };
            };
        };
    };
    replaceDevice: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Fields to count by (up to two), comma-separated */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
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
            query?: never;
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
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseDiagnostic"];
                    "application/xml": components["schemas"]["ListResponseDiagnostic"];
                    "application/yaml": components["schemas"]["ListResponseDiagnostic"];
                };
            };
        };
    };
    createDiagnostic: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Diagnostic"];
                    "application/xml": components["schemas"]["Diagnostic"];
                    "application/yaml": components["schemas"]["Diagnostic"];
                };
            };
        };
    };
    replaceDiagnostic: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseDomain"];
                    "application/xml": components["schemas"]["ListResponseDomain"];
                    "application/yaml": components["schemas"]["ListResponseDomain"];
                };
            };
        };
    };
    createDomain: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Domain"];
                    "application/xml": components["schemas"]["Domain"];
                    "application/yaml": components["schemas"]["Domain"];
                };
            };
        };
    };
    replaceDomain: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseGroup"];
                    "application/xml": components["schemas"]["ListResponseGroup"];
                    "application/yaml": components["schemas"]["ListResponseGroup"];
                };
            };
        };
    };
    createGroup: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Group"];
                    "application/xml": components["schemas"]["Group"];
                    "application/yaml": components["schemas"]["Group"];
                };
            };
        };
    };
    replaceGroup: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseHook"];
                    "application/xml": components["schemas"]["ListResponseHook"];
                    "application/yaml": components["schemas"]["ListResponseHook"];
                };
            };
        };
    };
    createHook: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Hook"];
                    "application/xml": components["schemas"]["Hook"];
                    "application/yaml": components["schemas"]["Hook"];
                };
            };
        };
    };
    replaceHook: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseScript"];
                    "application/xml": components["schemas"]["ListResponseScript"];
                    "application/yaml": components["schemas"]["ListResponseScript"];
                };
            };
        };
    };
    createScript: {
        parameters: {
            query?: never;
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
    validateScript: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["Health"];
                    "application/xml": components["schemas"]["Health"];
                    "application/yaml": components["schemas"]["Health"];
                };
            };
        };
    };
    getClusterMaster: {
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
                    "application/json": components["schemas"]["ClusterMaster"];
                    "application/xml": components["schemas"]["ClusterMaster"];
                    "application/yaml": components["schemas"]["ClusterMaster"];
                };
            };
        };
    };
    getInfo: {
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseTask"];
                    "application/xml": components["schemas"]["ListResponseTask"];
                    "application/yaml": components["schemas"]["ListResponseTask"];
                };
            };
        };
    };
    cancelTask: {
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseTask"];
                    "application/xml": components["schemas"]["ListResponseTask"];
                    "application/yaml": components["schemas"]["ListResponseTask"];
                };
            };
        };
    };
    createTask: {
        parameters: {
            query?: never;
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
                /** @description Fields to count by (up to two), comma-separated */
                groupBy?: string;
                /** @description Size of the time buckets */
                interval?: "hour" | "day" | "week" | "month";
                /** @description Date field of the time buckets */
                on?: string;
                /** @description Start of the range (included) */
                from?: Date;
                /** @description End of the range (excluded) */
                to?: Date;
                /** @description Time zone of the buckets (IANA name, UTC by default) */
                timeZone?: string;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseUser"];
                    "application/xml": components["schemas"]["ListResponseUser"];
                    "application/yaml": components["schemas"]["ListResponseUser"];
                };
            };
        };
    };
    createUser: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["User"];
                    "application/xml": components["schemas"]["User"];
                    "application/yaml": components["schemas"]["User"];
                };
            };
        };
    };
    replaceUser: {
        parameters: {
            query?: never;
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
            query?: never;
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
                /** @description Sort fields, comma-separated, - for a descending order (name,-changeDate) */
                sort?: string;
                /** @description Free text search */
                q?: string;
                /** @description false to skip the count of the matching items */
                count?: boolean;
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
                    "application/json": components["schemas"]["ListResponseVaultInstance"];
                    "application/xml": components["schemas"]["ListResponseVaultInstance"];
                    "application/yaml": components["schemas"]["ListResponseVaultInstance"];
                };
            };
        };
    };
    createVaultInstance: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["VaultInstance"];
                    "application/xml": components["schemas"]["VaultInstance"];
                    "application/yaml": components["schemas"]["VaultInstance"];
                };
            };
        };
    };
    replaceVaultInstance: {
        parameters: {
            query?: never;
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
            query?: never;
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
                    "application/json": components["schemas"]["VaultConnectionTest"];
                    "application/xml": components["schemas"]["VaultConnectionTest"];
                    "application/yaml": components["schemas"]["VaultConnectionTest"];
                };
            };
        };
    };
    testVaultInstanceDefinition: {
        parameters: {
            query?: never;
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
    SoftwareRule: Pick<components["schemas"]["SoftwareRule"], "id" | "url" | "display" | "priority" | "level" | "group" | "driver" | "family" | "familyRegExp" | "version" | "versionRegExp" | "partNumber" | "partNumberRegExp">;
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
