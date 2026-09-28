import { ApiReferenceReact } from "@scalar/api-reference-react"
import { Stack } from "@chakra-ui/react"
import '@scalar/api-reference-react/style.css'

export function ApiBrowserScreen() {
  return (
    <Stack h="100vh" overflow="auto">
      <ApiReferenceReact
        configuration={{
          url: "/api/openapi.json",
          hideClientButton: true,
          telemetry: false,
          agent: { disabled: true },
        }}
      />
    </Stack>
  )
}
