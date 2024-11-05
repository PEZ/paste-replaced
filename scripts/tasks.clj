(ns tasks
  (:require [clojure.string :as string]
            publish
            util))

(defn publish! [& args]
  (apply publish/run args))

(defn print-release-notes! [version]
  (let [changelog-text (publish/get-changelog-text-for-version version)]
    (println changelog-text)))

(defn -bump-version! [user-email user-name dry-run?]
  (println "Bumping version")
  (util/sh dry-run? "git" "config" "--global" "user.email" user-email)
  (util/sh dry-run? "git" "config" "--global" "user.name" user-name)
  (util/sh dry-run? "npm" "set" "git-tag-version" "false")
  (util/sh dry-run? "npm" "version" "patch")
  (util/sh dry-run? "git" "add" ".")
  (let [version (-> (util/sh false "node" "-p" "require('./package').version")
                    :out
                    string/trim)]
    (util/sh dry-run? "git" "commit" "-m" (str "Bring on version " version "!")))
  (util/sh dry-run? "git" "push" "origin" "HEAD"))

(defn bump-version! [& args]
  (let [[user-email user-name dry-arg] args]
    (-bump-version! user-email user-name (when dry-arg true))))

(comment
  (bump-version! "pez@pezius.com" "Peter Strömberg" "-d"))

(defn -package-pre-release! [branch dry-run?]
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
    (println (:out (util/sh dry-run? "npm" "version" "--no-git-tag-version" "prerelease" "--preid" pre-id)))
    (println (:out (util/sh dry-run? "npx" "vsce" "package" "--pre-release")))
    (println (:out (util/sh dry-run? "npm" "version" "--no-git-tag-version" current-version)))))

(defn package-pre-release! [& args]
  (let [[branch dry-arg] args]
    (-package-pre-release! branch (when dry-arg true))))

(comment
  (package-pre-release! "-d")
  :rcf)

