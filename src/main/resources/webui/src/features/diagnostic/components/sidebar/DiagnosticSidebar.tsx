import { Protected } from "@/components"
import { useAuth } from "@/contexts"
import { LuChevronUp, LuPlus, LuStethoscope } from "react-icons/lu"
import { Level, TaskType } from "@/types"
import { Button, Group, IconButton, Menu, Portal, Separator, Stack } from "@chakra-ui/react"
import { useTranslation } from "react-i18next"
import AddTaskTrigger from "@/features/task/components/AddTaskTrigger"
import { DiagnosticSidebarProvider } from "../../contexts"
import { DiagnosticSidebarContext } from "../../contexts/DiagnosticSidebarProvider"
import AddDiagnosticTrigger from "../AddDiagnosticTrigger"
import DiagnosticSidebarList from "./DiagnosticSidebarList"
import DiagnosticSidebarSearch from "./DiagnosticSidebarSearch"
import DiagnosticSidebarSearchList from "./DiagnosticSidebarSearchList"

export default function DiagnosticSidebar() {
  const { t } = useTranslation()
  const { user } = useAuth()
  // Group's `attached` styling clones props onto its direct children only, so
  // the add-diagnostic button must be Group's direct child rather than hidden
  // behind a <Protected> wrapper - otherwise it never receives the
  // data-first/data-last attributes that remove its adjoining rounded corner.
  const isExecuteReadWrite = (user?.level || 0) >= Level.ExecuteReadWrite

  return (
    <DiagnosticSidebarProvider>
      <Stack w="full" h="full" overflow="hidden" gap="0">
        <DiagnosticSidebarContext.Consumer>
          {({ query }) => (
            <>
              <DiagnosticSidebarSearch />
              <Separator />

              {query ? <DiagnosticSidebarSearchList /> : <DiagnosticSidebarList />}
              <Protected minLevel={Level.Operator}>
                <Separator />
                <Stack p="6">
                  <Menu.Root positioning={{ placement: "top" }}>
                    <Group attached w="full">
                      {isExecuteReadWrite && (
                        <AddDiagnosticTrigger>
                          <Button flex="1">
                            <LuPlus />
                            {t("diagnostic.add")}
                          </Button>
                        </AddDiagnosticTrigger>
                      )}
                      <Menu.Trigger asChild>
                        <IconButton aria-label={t("common.actions")}>
                          <LuChevronUp />
                        </IconButton>
                      </Menu.Trigger>
                    </Group>
                    <Portal>
                      <Menu.Positioner>
                        <Menu.Content>
                          <AddTaskTrigger initialType={TaskType.RunGroupDiagnostic}>
                            <Menu.Item value="run-diagnostics-on-group">
                              <LuStethoscope />
                              {t("diagnostic.runOnGroupAction")}
                            </Menu.Item>
                          </AddTaskTrigger>
                        </Menu.Content>
                      </Menu.Positioner>
                    </Portal>
                  </Menu.Root>
                </Stack>
              </Protected>
            </>
          )}
        </DiagnosticSidebarContext.Consumer>
      </Stack>
    </DiagnosticSidebarProvider>
  )
}
