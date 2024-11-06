(ns tasks
  (:require [babashka.process :as p]
            [clojure.string :as string]
            publish
            util
            [babashka.cli :as cli]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn publish! [args]
  (publish/yolo! args))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn print-release-notes! [{:keys [version]}]
  (let [changelog-text (publish/get-changelog-text-for-version version)]
    (println changelog-text)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn bump-version! [{:keys [user-email user-name dry]}]
  (println "Bumping version")
  (util/sh dry "git" "config" "--global" "user.email" user-email)
  (util/sh dry "git" "config" "--global" "user.name" user-name)
  (util/sh dry "npm" "set" "git-tag-version" "false")
  (util/sh dry "npm" "version" "patch")
  (util/sh dry "git" "add" ".")
  (let [version (-> (util/sh false "node" "-p" "require('./package').version")
                    :out
                    string/trim)]
    (util/sh dry "git" "commit" "-m" (str "Bring on version " version "!")))
  (util/sh dry "git" "push" "origin" "HEAD"))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn package-pre-release! [{:keys [branch dry]}]
  (let [current-version (-> (util/sh false "node" "-p" "require('./package').version")
                            :out string/trim)
        commit-id (-> (util/sh false "git" "rev-parse" "--short" "HEAD")
                      :out string/trim)
        random-slug (util/random-slug 2)
        slugged-branch (string/replace branch #"/" "-")
        pre-id (str slugged-branch "-" commit-id "-" random-slug)]
    (println "Current version:" current-version)
    (println "HEAD Commit ID:" commit-id)
    (println "Packaging pre-release...")
    (println (:out (util/sh dry "npm" "version" "--no-git-tag-version" "prerelease" "--preid" pre-id)))
    (println (:out (util/sh dry "npx" "vsce" "package" "--pre-release")))
    (println (:out (util/sh dry "npm" "version" "--no-git-tag-version" current-version)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn run-e2e-tests-with-vsix! [{:keys [vsix]}]
  (println "Running end-to-end tests using vsix:" vsix)
  (p/shell "node" "./e2e-test-ws/launch.js" (str "--vsix=" vsix)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn run-e2e-tests-from-working-dir! []
  (println "Running end-to-end tests using working directory")
  (p/shell "node" "./e2e-test-ws/launch.js"))
