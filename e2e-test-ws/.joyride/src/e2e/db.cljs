(ns e2e.db
  (:require [cljs.test]))

(def !state (atom {:running nil
                   :pass 0
                   :fail 0
                   :error 0}))

