import { ProtectedRoute, ThemeProvider, ToastProvider } from "@/components"
import { AuthProvider } from "@/contexts"
import {
  AdministrationApiTokenScreen,
  AdministrationClusteringScreen,
  AdministrationDeviceCredentialSetScreen,
  AdministrationDomainScreen,
  AdministrationDriverScreen,
  AdministrationScreen,
  AdministrationUserScreen,
  AdministrationVaultInstanceScreen,
  AdministrationWebhookScreen,
} from "@/features/administration"
import { SigninScreen } from "@/features/auth"
import {
  ComplianceScreen,
  ConfigurationCompliancePolicyScreen,
  ConfigurationComplianceRuleScreen,
  HardwareComplianceScreen,
  SoftwareComplianceScreen,
} from "@/features/compliance"
import {
  DeviceComplianceScreen,
  DeviceConfigurationScreen,
  DeviceDetailScreen,
  DeviceDiagnosticScreen,
  DeviceEmptyScreen,
  DeviceGeneralScreen,
  DeviceInterfaceScreen,
  DeviceModuleScreen,
  DeviceScreen,
  DeviceTaskScreen,
} from "@/features/device"
import {
  DiagnosticDetailScreen,
  DiagnosticEmptyScreen,
  DiagnosticScreen,
} from "@/features/diagnostic"
import {
  ReportConfigurationChangeScreen,
  ReportConfigurationComplianceDetailScreen,
  ReportConfigurationComplianceEmptyScreen,
  ReportConfigurationComplianceScreen,
  ReportDeviceAccessFailureScreen,
  ReportHardwareSupportStatusScreen,
  ReportScreen,
  ReportSoftwareComplianceScreen,
} from "@/features/report"
import { TaskActiveScreen, TaskHistoryScreen, TaskScreen } from "@/features/task"
import i18n, { LocalizationProvider } from "@/i18n"
import { MainScreen, NotFoundScreen } from "@/screens"
import { Level } from "@/types"
import { Center, Spinner } from "@chakra-ui/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { lazy, Suspense } from "react"
import { I18nextProvider } from "react-i18next"
import { BrowserRouter, Navigate, Route, Routes } from "react-router"
import { ApplicationProvider } from "./contexts/ApplicationProvider"
import { DialogProviderWithI18n } from "./dialog/extensions"

const ApiBrowserScreen = lazy(() =>
  import("@/screens/ApiBrowserScreen").then((module) => ({
    default: module.ApiBrowserScreen,
  })),
)

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      structuralSharing: false,
      retryOnMount: true,
      retry: 0,
    },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <LocalizationProvider>
        <ThemeProvider>
          <BrowserRouter>
            <AuthProvider>
              <ApplicationProvider>
                <DialogProviderWithI18n>
                  <ToastProvider />
                  <Routes>
                    <Route path="signin" element={<SigninScreen />} />
                    <Route
                      path="app/api-browser"
                      element={
                        <Suspense
                          fallback={
                            <Center h="100vh">
                              <Spinner size="lg" />
                            </Center>
                          }
                        >
                          <ApiBrowserScreen />
                        </Suspense>
                      }
                    />
                    <Route path="app" element={<MainScreen />}>
                      <Route index element={<Navigate to="devices" replace />} />
                      <Route path="reports" element={<ReportScreen />}>
                        <Route index element={<Navigate to="configuration-change" replace />} />
                        <Route
                          path="configuration-change"
                          element={<ReportConfigurationChangeScreen />}
                        />
                        <Route
                          path="device-access-failure"
                          element={<ReportDeviceAccessFailureScreen />}
                        />
                        <Route
                          path="configuration-compliance"
                          element={<ReportConfigurationComplianceScreen />}
                        >
                          <Route index element={<ReportConfigurationComplianceEmptyScreen />} />
                          <Route
                            path=":id"
                            element={<ReportConfigurationComplianceDetailScreen />}
                          />
                        </Route>
                        <Route
                          path="software-compliance"
                          element={<ReportSoftwareComplianceScreen />}
                        />
                        <Route
                          path="hardware-support-status"
                          element={<ReportHardwareSupportStatusScreen />}
                        />
                      </Route>
                      <Route path="devices" element={<DeviceScreen />}>
                        <Route index element={<DeviceEmptyScreen />} />
                        <Route path=":id" element={<DeviceDetailScreen />}>
                          <Route index element={<Navigate to="general" replace />} />
                          <Route path="general" element={<DeviceGeneralScreen />} />
                          <Route path="configurations" element={<DeviceConfigurationScreen />} />
                          <Route path="interfaces" element={<DeviceInterfaceScreen />} />
                          <Route path="modules" element={<DeviceModuleScreen />} />
                          <Route path="diagnostics" element={<DeviceDiagnosticScreen />} />
                          <Route path="compliance" element={<DeviceComplianceScreen />} />
                          <Route path="tasks" element={<DeviceTaskScreen />} />
                        </Route>
                      </Route>
                      <Route path="diagnostics" element={<DiagnosticScreen />}>
                        <Route index element={<DiagnosticEmptyScreen />} />
                        <Route path=":id" element={<DiagnosticDetailScreen />} />
                      </Route>
                      <Route path="compliance" element={<ComplianceScreen />}>
                        <Route path="hardware" element={<HardwareComplianceScreen />} />
                        <Route path="software" element={<SoftwareComplianceScreen />} />
                        <Route
                          path="config/:policyId"
                          element={<ConfigurationCompliancePolicyScreen />}
                        />
                        <Route
                          path="config/:policyId/:ruleId"
                          element={<ConfigurationComplianceRuleScreen />}
                        />
                        <Route index element={<Navigate to="software" replace />} />
                      </Route>
                      <Route path="tasks" element={<TaskScreen />}>
                        <Route index element={<Navigate to="active" replace />} />
                        <Route path="active" element={<TaskActiveScreen />} />
                        <Route path="history" element={<TaskHistoryScreen />} />
                      </Route>
                      <Route element={<ProtectedRoute minLevel={Level.Admin} />}>
                        <Route path="administration" element={<AdministrationScreen />}>
                          <Route index element={<Navigate to="user" replace />} />
                          <Route path="user" element={<AdministrationUserScreen />} />
                          <Route path="device-domain" element={<AdministrationDomainScreen />} />
                          <Route
                            path="device-credential"
                            element={<AdministrationDeviceCredentialSetScreen />}
                          />
                          <Route path="driver" element={<AdministrationDriverScreen />} />
                          <Route path="api-token" element={<AdministrationApiTokenScreen />} />
                          <Route path="webhook" element={<AdministrationWebhookScreen />} />
                          <Route path="vault" element={<AdministrationVaultInstanceScreen />} />
                          <Route path="clustering" element={<AdministrationClusteringScreen />} />
                        </Route>
                      </Route>
                      <Route path="*" element={<NotFoundScreen fullPage={false} />} />
                    </Route>
                    <Route path="/" element={<Navigate to="app" />} />
                    <Route path="*" element={<NotFoundScreen />} />
                  </Routes>
                </DialogProviderWithI18n>
              </ApplicationProvider>
            </AuthProvider>
          </BrowserRouter>
        </ThemeProvider>
        </LocalizationProvider>
      </I18nextProvider>
    </QueryClientProvider>
  )
}

export default App
