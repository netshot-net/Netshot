import type { Brief, Schemas } from "@/api/client"
import type { DriverOptionType } from "./device"

/** A user input a script asks for (the enum of the UI has the values of the API). */
export type ScriptUserInputDefinition = Omit<Schemas["UserInputDefinition"], "type" | "name" | "label"> & {
  name: string
  label: string
  type: DriverOptionType
}

/** A saved script in its brief view (the script lists): no code and no user inputs. */
export type ScriptBrief = Brief["Script"] & {
  id: number
  name: string
  deviceDriver: string
}

/** A saved script (the brief view has no code and no user inputs). */
export type Script = Omit<Schemas["Script"], "userInputDefinitions"> & {
  id: number
  name: string
  deviceDriver: string
  userInputDefinitions?: Record<string, ScriptUserInputDefinition>
}
