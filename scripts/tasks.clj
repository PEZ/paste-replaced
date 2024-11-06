(ns tasks
  (:require [babashka.process :as p]
            [clojure.string :as string]
            publish
            util))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn publish! [args]
  (publish/yolo! args))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn print-release-notes! [{:keys [version]}]
  (let [changelog-text (publish/get-changelog-text-for-version version)]
    (println changelog-text)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn bump-version! [{:keys [user-email user-name dry force]}]
  (if force
    (do
      (println "Bumping version")
      (util/shell dry "git" "config" "--global" "user.email" user-email)
      (util/shell dry "git" "config" "--global" "user.name" user-name)
      (util/shell dry "npm" "version" "--no-git-tag-version" "patch")
      (util/shell dry "git" "add" ".")
      (let [version (-> (util/sh false "node" "-p" "require('./package').version")
                        :out
                        string/trim)]
        (util/shell dry "git" "commit" "-m" (str "Bring on version " version "!")))
      (util/shell dry "git" "push" "origin" "HEAD"))
    (println "Use --force to actually bump the version")))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn package-pre-release! [{:keys [branch dry]}]
  (let [current-version (-> (util/sh false "node" "-p" "require('./package').version")
                            :out string/trim)
        commit-id (-> (util/sh false "git" "rev-parse" "--short" "HEAD")
                      :out string/trim)
        random-slug (util/random-slug 2)
        slugged-branch (string/replace branch #"/" "-")
        version (str current-version "-" slugged-branch "-" commit-id "-" random-slug)]
    (println "Current version:" current-version)
    (println "HEAD Commit ID:" commit-id)
    (println "Packaging pre-release...")
    (util/shell dry "npm" "version" "--no-git-tag-version" version)
    (util/shell dry "npx" "vsce" "package" "--pre-release")
    (util/shell dry "npm" "version" "--no-git-tag-version" current-version)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn run-e2e-tests-with-vsix! [{:keys [vsix]}]
  (println "Running end-to-end tests using vsix:" vsix)
  (util/shell false "node" "./e2e-test-ws/launch.js" (str "--vsix=" vsix)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn run-e2e-tests-from-working-dir! []
  (println "Running end-to-end tests using working directory")
  (util/shell false "node" "./e2e-test-ws/launch.js"))
