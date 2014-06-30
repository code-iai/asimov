(ns asimov.node
  (:require [asimov.slave :as s]
            [asimov.configuration :as config]
            [asimov.tcpros :as tcpros]
            [asimov.messages :as msgs]
            [asimov.master :as m]))

(defn node [name & {:keys [port handlerfn]
                    :or {port 8080 handlerfn s/slave-handler}}]
  (let [n (atom {:name name
                 :host (config/cfg :localhost)
                 :port port})]
    (swap! n assoc :server (s/start-server {:port port
                                            :handler (handlerfn n)}))
    n))


(defn subscribe-to-topic [node-name topic msg-name]
  (let [res (m/register-subscriber node-name topic msg-name "http://localhost:8080")
        _ (prn "res " res)
        provider-url (first (:provider-url res))
        _ (prn "provider url" provider-url)
        res (m/request-topic "http://192.168.254.101:56766" node-name topic [["TCPROS"]])
        _ (prn "request-res " res)
        [_ [_ host port]] res
        host "192.168.254.101"
         _ (prn host port)
         msgs (msgs/load-msgs (clojure.java.io/file "resources/"))
         idx (clojure.set/index msgs [:name :package])
         [package name] (.split msg-name "/")
         msg (first (idx {:name name :package package}))
         _ (prn package name msg)
        ]
    (#'tcpros/subscribe host port node-name topic msg)))
