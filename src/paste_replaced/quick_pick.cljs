(ns paste-replaced.quick-pick
  (:require ["vscode" :as vscode]
            [paste-replaced.db :as db]
            [promesa.core :as p]))

(defn quick-pick!+ [items options save-as]
  (let [qp ^js (vscode/window.createQuickPick)
        context ^js (:extension-context @db/!app-db)
        ws-state ^js (.-workspaceState context)
        saved-label (.get ws-state save-as)
        saved-items (.filter items (fn [^js item]
                                     (= saved-label (.-label item))))]
    (unchecked-set qp "title" (:title options))
    (unchecked-set qp "items" items)
    (unchecked-set qp "activeItems" saved-items)
    (p/create (fn [resolve _reject]
                (doto qp
                  (.show)
                  (.onDidAccept (fn []
                                  (if (< 0 (-> qp .-selectedItems .-length))
                                    (p/let [choice (-> qp .-selectedItems (unchecked-get 0))]
                                      (resolve choice)
                                      (.update ws-state save-as (.-label choice)))
                                    (resolve js/undefined))
                                  (.hide qp)))
                  (.onDidHide (fn []
                                (resolve #js [])
                                (.hide qp))))))))