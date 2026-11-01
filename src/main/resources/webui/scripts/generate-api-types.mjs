// Generates the TypeScript types of the REST API (src/api/schema.d.ts) from the snapshot of its OpenAPI document
// (src/api/openapi.json, kept current by V1OpenApiSnapshotTest on the server side).
// The dates (format date-time) are typed as Date: the API client converts them when it reads a response.
import fs from "node:fs"

import openapiTS, { astToString } from "openapi-typescript"
import ts from "typescript"

const DATE = ts.factory.createTypeReferenceNode(ts.factory.createIdentifier("Date"))
const NULL = ts.factory.createLiteralTypeNode(ts.factory.createNull())

const source = new URL("../src/api/openapi.json", import.meta.url)
const target = new URL("../src/api/schema.d.ts", import.meta.url)

const ast = await openapiTS(source, {
  transform(schemaObject) {
    if (schemaObject.format === "date-time") {
      return schemaObject.nullable ? ts.factory.createUnionTypeNode([DATE, NULL]) : DATE
    }
    return undefined
  },
})

// The brief view of each representation (the default view of the lists): only these properties are present
const document = JSON.parse(fs.readFileSync(source, "utf8"))
const briefs = Object.entries(document.components?.schemas ?? {})
  .filter(([, schema]) => Array.isArray(schema["x-netshot-brief"]) && schema["x-netshot-brief"].length > 0)
  .map(([name, schema]) => {
    const keys = schema["x-netshot-brief"].map((key) => JSON.stringify(key)).join(" | ")
    return `    ${name}: Pick<components["schemas"]["${name}"], ${keys}>;`
  })
const briefInterface = "\n/** The brief view of each representation (the default view of the lists). */\n" +
  "export interface briefs {\n" + briefs.join("\n") + "\n}\n"

const header = "// Generated from openapi.json by `npm run api:types`: do not edit.\n\n"
fs.writeFileSync(target, header + astToString(ast) + briefInterface)
console.log(`Generated ${target.pathname}`)
