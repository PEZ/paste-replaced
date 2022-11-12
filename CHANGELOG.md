# Change Log

Changes to Paste Replaced

## [Unreleased]

## [1.1.7] - 2022-11-12

- Actually fix bug with backward compatability with old config format 🤦

## [1.1.6] - 2022-11-12

- Fix bug with backward compatability with old config format

## [1.1.5] - 2022-10-25

- Fix bug with referencing named replacers
- Add OPEN README message when opening menu with no valid replacers are configured

## [1.1.4] - 2022-10-25

- Changed default paste-replaced keyboard shortcut to `ctrl+alt+v space`
- Add Paste Replace... menu
- Generalize Select and Paste Replace
- Add `simulateTypingSpeed` local config to **replacers**
- Add `paste-replacted.skipPaste` global option
- Add `skipPaste` local **replacers** option
- Keyboard shortcuts can now provide **replacers**, and select command ids (and `simulateTypingSpeed`, and `skipPaste`)
- Add `string` type `replacements` for pasting/typing verbatim

## [1.1.3] - 2022-05-31

- Add some logging when Paste Replaced starts and finsishes activating

## 1.1.2 - 2021-09-23

- Tweaks to fast-typing speeds
- Bug fix for new-lines w/o indents in fast-typed text

## 1.1.1 - 2021-09-21

- Fast typing has speed settings, typing in text character by character
- Ported extension to from TypeScript ClojureScript

## 1.1.0 - 2021-09-15

- Command(s) for replacing text at will. Can be used for quick-typing canned snippets

## 1.0.0 - 2021-09-15

- Initial release
- Pastes the content of the clipboard, text replaced by configurable regular expressions