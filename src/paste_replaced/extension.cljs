(ns paste-replaced.extension
  (:require ["vscode" :as vscode]
            [clojure.string :as string]
            [paste-replaced.db :as db]
            [paste-replaced.replacer :as replacer]
            [paste-replaced.utils :as utils :refer [info jsify]]
            [paste-replaced.when-contexts :as when-contexts]
            [promesa.core :as p]))

(defn- register-command! [^js context command-id var]
  (let [disposable (vscode/commands.registerCommand command-id var)]
    (swap! db/!app-db update :disposables conj disposable)
    (.push (.-subscriptions context) disposable)))

(defn- clear-disposables! []
  (swap! db/!app-db assoc :disposables [])
  (p/run! (fn [^js disposable]
            (.dispose disposable))
          (:disposables @db/!app-db)))

(def api (jsify {:getContextValue (fn [k]
                                    (when-contexts/context k))}))

(defn ^:export activate [context]
  (when context
    (swap! db/!app-db assoc
           :output-channel (vscode/window.createOutputChannel "Paste Replaced")
           :extension-context context
           :workspace-root-path vscode/workspace.rootPath
           :typing-interrupted? false)
    (utils/sayln "Paste Replace activating"))
  (let [{:keys [extension-context]} @db/!app-db]
    (register-command! extension-context "paste-replaced.paste" #'replacer/paste-replaced!+)
    (register-command! extension-context "paste-replaced.selectAllAndPasteReplaced" #'replacer/select-all-and-paste-replaced)
    (register-command! extension-context "paste-replaced.selectWordLeftAndPasteReplaced" #'replacer/select-word-left-and-paste-replaced)
    (register-command! extension-context "paste-replaced.interruptTyping" #'replacer/interrupt-typing!)
    (when-contexts/set-context!+ ::when-contexts/paste-replaced.isActive true)
    api))

(defn ^:export deactivate []
  (when-contexts/set-context!+ ::when-contexts/paste-replaced.isActive false)
  (clear-disposables!))


(defn before [done]
  (-> (clear-disposables!)
      (p/then done)))

(defn after []
  (info "shadow-cljs reloaded paste-replaced")
  (js/console.log "shadow-cljs reloaded paste-replaced"))

(comment
  (def ba (before after)))