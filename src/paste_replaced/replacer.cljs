(ns paste-replaced.replacer
  (:require ["vscode" :as vscode]
            [paste-replaced.db :as db]
            [paste-replaced.utils :refer [cljify]]
            [paste-replaced.when-contexts :as when-contexts]
            [promesa.core :as p]))

(defn gaussian-rand'
  []
  (let [accuracy 7]
    (loop [rand 0
           i 0]
      (if (< i accuracy)
        (recur (+ rand (.random js/Math)) (inc i))
        (/ rand accuracy)))))

(defn gaussian-rand
  [start end]
  (js/Math.floor
   (+ start (* (gaussian-rand') (+ (- end start) 1)))))

(def typing-pauses
  {"fast" {:char 0
           :space 0
           :nl 350}
   "intermediate" {:char 75
           :space 250
           :nl 1300}
   "slow" {:char 350
           :space 1000
           :nl 2500}})

(def typing-speed "slow")

(defn humanize-pause
  [s typing-speed]
  (cond
    (re-find #"^ |\t$" s) (gaussian-rand 0 (-> typing-speed typing-pauses :space))
    (re-find #"\s{2,}|\n" s) (gaussian-rand 0 (-> typing-speed typing-pauses :nl))
    :else (gaussian-rand 0 (-> typing-speed typing-pauses :char))))

(def ^:private unicode-split-re (js/RegExp. "." "us"))

(defn typing!+
  [typing?]
  (swap! db/!app-db assoc :typing-interrupted? false)
  (p/do! (when-contexts/set-context!+ "paste-replaced.isTyping" typing?)))

(defn interrupt-typing! []
  (swap! db/!app-db assoc :typing-interrupted? true))

(defn simulate-typing
  "Chops up `new-text` in characters and then, one at a time,
   writes them to the clipboard and then pastes them. Pausing
   with a randomized distribution around `type-pause`."
  [new-text typing-speed]
  (p/let [matches  (re-seq #"\s+|\S+" new-text)
          words (if matches matches [])]
    (p/run!
     (fn [word]
       (when-not (:typing-interrupted? @db/!app-db)
         (p/run!
          (fn [s]
            (when-not (:typing-interrupted? @db/!app-db)
              (p/do! (vscode/env.clipboard.writeText s)
                     (vscode/commands.executeCommand "execPaste")
                     (p/create
                      (fn [resolve, _reject]
                        (js/setTimeout resolve
                                       (humanize-pause s typing-speed)))))))
          (if (re-find #"\s{2,}" word)
            [word]
            (re-seq unicode-split-re word)))))
     words)))

(defn- compile-regex [r]
  [(js/RegExp. (r 0) (get r 2 "")) (r 1)])


(defn paste-replaced!+
  "Pastes what is on the Clipboard and pastes it with the replacers
   configured in `paste-replaced.replacers`.
   Restores original (un-replaced) clipboard content when done."
  []
  (try
    (p/let [all-replacers (-> (vscode/workspace.getConfiguration "paste-replaced")
                              (.get "replacers")
                              (cljify))]
      (if (and all-replacers (> (count all-replacers) 0))
        (p/let [replacers-config (first all-replacers)
                _ (def replacers-config replacers-config)
                replacers (map
                           (fn [r]
                             (compile-regex r))
                           (if (vector? replacers-config)
                             replacers-config
                             (:replacements replacers-config)))
                original-clipboard-text (vscode/env.clipboard.readText)
                _ (def replacers replacers)
                new-text (reduce (fn [acc [s r]]
                                   (.replace acc s r))
                                 original-clipboard-text
                                 replacers)
                simulate-typing-config (-> (vscode/workspace.getConfiguration "paste-replaced")
                                           (.get "simulateTypingSpeed"))]
          (typing!+ true)
          (if (= simulate-typing-config "instant")
            (p/do! (vscode/env.clipboard.writeText new-text)
                   (vscode/commands.executeCommand "execPaste"))
            (simulate-typing new-text simulate-typing-config))
          (vscode/env.clipboard.writeText original-clipboard-text)
          (typing!+ false))
        (vscode/window.showWarningMessage "No replacers configured?")))
    (catch :default error
      (.error js/console error)
      (vscode/window.showErrorMessage (str "Paste Replaced failed: "
                                           error))
      (typing!+ false)
      (throw (js/Error. (str "Paste Replaced failed: "
                             error))))))

(defn select-and-paste-replaced!+
  "Selects some text, copied it and then pastes it replaced.
   Restoring original clipboard contents when done.
   `select-command-id` is the command id to use for selecting
   the text to be pasted replaced."
  [select-command-id]
  (p/let [original-clipboard-text (vscode/env.clipboard.readText)]
    (p/do! (vscode/commands.executeCommand  select-command-id)
           (vscode/commands.executeCommand  "execCopy")
           (paste-replaced!+)
           (vscode/env.clipboard.writeText original-clipboard-text))))

(defn select-all-and-paste-replaced!+
  "Selects all text and then pastes it replaced.
   Extra useful in input prompts."
  []
  (p/do! (select-and-paste-replaced!+ "editor.action.selectAll")))

(defn select-word-left-and-paste-replaced!+
  "Selects the word to the left of the cursor, then pastes it replaced.
   Only works in a `vscode/TextEditor`."
  []
  (p/do! (select-and-paste-replaced!+ "cursorWordLeftSelect")))
