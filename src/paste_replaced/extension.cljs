(ns paste-replaced.extension
  (:require ["vscode" :as vscode]
            [paste-replaced.db :as db]
            [paste-replaced.replacer :as replacer]
            [paste-replaced.utils :as utils :refer [info jsify cljify]]
            [paste-replaced.when-contexts :as when-contexts]
            [promesa.core :as p]))

(defn- register-command! [^js context command-id var]
  (let [disposable (vscode/commands.registerCommand command-id var)]
    (swap! db/!app-db update :disposables conj disposable)
    (.push (.-subscriptions context) disposable)))

(defn- clear-disposables! []
  (-> (p/run! (fn [^js disposable]
                (.dispose disposable))
              (:disposables @db/!app-db))
      (.then (fn [] (swap! db/!app-db assoc :disposables [])))))

(def api (jsify {:getContextValue (fn [k]
                                    (when-contexts/context k))}))

(defn ^:export activate [context]
  (js/console.info "Paste Replace activate START")
  (when context
    (swap! db/!app-db assoc
           :output-channel (vscode/window.createOutputChannel "Paste Replaced")
           :extension-context context
           :workspace-root-path (some-> (cljify vscode/workspace.workspaceFolders)
                                        first)
           :typing-interrupted? false)
    (utils/sayln "Paste Replace activating..."))
  (try (let [{:keys [extension-context]} @db/!app-db]
        (register-command! extension-context "paste-replaced.paste" #'replacer/paste-replaced!+)
        (register-command! extension-context "paste-replaced.pasteFromTexts" #'replacer/paste-replaced-from-texts!+)
        (register-command! extension-context "paste-replaced.pasteText" #'replacer/paste-replaced-text!+)
        (register-command! extension-context "paste-replaced.pasteSelectionReplaced" #'replacer/select-and-paste-replaced!+)
        (register-command! extension-context "paste-replaced.interruptTyping" #'replacer/interrupt-typing!)
        (when-contexts/set-context!+ ::when-contexts/paste-replaced.isActive true)
        (js/console.info "Paste Replace activate END")
        (utils/sayln "Paste Replace activation done"))
      (catch :default e
        (utils/say-error (str "Paste Replace activation failed: " (.-message e) ", see Development Console for stack trace"))
        (throw e)))
  
  api)

(defn ^:export deactivate []
  (when-contexts/set-context!+ ::when-contexts/paste-replaced.isActive false)
  (clear-disposables!))


(defn before [done]
  (js/console.log "shadow-cljs before reloading paste-replaced")
  (-> (clear-disposables!)
      (p/then done)))

(defn after []
  (info "shadow-cljs reloaded paste-replaced")
  (js/console.log "shadow-cljs reloaded paste-replaced")
  (activate nil))

(comment
  #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
  (def ba (before after)))