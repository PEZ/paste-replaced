(ns paste-replaced.db)

(def init-db {:output-channel nil
              :extension-context nil
              :disposables []
              :workspace-root-path nil})

(defonce !app-db (atom init-db))
