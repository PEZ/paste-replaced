(ns paste-replaced.replacer
  (:require ["vscode" :as vscode]
            ["open" :as open]
            [paste-replaced.db :as db]
            [paste-replaced.quick-pick :as qp]
            [paste-replaced.utils :as utils :refer [cljify jsify]]
            [paste-replaced.when-contexts :as when-contexts]
            [promesa.core :as p]
            [cljs.tools.reader :as tr]
            [cljs.pprint :as pprint]))

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
           :nl 350
           :description "Typed as a really fast keyboard wielder"}
   "intermediate" {:char 75
                   :space 250
                   :nl 1300
                   :description "Typed as an intermediately fast typist"}
   "slow" {:char 350
           :space 1000
           :nl 2500
           :description "Typed as a slow, painfully slow, typist"}})

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
  [text typing-speed]
  (p/let [matches  (re-seq #"\s+|\S+" text)
          words (if matches matches [])]
    (p/run!
     (fn [word]
       (when-not (:typing-interrupted? @db/!app-db)
         (p/run!
          (fn [c]
            (when-not (:typing-interrupted? @db/!app-db)
              (p/do! (vscode/env.clipboard.writeText c)
                     (vscode/commands.executeCommand "editor.action.clipboardPasteAction")
                     (p/create
                      (fn [resolve, _reject]
                        (js/setTimeout resolve
                                       (humanize-pause c typing-speed)))))))
          (if (re-find #"\s{2,}" word)
            [word]
            (re-seq unicode-split-re word)))))
     words)))

(defn- compile-regex
  [r]
  (if (and 
       (vector? r)
       (<= 2 (count r))
       (every? string? r)) 
    [(js/RegExp. (r 0) (get r 2 "")) (r 1)]
    (throw (js/Error. "Replacers regex should be a tuple of strings: [`search`, `replace`, `flags?`]"))))

(defn- skip-paste-for-replacer?
  [replacer]
  (cond
    (boolean? (:skipPaste replacer))
    (:skipPaste replacer)

    :else
    (-> (vscode/workspace.getConfiguration "paste-replaced")
        (.get "skipPaste"))))

(defn- simulate-typing-config-for-replacer
  [replacer]
  (or (:simulateTypingSpeed replacer)
      (-> (vscode/workspace.getConfiguration "paste-replaced")
          (.get "simulateTypingSpeed"))))

(defn- paste-replaced-using-replacer!+
  [replacer]
  (assert (map? replacer))
  (p/let [replacements (:replacements replacer)
          replacers (cond
                      (string? replacements) replacements
                      (and (vector? replacements)
                           (every? vector? replacements)) (map
                                                           (fn [r]
                                                             (compile-regex r))
                                                           replacements)
                      :else (throw (js/Error. "Invalid replacements in replacer. Should be a vector of tuples.")))
          original-clipboard-text (vscode/env.clipboard.readText)
          new-text (if (string? replacers)
                     replacers
                     (reduce (fn [acc [s r]]
                               (.replace acc s r))
                             original-clipboard-text
                             replacers))
          simulate-typing-config (simulate-typing-config-for-replacer replacer)
          skip-paste? (skip-paste-for-replacer? replacer)]
    (typing!+ true)
    (if (= simulate-typing-config "instant")
      (p/do! (vscode/env.clipboard.writeText new-text)
             (when-not skip-paste?
               (vscode/commands.executeCommand "editor.action.clipboardPasteAction")))
      (if skip-paste?
        (vscode/env.clipboard.writeText new-text)
        (simulate-typing new-text simulate-typing-config)))
    (if skip-paste?
      (vscode/window.showInformationMessage "Replaced text copied to the clipboard.")
      (vscode/env.clipboard.writeText original-clipboard-text))
    (typing!+ false)))

(defn- show-replacers-picker!+
  [replacers]
  (p/let [menu-items (mapv (fn [r]
                             (cond-> r
                               :always
                               (assoc :label (:name r)
                                      :replacer r)

                               (simulate-typing-config-for-replacer r)
                               (assoc :description (simulate-typing-config-for-replacer r))

                               (skip-paste-for-replacer? r)
                               (assoc :description "skip paste")))
                           replacers)
          choice (qp/quick-pick!+ (jsify menu-items) {:title "Choose replacer"} "replacers")]
    (cljify choice)))

(defn- show-texts-picker!+
  [texts]
  (p/let [menu-items (mapv (fn [t]
                             (assoc t :label (:name t)))
                           texts)
          choice (qp/quick-pick!+ (jsify menu-items) {:title "Choose text"} "texts")
          clj-choice (cljify choice)]
    (if (vector? clj-choice)
      (first clj-choice)
      clj-choice)))

(defn- show-readme-message+ [msg]
  (-> (vscode/window.showInformationMessage msg "Open README")
      (.then
       (fn [button]
         (when button
           (open "https://github.com/PEZ/paste-replaced#paste-replaced"))))))

(defn- named-from-picker!+ [all-replacers]
  (p/let [named-replacers (filter #(and (map? %)
                                        (:name %)) all-replacers)
          replacer (if (< 0 (count named-replacers))
                     (show-replacers-picker!+ named-replacers)
                     (show-readme-message+ "No named replacers found."))]
    (:replacer replacer)))

(defn- all-configured-replacers []
  (let [all-replacers-configs (-> (vscode/workspace.getConfiguration "paste-replaced")
                                  (.inspect "replacers")
                                  (cljify))
        all-replacers (into (vec (:workspaceValue all-replacers-configs))
                            (:globalValue all-replacers-configs))]
    all-replacers))

(defn- choose-replacer!+
  [^js provided-replacer]
  (let [all-replacers (all-configured-replacers)]
    (cond
      (string? provided-replacer)
      (let [found-replacer (->> all-replacers
                                (filter (fn [replacer]
                                          (= provided-replacer (:name replacer))))
                                first)]
        (if found-replacer
          found-replacer
          (vscode/window.showErrorMessage (str "No replacer found named: " provided-replacer))))

      (-> provided-replacer cljify vector?)
      {:replacements (cljify provided-replacer)}

      (-> provided-replacer cljify map?)
      (if (-> provided-replacer cljify :replacements)
        (cljify provided-replacer)
        (named-from-picker!+ all-replacers))

      (nil? provided-replacer)
      (if (and all-replacers (> (count all-replacers) 0))
        (if (every? vector? all-replacers)
          {:replacements (first all-replacers)}
          (named-from-picker!+ all-replacers))
        (show-readme-message+ "No replacers configured?"))

      :else
      (vscode/window.showErrorMessage "Malformed replacer provided"))))

(defn paste-replaced!+
  "Pastes what is on the Clipboard and pastes it with the replacers
   configured in `paste-replaced.replacers`.
   Restores original (un-replaced) clipboard content when done."
  ([]
   (paste-replaced!+ nil))
  ([^js provided-replacer]
   (try
     (p/let [replacer (choose-replacer!+ provided-replacer)]
       (when replacer
         (paste-replaced-using-replacer!+ replacer)))
     (catch :default error
       (js/console.error error)
       (vscode/window.showErrorMessage (str "Paste Replaced failed: "
                                            error))
       (typing!+ false)
       (throw (js/Error. (str "Paste Replaced failed: "
                              error)))))))

(defn- pretty-str
  [x]
  (binding [pprint/*print-right-margin* 120]
    (with-out-str (pprint/pprint x))))

(defn- str-text [text-item]
  (def text-item text-item)
  (if (= (type "")
         (type (:text text-item)))
    text-item
    (update text-item :text pretty-str)))

(defn- paste-replaced-text-impl!+
  ([text]
   (paste-replaced-text-impl!+ text nil))
  ([text provided-replacer]
   (p/let [original-clipboard-text (vscode/env.clipboard.readText)
           replacer (choose-replacer!+ provided-replacer)]
     (when replacer
       (vscode/env.clipboard.writeText text)
       (when-not (skip-paste-for-replacer? replacer)
         (p/do (paste-replaced!+ replacer)
               (vscode/env.clipboard.writeText original-clipboard-text)))))))

(defn paste-replaced-text!+
  "For pasting a provided text string.
   If `args` is missing, will show a information message about configuring a shortcut.
   If `args` is a string, will paste that string un-modified.
   If `args` is an array, expects it to be a tuple `[text, replacer]`
   If `replacer` is `null`, will present a menu of configured replacers."
  ([]
   (show-readme-message+ "This command can only be used via a configured Keyboard Shortcut."))
  ([^js args]
   (if (string? args)
     (paste-replaced-text-impl!+ args [])
     (let [clj-args (cljify args)]
       (if (vector? clj-args)
         (apply paste-replaced-text-impl!+ (update clj-args 0 str))
         (show-readme-message+ "`args` to paste-replaced.pasteText needs to be a string or a tuple `[text, replacer]`."))))))

(defn paste-replaced-canned!+
  "Presents a menu from the text items in the EDN file `texts-file`.
   Pastes the selected text using `provided-replacer`.
   `texts-file` defaults to `<workspace-root>/paste-replaced-texts.edn`
   `provided-replacer` defaults to no replacements.
   Providing a `nil` replacer will show a replacers menu."
  ([]
   (paste-replaced-canned!+ nil))
  ([provided-replacer]
   (p/let [config-file (-> (vscode/workspace.getConfiguration "paste-replaced")
                           (.get "canned-texts-file"))]
     (paste-replaced-canned!+ provided-replacer config-file)))
  ([provided-replacer texts-file]
   (p/let [file-exists? (if texts-file
                          (utils/path-or-uri-exists?+ texts-file)
                          (throw (js/Error. "No canned texts file provided")))
           texts (if file-exists?
                   (p/-> texts-file
                         (utils/vscode-read-uri+)
                         (tr/read-string)
                         (p/->>
                          (mapv str-text))
                         (p/catch (fn [e]
                                    (throw (js/Error "Error reading texts file" e)))))
                   (show-readme-message+ (str "Canned texts file not found: " texts-file)))
           choice (when texts 
                    (show-texts-picker!+ texts))] 
     (when choice
       (paste-replaced-text-impl!+ (:text choice) provided-replacer)))))

(defn select-and-paste-replaced!+
  "Selects some text, copies it and then pastes it replaced.
   Restoring original clipboard contents when done.
   `command-args` is a `replacer` object with an optional field
   `selectCommandId` which, if present, will be used for performning
   a selection that will be copied and than pasted, replacing the
   selection using any `replacements`.
   With no `selectCommandId`, the current selection is used."
  ([]
   (select-and-paste-replaced!+ nil))
  ([^js provided-replacer]
   (p/let [original-clipboard-text (vscode/env.clipboard.readText)
           replacer (choose-replacer!+ provided-replacer)
           command-id (some-> replacer :selectCommandId)]
     (when command-id
       (vscode/commands.executeCommand command-id))
     (vscode/commands.executeCommand "editor.action.clipboardCopyAction")
     (when-not (skip-paste-for-replacer? replacer)
       (p/do (paste-replaced!+ replacer)
             (vscode/env.clipboard.writeText original-clipboard-text))))))
