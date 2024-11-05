(ns paste-replaced.pauses-test
  (:require [clojure.test :refer [deftest is testing]]
            [paste-replaced.pauses :as sut]))

(deftest pause
  (testing "It identifies the correct pause from the input"
    (is (= 75 (sut/pause "foo" :intermediate sut/typing-pauses)))
    (is (= 250 (sut/pause " foo" :intermediate sut/typing-pauses)))
    (is (= 250 (sut/pause "  foo" :intermediate sut/typing-pauses)))
    (is (= 250 (sut/pause "foo\t" :intermediate sut/typing-pauses)))
    (is (= 1300 (sut/pause "\nfoo" :intermediate sut/typing-pauses)))
    (is (= 1300 (sut/pause "foo  bar" :intermediate sut/typing-pauses)))))
