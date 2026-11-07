import { Icon, Stack, Text } from "@chakra-ui/react"
import { useEffect, useRef, useMemo } from "react"
import { NavLink, useParams } from "react-router"

import type { DiagnosticBrief } from "@/api/diagnostic"
import { DiagnosticType } from "@/types"
import { Tooltip } from "@/components/ui/tooltip"

import { LuCircleX, LuRegex } from "react-icons/lu"
import { SiJavascript, SiPython } from "react-icons/si"

type DiagnosticBoxProps = {
  diagnostic: DiagnosticBrief
}

function DiagnosticItem(props: DiagnosticBoxProps) {
  const { diagnostic } = props

  const params = useParams<{ id: string }>()
  const isCurrentDiagnostic = +(params?.id ?? "0") === diagnostic?.id
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (isCurrentDiagnostic && containerRef.current) {
      containerRef.current.scrollIntoView({ block: "nearest" })
    }
  }, [isCurrentDiagnostic])

  const iconEl = useMemo(() => {
    if (diagnostic.type === DiagnosticType.Javascript) {
      return <SiJavascript />
    } else if (diagnostic.type === DiagnosticType.Python) {
      return <SiPython />
    } else {
      return <LuRegex />
    }
  }, [diagnostic])

  const isDisabled = !diagnostic?.enabled
  const isAssigned = !!diagnostic?.targetGroup

  return (
    <NavLink to={`./${diagnostic?.id}`}>
      {({ isActive }) => (
        <Stack
          px="3"
          gap="1"
          height="44px"
          justifyContent="center"
          bg={isActive ? "green.50" : "white"}
          transition="all .2s ease"
          _hover={{
            bg: isActive ? "green.50" : "grey.50",
          }}
          borderRadius="md"
          ref={containerRef}
        >
          <Stack direction="row" gap="3" alignItems="center" justifyContent="space-between">
            <Stack direction="row" gap="3" alignItems="center" overflow="hidden">
              <Icon color="green.600" size="md" opacity={isDisabled ? 0.5 : 1} flexShrink="0">
                {iconEl}
              </Icon>
              <Text lineClamp={1} opacity={isDisabled ? 0.5 : 1}>{diagnostic?.name}</Text>
            </Stack>
            {!isAssigned && (
              <Tooltip content="Not assigned to any group">
                <Icon color="red.500" size="sm" flexShrink="0">
                  <LuCircleX />
                </Icon>
              </Tooltip>
            )}
          </Stack>
        </Stack>
      )}
    </NavLink>
  )
}

export default DiagnosticItem
