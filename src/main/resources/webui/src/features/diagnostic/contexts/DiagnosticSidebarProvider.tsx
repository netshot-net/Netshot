import type { DiagnosticBrief } from "@/api/diagnostic"
import {
  Dispatch,
  PropsWithChildren,
  SetStateAction,
  createContext,
  use,
  useState,
} from "react"

export type DiagnosticSidebarContextType = {
  query: string
  setQuery: Dispatch<SetStateAction<string>>
  total: number
  setTotal: Dispatch<SetStateAction<number>>
  data: DiagnosticBrief[]
  setData: Dispatch<SetStateAction<DiagnosticBrief[]>>
}

export const DiagnosticSidebarContext = createContext<DiagnosticSidebarContextType>(null!)
export const useDiagnosticSidebar = () => use(DiagnosticSidebarContext)

export default function DiagnosticSidebarProvider(props: PropsWithChildren) {
  const { children } = props
  const [query, setQuery] = useState<string>("")
  const [total, setTotal] = useState<number>(0)
  const [data, setData] = useState<DiagnosticBrief[]>([])

  return (
    <DiagnosticSidebarContext
      value={{
        query,
        setQuery,
        total,
        setTotal,
        data,
        setData,
      }}
    >
      {children}
    </DiagnosticSidebarContext>
  )
}
