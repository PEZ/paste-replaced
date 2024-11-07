(ns test-runner.runner
  (:require [clojure.string :as string]
            [cljs.test]
            [promesa.core :as p]
            [test-runner.config :as config]
            [test-runner.db :as db]
            ["vscode" :as vscode]))

(defn- write [& xs]
  (js/process.stdout.write (string/join " " xs)))

(defmethod cljs.test/report [:cljs.test/default :begin-test-var] [m]
  (write "===" (str (-> m :var meta :name) ": ")))

(defmethod cljs.test/report [:cljs.test/default :end-test-var] [m]
  (write " ===\n"))

(def old-pass (get-method cljs.test/report [:cljs.test/default :pass]))

(defmethod cljs.test/report [:cljs.test/default :pass] [m]
  (binding [*print-fn* write] (old-pass m))
  (write "✅")
  (swap! db/!state update :pass inc))

(def old-fail (get-method cljs.test/report [:cljs.test/default :fail]))

(defmethod cljs.test/report [:cljs.test/default :fail] [m]
  (binding [*print-fn* write] (old-fail m))
  (write "❌")
  (swap! db/!state update :fail inc))

(def old-error (get-method cljs.test/report [:cljs.test/default :fail]))

(defmethod cljs.test/report [:cljs.test/default :error] [m]
  (binding [*print-fn* write] (old-error m))
  (write "🚫")
  (swap! db/!state update :error inc))

(def old-end-run-tests (get-method cljs.test/report [:cljs.test/default :end-run-tests]))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (binding [*print-fn* write]
    (old-end-run-tests m)
    (let [{:keys [running pass fail error]} @db/!state
          passed-minimum-threshold 2
          fail-reason (cond
                        (< 0 (+ fail error)) "FAILURE: Some tests failed or errored"
                        (< pass passed-minimum-threshold) (str "FAILURE: Less than " passed-minimum-threshold " assertions passed")
                        :else nil)]
      (println "Runner: tests run, results:" (select-keys  @db/!state [:pass :fail :error]))
      (if fail-reason
        (p/reject! running fail-reason)
        (p/resolve! running true)))))

(defn test-file? [file]
  (and (.endsWith file ".cljs")
       (not (.startsWith file "_"))))

(defn file->ns [file]
  (-> file
      (string/replace #"/" ".")
      (string/replace #"\.cljs$" "")
      (string/replace #"^tests\." "")))

(defn find-test-files+ [path]
  (p/->> (vscode/workspace.findFiles (str path "/**/*_test.cljs"))
         (.map vscode/workspace.asRelativePath)))

(defn get-test-namespaces+ [tests-path]
  (p/let [files (find-test-files+ tests-path)]
    (def files files)
    (->> files
         (filter #(test-file? (first %)))
         (map #(file->ns (first %)))
         (map symbol))))

(comment
  (def tests-path "e2e-test-ws/.joyride/src")
  (p/let [test-namespaces (get-test-namespaces+ tests-path)]
    (println "test-namespaces" test-namespaces)
    (def test-namespaces test-namespaces)
    :rcf)
  )

(defn run-all-tests [tests-directory]
  (let [running (p/deferred)]
    (swap! db/!state assoc :running running)
    (try
      (doseq [ns-sym (config/ns-symbols)]
        (require ns-sym)
        (cljs.test/run-tests ns-sym))
      (catch :default e
        (p/reject! (:running @db/!state) e)))
    running))

(comment
  (run-all-tests)
  :rcf)

