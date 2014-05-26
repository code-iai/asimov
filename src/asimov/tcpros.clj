(ns asimov.tcpros
  (:require [lamina.core :as l]
            [aleph.tcp   :as a]
            [gloss.core  :as g]))

(defn handler [ch client-info]
  (receive-all ch
    #(enqueue ch (str "You said " %))))

(defn listen! []
  (start-tcp-server handler {:port 10000, :frame (string :utf-8 :delimiters ["\r\n"])}))
