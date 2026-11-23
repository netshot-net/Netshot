import i18n from "@/i18n"
import { tKey, type TranslationKey } from "@/i18n/tKey"

const IPV4_PATTERN =
  /^(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d)$/;
const IPV6_PATTERN = /^[0-9a-fA-F]*:[0-9a-fA-F:]*$/;
const HOSTNAME_PATTERN =
  /^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/;

export default {
  ip(message?: TranslationKey) {
    return {
      pattern: {
        value:
          /(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d{1})\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d{1})/g,
        message: i18n.t(message || tKey("common.thisIsNotAValidIpAddress")),
      },
    };
  },
  // Accepts an IPv4 literal, an IPv6 literal, or a hostname/FQDN (the mgmt/connect
  // address is resolved lazily at connect time, so this is a syntax-only check).
  hostOrIp(message?: TranslationKey) {
    return {
      validate: (value: string) =>
        IPV4_PATTERN.test(value) ||
        IPV6_PATTERN.test(value) ||
        HOSTNAME_PATTERN.test(value) ||
        i18n.t(message || tKey("common.thisIsNotAValidIpAddressOrHostname")),
    };
  },
}