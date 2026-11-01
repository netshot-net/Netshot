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

const header = "// Generated from openapi.json by `npm run api:types`: do not edit.\n\n"
fs.writeFileSync(target, header + astToString(ast))
console.log(`Generated ${target.pathname}`)
