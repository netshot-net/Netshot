import eslintConfigPrettier from "eslint-config-prettier/flat"
import globals from "globals"
import eslintReact from "@eslint-react/eslint-plugin";
import eslintJs from "@eslint/js";
import { defineConfig } from "eslint/config"
import tseslint from "typescript-eslint"

export default defineConfig([
  {
    files: ["**/*.ts", "**/*.tsx"],
    extends: [
      eslintJs.configs.recommended,
      tseslint.configs.recommended,
      eslintReact.configs["recommended-typescript"],
      eslintConfigPrettier,
    ],
    // Configure language/parsing options
    languageOptions: {
      // Use TypeScript ESLint parser for TypeScript files
      parser: tseslint.parser,
      globals: globals.browser,
      parserOptions: {
        // Enable project service for better TypeScript integration
        projectService: {
          // Root-level config files aren't part of tsconfig.json's "src"
          // include, so type-check them against the default project instead
          allowDefaultProject: ["*.ts", "*.d.ts"],
        },
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },

  // --- Architecture boundaries -------------------------------------------
  // Layers, from lowest to highest:
  //   1. api/ + types/          backend contract mirror
  //   2. shared: components/, hooks/, utils/, constants/
  //   3. capabilities: i18n/, theme/, dialog/, contexts/
  //   4. features/<domain>/     owns a domain end to end
  // A layer may only import from layers below it. These rules enforce that
  // nothing below features/ reaches back up into it.
  {
    files: [
      "src/api/**/*.{ts,tsx}",
      "src/types/**/*.{ts,tsx}",
      "src/utils/**/*.{ts,tsx}",
      "src/constants/**/*.{ts,tsx}",
      "src/hooks/**/*.{ts,tsx}",
      "src/dialog/**/*.{ts,tsx}",
      "src/i18n/**/*.{ts,tsx}",
      "src/theme/**/*.{ts,tsx}",
      "src/contexts/**/*.{ts,tsx}",
    ],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/features", "@/features/*"],
              message:
                "Lower layers must not import from features/. Move the shared piece down (components/entity for logic-free entity rendering) or the domain-specific piece into the feature that owns it.",
            },
          ],
        },
      ],
    },
  },

  // Shared components: same rule. queryBuilder is a known, documented
  // exception - it composes option data from four different domains and needs
  // those providers injected before it can obey the boundary.
  {
    files: ["src/components/**/*.{ts,tsx}"],
    ignores: ["src/components/queryBuilder/**", "src/api/schema.d.ts"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/features", "@/features/*"],
              message:
                "Shared components must not import from features/. If it renders a domain entity without any domain logic, put it in components/entity instead.",
            },
          ],
        },
      ],
    },
  },

  // components/entity holds logic-free renderings of @/types entities, so it
  // is safe for every feature to depend on. It must stay free of data access.
  {
    files: ["src/components/entity/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/features", "@/features/*", "@/api", "@/api/*"],
              message:
                "components/entity must stay presentational: no data fetching and no feature imports. Anything that queries the API belongs in the owning feature.",
            },
          ],
        },
      ],
    },
  },
])
