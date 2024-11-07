(ns tests.foo.bar.b-test
  (:require [cljs.test :refer [deftest testing is]]))

(deftest hello
  (testing "We can test things"
    (is (= :foo :foo))))