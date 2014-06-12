(ns asimov.util
  (:require [midje.sweet :as midje]))

(defn cycles
  "Detects and returns cycles in dependency graphs.
  Code adapted from Cris Grangers https://www.refheap.com/20384"
  [graph]
  (letfn [(find-cycles [cur {:keys [seen root stack graph] :as state}]
                       (first (filter identity (for [c (remove seen cur)]
                                                 (if (= c root)
                                                   (conj stack c)
                                                   (find-cycles (get graph c) (-> state
                                                                                  (update-in [:stack] conj c)
                                                                                  (update-in [:seen] conj c))))))))]
    (into #{}
          (filter identity
                  (for [[root deps] graph
                        :let [stack (find-cycles deps {:seen #{} :stack [root] :graph graph :root root})]]
                    stack)))))

(defn throws+ [message data]
  (midje/throws
   clojure.lang.ExceptionInfo
   message
   (fn [exception] (= data (:object (.getData exception))))))
