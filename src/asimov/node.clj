(ns asimov.node
  (:require [asimov.slave :as s]
            [asimov.configuration :as config]))

(defn node [name & {:keys [port]}]
  (atom {:server (s/start-server port)
         :name name
         :host (config/cfg :localhost)
         :port port}))


(defn subscribe [node topic message]
  )