(ns tasks
  (:require publish))

(defn publish! [& args]
  (apply publish/run args))

(defn print-release-notes! [version]
  (let [changelog-text (publish/get-changelog-text-for-version version)]
    (println changelog-text)))