import * as vscode from "vscode";

type ReplacerTuple = [string, string, string?];
type Replacer = ReplacerTuple[];

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
			await vscode.env.clipboard.writeText(newText);
			await vscode.commands.executeCommand("execPaste");
			await vscode.env.clipboard.writeText(originalClipBoardText);
    } else {
      vscode.window.showWarningMessage(`No replacers configured?`);
    }
  } catch (error) {
    console.log(error);
		vscode.window.showErrorMessage(`Paste Replaced failed: ${error}`);
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
  console.log(
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
}

// this method is called when your extension is deactivated
export function deactivate() {}
