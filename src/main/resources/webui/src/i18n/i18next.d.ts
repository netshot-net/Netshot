import "i18next"
import type en from "./en.json"

// Types the translation keys from the English resources: t() refuses an unknown key.
declare module "i18next" {
  interface CustomTypeOptions {
    defaultNS: "translation"
    resources: typeof en
  }
}
