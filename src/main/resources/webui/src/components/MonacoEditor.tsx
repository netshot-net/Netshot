import { Box, BoxProps } from "@chakra-ui/react";
import { Uri, editor } from "monaco-editor";
import "./monaco/monacoWorkerKeepAlive";
import {
  MutableRefObject,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
} from "react";
import {
  ScriptKind,
  registerNetshotPythonCompletions,
  registerNetshotScriptGlobals,
  scriptKindByModel,
} from "./monaco/netshotScriptSupport";

self.MonacoEnvironment = {
  async getWorker(_, label) {

    if (label === "typescript" || label === "javascript") {
      const jsWorker = await import(
        "monaco-editor/language/typescript/ts.worker?worker"
      );
      return new jsWorker.default();
    }

    const worker = await import("monaco-editor/editor/editor.worker?worker");
    return new worker.default();
  },
};

registerNetshotScriptGlobals();
registerNetshotPythonCompletions();

export type MonacoEditorProps = {
  language?: editor.IStandaloneEditorConstructionOptions["language"] | "python";
  /**
   * Which of the Netshot script APIs this editor exposes (run script,
   * diagnostic, or compliance rule) — drives completion for Python, which
   * has no real language service to infer this from JSDoc-typed params.
   */
  scriptKind?: ScriptKind;
  readOnly?: boolean;
  value?: string;
  onModelChange?(value: string): void;
  onFocus?(): void;
  onBlur?(): void;
  ref?: MutableRefObject<HTMLDivElement>;
} & BoxProps;

function MonacoEditor(props: MonacoEditorProps) {
  const {
    ref,
    value,
    readOnly = false,
    language = "javascript",
    scriptKind,
    onModelChange,
    onFocus,
    onBlur,
    ...other
  } = props;
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<editor.IStandaloneCodeEditor | undefined>(undefined);
  const modelRef = useRef<editor.ITextModel | undefined>(undefined);
  const instanceId = useId();

  useLayoutEffect(() => {
    if (!containerRef?.current) return;
    if (editorRef?.current) return;

    const model = editor.createModel(value ?? "", language, Uri.parse(`inmemory://netshot-script/${instanceId}`));
    if (scriptKind) {
      scriptKindByModel.set(model, scriptKind);
    }
    modelRef.current = model;

    editorRef.current = editor.create(containerRef.current, {
      model,
      automaticLayout: true,
      readOnly,
      padding: {
        top: 20,
      },
      // Monaco defaults quickSuggestions.strings to "off": suggestions
      // don't auto-pop while typing inside a string literal (only Ctrl+Space
      // triggers them), which silently hides the whole point of typing
      // device.get's key parameter as a string-literal union - enable it.
      quickSuggestions: {
        strings: true,
      },
    });

    // Monaco's built-in TS/JS completion provider only declares "." as a
    // triggerCharacter (not a quote), so quickSuggestions.strings alone
    // still doesn't show anything the instant an opening quote is typed -
    // only once quickSuggestions' own "typing a word" heuristic kicks in
    // after the first character inside the string. Force-trigger the
    // suggest widget the moment a quote is typed so the string-literal-union
    // suggestions (e.g. device.get's key names) appear immediately, with
    // nothing to type first.
    const quoteTriggerDisposable = editorRef.current.onDidChangeModelContent((event) => {
      if (event.isUndoing || event.isRedoing) return;
      const lastChange = event.changes[event.changes.length - 1];
      if (lastChange && ['"', "'", '""', "''"].includes(lastChange.text)) {
        editorRef.current?.trigger("netshot", "editor.action.triggerSuggest", {});
      }
    });

    return () => {
      quoteTriggerDisposable.dispose();
      editorRef.current?.dispose();
      editorRef.current = undefined;
      if (modelRef.current) {
        scriptKindByModel.delete(modelRef.current);
        modelRef.current.dispose();
        modelRef.current = undefined;
      }
    };
    // Intentionally run once on mount: this editor owns its model for its
    // whole lifetime — later prop changes (e.g. `value` on every keystroke
    // via a controlled form) must not tear it down and recreate it.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!editorRef.current) {
      return;
    }

    if (onModelChange) {
      editorRef.current.onDidChangeModelContent(() => {
        const value = editorRef.current?.getModel()?.getValue();
        if (value !== undefined) onModelChange(value);
      });
    }

    if (onFocus) {
      editorRef.current.onDidFocusEditorText(onFocus);
    }

    if (onBlur) {
      editorRef.current.onDidBlurEditorText(onBlur);
    }
  }, [onModelChange, onFocus, onBlur, editorRef]);

  return (
    <Box
      ref={ref}
      position="relative"
      py="4"
      flex="1"
      borderRadius="lg"
      border="1px solid"
      borderColor="grey.100"
      {...other}
    >
      <Box
        flex="1"
        position="absolute"
        top="0"
        left="0"
        bottom="0"
        right="0"
        width="100%"
        height="100%"
        ref={containerRef}
      />
    </Box>
  );
}

export default MonacoEditor;
