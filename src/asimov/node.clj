(ns asimov.node
  (:require [asimov.slave :as s]))

(defn node [name & {:keys [port handler]}]
  {:server (s/start-server )})