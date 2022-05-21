const vscode = require("vscode");
const extension = require("./out/extension.js");

/**
 * @param {vscode.ExtensionContext} context
 */
function activate(context) {
  return extension.activate(context);
}

function deactivate() {
  extension.deactivate();
}

module.exports = {
  activate,
  deactivate,
};
