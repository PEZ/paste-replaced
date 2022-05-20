import * as vscode from "vscode";

type ReplacerTuple = [string, string, string?];
type Replacer = ReplacerTuple[];


function _gaussianRand() {
  const GAUSS_FACTOR = 7;
  let rand = 0;
  for (let i = 0; i < GAUSS_FACTOR; i += 1) {
    rand += Math.random();
  }
  return rand / GAUSS_FACTOR;
}

function gaussianRand(start: number, end: number) {
  return Math.floor(start + _gaussianRand() * (end - start + 1));
}

type TypingSpeeds = "instant" | "fast" | "intermediate" | "slow";

const typingSpeed = {
  fast: 0.01,
  intermediate: 6,
  slow: 15,
};

function humanizePause(s: string, typePause: number): number {
  if (s.match(/^ |\t$/)) {
    //console.debug("space! s:", `'${s}'`);
    return gaussianRand(
      typePause,
      Math.pow(typePause * 2, 2.2)
    );
  } else if (s.match(/\s{2,}|\n/)) {
    //console.debug("spaces! s:", `'${s}'`);
    return gaussianRand(
      typePause,
      Math.pow((typePause + 5) * 2, 2) * 3
    );
  } else {
    return gaussianRand(0, typePause * 20);
  }
}

let typingInterrupted = false;

const setIsTyping = async (isTyping: boolean) => {
  typingInterrupted = false;
  vscode.commands.executeCommand(
    "setContext",
    "paste-replaced.isTyping",
    isTyping
  );
};

/**
 * Pastes what is on the Clipboard and pastes it with the replacers
 * configured in `paste-replaced.replacers`.
 * Restores original clipboard content when done.
 */
const pasteReplaced = async () => {
  try {
    const allReplacers: Replacer[] | undefined = vscode.workspace
      .getConfiguration("paste-replaced")
      .get("replacers");
    if (allReplacers && allReplacers.length > 0) {
      setIsTyping(true);
      const replacersConfig: Replacer = allReplacers[0] as Replacer;
      const replacers: [RegExp, string][] = replacersConfig.map((r) => [
        new RegExp(r[0], r[2]),
        r[1],
      ]);
      const originalClipBoardText = await vscode.env.clipboard.readText();
      let newText = originalClipBoardText;
      replacers.forEach(([s, r]) => {
        newText = newText.replace(s, r);
      });
      const simulateTyping = vscode.workspace
        .getConfiguration("paste-replaced")
        .get("simulateTypingSpeed") as TypingSpeeds;
      if (simulateTyping === "instant") {
        await vscode.env.clipboard.writeText(newText);
        await vscode.commands.executeCommand("execPaste");
      } else {
        const typePause = typingSpeed[simulateTyping];
        const words = newText.match(/\s+|\S+/g) ?? [];
        //console.debug("words", words);
        for (let word of words) {
          //console.debug("word:", `'${word}'`);
          if (typingInterrupted) {
            break;
          }
          for (let s of word.match(/\s{2,}/) ? [word] : word) {
            //console.debug("s:", `'${s}'`);
            await vscode.env.clipboard.writeText(s);
            await vscode.commands.executeCommand("execPaste");
            await new Promise((resolve) =>
              setTimeout(resolve, humanizePause(s, typePause))
            );
          }
        }
      }
      await vscode.env.clipboard.writeText(originalClipBoardText);
      setIsTyping(false);
    } else {
      vscode.window.showWarningMessage(`No replacers configured?`);
    }
  } catch (error) {
    console.error(error);
    vscode.window.showErrorMessage(`Paste Replaced failed: ${error}`);
    setIsTyping(false);
    throw new Error(`Paste Replaced failed: ${error}`);
  }
};

/**
 * Selects some text and then pastes it replaced.
 * Restoring original clipboard contents when done.
 * @param selectCommandId the command id to use for selecting the text to be pasted replaced
 */
const selectAndPasteReplaced = async (selectCommandId: string) => {
  const originalClipboardText = await vscode.env.clipboard.readText();
  await vscode.commands.executeCommand(selectCommandId);
  await vscode.commands.executeCommand("execCopy");
  await pasteReplaced();
  await vscode.env.clipboard.writeText(originalClipboardText);
};

/**
 * Selects all text and then pastes it replaced.
 * Extra useful in input prompts.
 */
const selectAllAndPasteReplaced = async () => {
  await selectAndPasteReplaced("editor.action.selectAll");
};

/**
 * Selects the word to the left of the cursor, then pastes it replaced.
 * Only works in a `vscode.TextEditor`.
 */
const selectWordLeftAndPasteReplaced = async () => {
  await selectAndPasteReplaced("cursorWordLeftSelect");
};

export function activate(context: vscode.ExtensionContext) {
  console.info(
    'Congratulations, your extension "paste-replaced" is now active!'
  );
  context.subscriptions.push(
    vscode.commands.registerCommand("paste-replaced.paste", pasteReplaced)
  );
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "paste-replaced.selectAllAndPasteReplaced",
      selectAllAndPasteReplaced
    )
  );
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "paste-replaced.selectWordLeftAndPasteReplaced",
      selectWordLeftAndPasteReplaced
    )
  );
  context.subscriptions.push(
    vscode.commands.registerCommand("paste-replaced.interruptTyping", () => {
      typingInterrupted = true;
    })
  );
}

// this method is called when your extension is deactivated
export function deactivate() {}
