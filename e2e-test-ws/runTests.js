const vscode = require('vscode');

exports.run = async () => {
  return vscode.commands.executeCommand(
    'joyride.runCode',
    "(require '[test-runner.runner :as runner]) (runner/run-all-tests)"
  );
};
