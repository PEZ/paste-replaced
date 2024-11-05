(ns paste-replaced.pauses)

(defn gaussian-rand'
  []
  (let [accuracy 7]
    (loop [rand 0
           i 0]
      (if (< i accuracy)
        (recur (+ rand (.random js/Math)) (inc i))
        (/ rand accuracy)))))

(defn gaussian-rand
  [start end]
  (js/Math.floor
   (+ start (* (gaussian-rand') (+ (- end start) 1)))))

(def typing-pauses
  {:fast {:char 0
          :space 0
          :nl 350
          :description "Typed as a really fast keyboard wielder"}
   :intermediate {:char 75
                  :space 250
                  :nl 1300
                  :description "Typed as an intermediately fast typist"}
   :slow {:char 350
          :space 1000
          :nl 2500
          :description "Typed as a slow, painfully slow, typist"}})

(defn pause
  [s typing-speed pauses]
  (cond
    (re-find #"^ |\t$" s) (-> typing-speed pauses :space)
    (re-find #"\s{2,}|\n" s) (-> typing-speed pauses :nl)
    :else (-> typing-speed pauses :char)))

(defn humanize-pause
  [s typing-speed]
  (gaussian-rand 0 (pause s (keyword typing-speed) typing-pauses)))