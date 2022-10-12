(ns paste-replaced.when-contexts
  (:require ["vscode" :as vscode]))

(defonce ^:private !db (atom {:contexts {::paste-replaced.isActive false
                                         ::paste-replaced.isNReplServerRunning false}}))

(defn set-context!+ [k v]
  (swap! !db assoc-in [:contexts k] v)
  (vscode/commands.executeCommand "setContext" (name k) v))

(defn context [k]
  (get-in @!db [:contexts (if (string? k)
                            (keyword (str "paste-replaced.when-contexts/" k))
                            k)]))

(comment
  (context "paste-replaced.isActive"))