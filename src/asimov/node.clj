(ns asimov.node
  (:require [asimov.slave :as s]
            [asimov.configuration :as config]))

(defn node [name & {:keys [port handlerfn]
                    :or {port 8080 handlerfn s/slave-handler}}]
  (let [n (atom {:name name
                 :host (config/cfg :localhost)
                 :port port})]
    (swap! n assoc :server (s/start-server {:port port
                                            :handler (handlerfn n)}))
    n))


(defn subscribe [node topic message]
  )