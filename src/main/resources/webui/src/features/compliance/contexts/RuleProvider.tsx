import type { Rule } from "@/api/rule";
import { PropsWithChildren, createContext, use } from "react";

export type RuleContextType = {
  rule: Rule | null | undefined;
  isLoading: boolean;
};

export const RuleContext = createContext<RuleContextType>(null!);
export const useRule = () => use(RuleContext);

export default function RuleProvider(
  props: PropsWithChildren<RuleContextType>
) {
  const { children, rule, isLoading } = props;

  return (
    <RuleContext value={{ rule, isLoading }}>
      {children}
    </RuleContext>
  );
}
