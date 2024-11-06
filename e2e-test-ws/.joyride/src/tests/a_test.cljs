(ns tests.a-test
  (:require ["vscode" :as vscode]
            [cljs.test :refer [deftest testing is]]
            [promesa.core :as p]
            [macros :refer [deftest-async]]))

(deftest hello
  (testing "We can test things"
    (is (= :foo :foo))))

(deftest-async extension-activation
  (testing "The extension activates"
    (p/let [extension (vscode/extensions.getExtension "betterthantomorrow.paste-replaced")
            api (.activate extension)]
      (is (not= nil? (.getContextValue api))))))

