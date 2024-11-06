(ns publish
  (:require [clojure.string :as string]
            [cheshire.core :as json]
            util))

(def changelog-filename "CHANGELOG.md")

(defn release-pattern [version]
  (re-pattern (str "## \\[" (string/replace version "." "\\.") "\\].*")))

(defn get-changelog-text-for-version [version]
  (let [pattern (release-pattern version)]
    (some-> (slurp changelog-filename)
            (string/split pattern)
            second
            (string/split #"##")
            first
            string/trim)))

(comment
  (get-changelog-text-for-version "v1.1.12")
  :rcf)

(defn new-changelog-text
  [changelog-text version]
  (let [utc-date (-> (java.time.Instant/now)
                     .toString
                     (clojure.string/split #"T")
                     (nth 0))
        new-header (format "## [v%s] - %s" version utc-date)
        new-text (string/replace-first
                  changelog-text
                  (release-pattern "Unreleased")
                  (format "## [Unreleased]\n\n%s" new-header))]
    new-text))

(defn commit-changelog [file-name message dry-run?]
  (println "Committing")
  (util/sh dry-run? "git" "add" file-name)
  (util/sh dry-run?
           "git" "commit"
           "-m" message
           "-o" file-name))

(defn tag [version dry-run?]
  (println "Tagging with version" version)
  (util/sh dry-run?
           "git" "tag"
           "-a" (str "v" version)
           "-m" (str "Version " version)))

(defn push [dry-run?]
  (println "Pushing")
  (util/sh dry-run? "git" "push" "--follow-tags"))

(defn git-status []
  (println "Checking git status")
  (let [result (util/sh false "git" "status")
        out (:out result)
        [_ branch] (re-find #"^On branch (\S+)\n" out)
        up-to-date (re-find #"Your branch is up to date" out)
        clean (re-find #"nothing to commit, working tree clean" out)]
    (cond-> #{}
      (not= "master" branch) (conj :not-on-master)
      (not up-to-date) (conj :not-up-to-date)
      (not clean) (conj :branch-not-clean))))

(defn tag-and-push! [version dry-run?]
  (tag version dry-run?)
  (push dry-run?)
  (println "Open to follow the progress of the release:")
  (println "  https://github.com/PEZ/paste-replaced/actions"))

(defn publish! [unreleased-changelog-text dry-run?]
  (let [changelog-text (slurp changelog-filename)
        extension-version (-> (slurp "package.json")
                              json/parse-string
                              (get "version"))]
    (if (empty? unreleased-changelog-text)
      (do
        (println "Publishing without updating the changelog.")
        (tag-and-push! extension-version dry-run?))
      (let [updated-changelog-text (new-changelog-text changelog-text extension-version)]
        (println "Updating changelog")
        (if-not dry-run?
          (spit changelog-filename updated-changelog-text)
          (println "Would write to changelog: " changelog-filename (subs updated-changelog-text 0 200) "..."))
        (commit-changelog changelog-filename
                          (str "Add changelog section for v" extension-version)
                          dry-run?)
        (tag-and-push! extension-version dry-run?)))))

(defn yolo! [{:keys [dry]}]
  (let [unreleased-changelog-text (get-changelog-text-for-version "Unreleased")

        status (git-status)]
    (println "dry-run?" dry)
    (if (or (seq status)
            (empty? unreleased-changelog-text))
      (do
        (when (seq status)
          (println "Git status issues: " status))
        (when (empty? unreleased-changelog-text)
          (println "There are no unreleased changes in the changelog."))
        (println "Release anyway? YES/NO: ")
        (flush)
        (let [answer (str (read))]
          (if-not (= "YES" answer)
            (println "Aborting publish.")
            (publish! unreleased-changelog-text dry))))
      (publish! unreleased-changelog-text dry))))
