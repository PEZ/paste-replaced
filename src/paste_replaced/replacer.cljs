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

(def typing-speed
  {"fast" 0.01
   "intermediate" 6
   "slow" 15})

(defn humanize-pause
  [s typePause]
  (cond
    (re-find #"^ |\t$" s) (gaussian-rand typePause (.pow js/Math (* typePause 2) 2.2))
    (re-find #"\s{2,}|\n" s) (gaussian-rand typePause
                                            (* (.pow js/Math (* (+ typePause 5) 2) 2)
                                               3))
    :else (gaussian-rand 0 (* typePause 20))))

(def ^:private unicode-split-re (js/RegExp. "." "u"))

(defn typing!+
  [typing?]
  (swap! db/!app-db assoc :typing-interrupted? false)
  (p/do! (when-contexts/set-context!+ "paste-replaced.isTyping" typing?)))

(defn interrupt-typing! []
  (swap! db/!app-db assoc :typing-interrupted? true))

(defn paste-replaced!+ []
  (try
    (p/let [allReplacers (-> (vscode/workspace.getConfiguration "paste-replaced")
                             (.get "replacers")
                             (cljify))]
      (if (and allReplacers (> (count allReplacers) 0))
        (p/let [replacersConfig (first allReplacers)
                replacers (map
                           (fn [r]
                             [(js/RegExp. (r 0) (get r 2 "")) (r 1)])
                           replacersConfig)
                originalClipBoardText (vscode/env.clipboard.readText)
                newText (reduce (fn [acc [s r]]
                                  (.replace acc s r))
                                originalClipBoardText
                                replacers)
                simulateTyping (-> (vscode/workspace.getConfiguration "paste-replaced")
                                   (.get "simulateTypingSpeed"))]
          (typing!+ true)
          (if (= simulateTyping "instant")
            (p/do! (vscode/env.clipboard.writeText newText)
                   (vscode/commands.executeCommand "execPaste"))
            (p/let [typePause (typing-speed simulateTyping)
                    matches  (re-seq #"\s+|\S+" newText)
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
                                                 (humanize-pause s typePause)))))))
                    (if (re-find #"\s{2,}" word)
                      [word]
                      (re-seq unicode-split-re word)))))
               words)))
          (vscode/env.clipboard.writeText originalClipBoardText)
          (typing!+ false))
        (vscode/window.showWarningMessage "No replacers configured?")))
    (catch :default error
      (.error js/console error)
      (vscode/window.showErrorMessage (str "Paste Replaced failed: "
                                           error))
      (typing!+ false)
      (throw (js/Error. (str "Paste Replaced failed: "
                             error))))))

(defn select-and-paste-replaced
  [select-command-id]
  (p/let [originalClipboardText (vscode/env.clipboard.readText)]
    (p/do! (vscode/commands.executeCommand  select-command-id)
           (vscode/commands.executeCommand  "execCopy")
           (paste-replaced!+)
           (vscode/env.clipboard.writeText originalClipboardText))))

(defn select-all-and-paste-replaced []
  (p/do! (select-and-paste-replaced "editor.action.selectAll")))

(defn select-word-left-and-paste-replaced []
  (p/do! (select-and-paste-replaced "cursorWordLeftSelect")))