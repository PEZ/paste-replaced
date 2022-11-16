(ns paste-replaced.utils
  (:require ["vscode" :as vscode]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [paste-replaced.db :as db]
            [promesa.core :as p]))

(defn jsify [clj-thing]
  (clj->js clj-thing))

(defn cljify [js-thing]
  (js->clj js-thing :keywordize-keys true))

(defn- ws-uri [path-or-uri]
  (p/let [^js uri (if (= (type "")
                         (type path-or-uri))
                    (vscode/Uri.file path-or-uri)
                    path-or-uri)]
    (vscode/Uri.joinPath (-> @db/!app-db
                             :workspace-root-path
                             :uri)
                         (.-fsPath uri))))

(defn path-or-uri-exists?+ [path-or-uri]
  (-> (p/let [^js uri (ws-uri path-or-uri)
              _stat (vscode/workspace.fs.stat uri)])
      (p/handle
       (fn [_r, e]
         (if e
           false
           true)))))

(defn vscode-read-uri+ [^js uri-or-path]
  (p/let [^js uri (ws-uri uri-or-path)]
    (-> (p/let [_ (vscode/workspace.fs.stat uri)
                data (vscode/workspace.fs.readFile uri)
                decoder (js/TextDecoder. "utf-8")
                text (.decode decoder data)]
          text))))

(defn workspace-root []
  vscode/workspace.rootPath)

(defn info [& xs]
  (vscode/window.showInformationMessage (str/join " " (mapv str xs))))

(defn warn [& xs]
  (vscode/window.showWarningMessage (str/join " " (mapv str xs))))

(defn error [& xs]
  (vscode/window.showErrorMessage (str/join " " (mapv str xs))))

(def ^{:dynamic true
       :doc "Should the Paste Replaced output channel be revealed after `say`?
             Default: `true`"}
  *show-when-said?* false)

(defn sayln [message]
  (let [channel ^js (:output-channel @db/!app-db)]
    (.appendLine channel message)
    (when *show-when-said?*
      (.show channel true))))

(defn say [message]
  (let [channel ^js (:output-channel @db/!app-db)]
    (.append channel message)
    (when *show-when-said?*
      (.show channel true))))

(defn say-error [message]
  (sayln (str "ERROR: " message)))

(defn say-result
  ([result]
   (say-result nil result))
  ([message result]
   (let [prefix (if (empty? message)
                  "=> "
                  (str message "\n=> "))]
     (.append ^js (:output-channel @db/!app-db) prefix)
     (sayln (with-out-str (pprint/pprint result))))))

(defn extension-path []
  (-> ^js (:extension-context @db/!app-db)
      (.-extensionPath)))