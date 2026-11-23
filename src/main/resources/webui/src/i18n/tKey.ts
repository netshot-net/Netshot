import type { ParseKeys } from "i18next"

/**
 * Marks a translation key stored for a later t() call (in a config object, an option list...),
 * so that i18next-cli sees it as used and TypeScript checks it exists. Returns the key unchanged.
 * For a plural key, pass `{ count: 0 }`: the count is ignored here, but tells i18next-cli
 * to look for the plural forms (key_one, key_other).
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars -- only read by i18next-cli
export function tKey<K extends ParseKeys>(key: K, _plural?: { count: number }): K {
  return key
}

/** A translation key of the English resources. */
export type TranslationKey = ParseKeys
