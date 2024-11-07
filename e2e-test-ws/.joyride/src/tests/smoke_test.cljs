(ns tests.smoke-test
  (:require ["vscode" :as vscode]
            [cljs.test :refer [deftest testing is]]
            [promesa.core :as p]
            [e2e.macros :refer [deftest-async]]))

(deftest hello
  (testing "We can test things sync things"
    (is (= :foo :foo))
    (is (= 1 (count vscode/workspace.workspaceFolders)))))

(deftest-async extension-activation
  (testing "The extension activates (which is an async operation)"
    (p/let [extension (vscode/extensions.getExtension "betterthantomorrow.paste-replaced")
            api (.activate extension)]
      (is (not= nil? (.getContextValue api))))))
