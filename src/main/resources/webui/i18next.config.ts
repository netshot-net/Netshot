import { defineConfig } from "i18next-cli"

export default defineConfig({
  locales: ["en", "fr"],
  extract: {
    input: "src/**/*.{js,jsx,ts,tsx}",
    output: "src/i18n/{{language}}.json",
    // Both files hold all the namespaces (a single "translation" root)
    mergeNamespaces: true,
    // tKey marks a key stored for a later t() call (see src/i18n/tKey.ts)
    functions: ["t", "*.t", "tKey"],
    // Keep the existing key order of the resource files
    sort: false,
  },
})
