import * as vscode from 'vscode';


type ReplacerTuple = [string, string, string?];
type Replacer = ReplacerTuple[];

export function activate(context: vscode.ExtensionContext) {
	console.log('Congratulations, your extension "paste-replaced" is now active!');
	context.subscriptions.push(vscode.commands.registerCommand('paste-replaced.paste', async () => {
		try {
			const allReplacers: Replacer[] | undefined = vscode.workspace.getConfiguration('paste-replaced').get('replacers');
			if (allReplacers && allReplacers.length > 0) {
				const replacersConfig: Replacer = allReplacers[0] as Replacer;
				const replacers: [RegExp, string][] = replacersConfig.map(r => [new RegExp(r[0], r[2]), r[1]]);
				const originalText = await vscode.env.clipboard.readText();
				let newText = originalText;
				replacers.forEach(([s, r]) => {
					newText = newText.replace(s, r);
				});
				vscode.env.clipboard.writeText(newText).then(async () => {
					await vscode.commands.executeCommand('execPaste');
					vscode.env.clipboard.writeText(originalText);
				});
			} else {
				vscode.window.showWarningMessage(`No replacers configured?`);
			}
		} catch (error) {
			console.log(error);
			vscode.window.showErrorMessage(`Paste Replaced failed: ${error}`);
		}
	}));
}

// this method is called when your extension is deactivated
export function deactivate() { }
