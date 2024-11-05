(ns util
  (:require [babashka.process :as p]))

(defn throw-if-error [{:keys [exit out err] :as result}]
  (if-not (= exit 0)
    (throw (Exception. (if (empty? out)
                         err
                         out)))
    result))

(defn sh [dry-run? & args]
  (if dry-run?
    (do (println "Dry run:" (apply pr-str args))
        {:exit 0})
    (apply p/sh args)))