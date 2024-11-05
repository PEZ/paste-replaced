(ns tasks
  (:require [clojure.string :as string]
            publish
            util))

(defn publish! [& args]
  (apply publish/run args))

(defn print-release-notes! [version]
  (let [changelog-text (publish/get-changelog-text-for-version version)]
    (println changelog-text)))

; git config --global user.email "$GITHUB_ACTOR@users.noreply.github.com"
; git config --global user.name "$GITHUB_ACTOR"
; git checkout dev
; npm set git-tag-version false && npm version patch
; git pull
; git add .
; git commit -m "Bring on version $(node -p "require('./package').version")!"
; git push origin HEAD

(defn -bump-version! [user-email user-name dry-run?]
  (println "Bumping version")
  (util/sh dry-run? "git" "config" "--global" "user.email" user-email)
  (util/sh dry-run? "git" "config" "--global" "user.name" user-name)
  (util/sh dry-run? "npm" "set" "git-tag-version" "false")
  (util/sh dry-run? "npm" "version" "patch")
  (util/sh dry-run? "git" "pull")
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