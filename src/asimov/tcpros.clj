(ns asimov.tcpros
  (:require [lamina.core :as l]
            [aleph.tcp   :as a]
            [gloss.core  :as g]))

(def header
  (g/finite-frame :uint32-le
                  (g/repeated (g/finite-frame :uint32-le
                                              [(g/string :ascii :delimiters [\=])
                                               (g/string :ascii)])
                              :prefix :none)))

#_(defn handler [ch client-info]
    (receive-all ch
                 #(enqueue ch (str "You said " %))))

#_(defn listen! []
    (start-tcp-server handler {:port 10000, :frame
                               (string :utf-8 :delimiters ["\r\n"])}))
