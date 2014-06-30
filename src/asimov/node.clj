(ns asimov.node
  (:require [asimov.slave :as s]
            [asimov.configuration :as config]
            [asimov.tcpros :as tcpros]
            [asimov.util :as util]
            [asimov.messages :as msgs]
            [asimov.master :as m]))
(when-not (:messages @config/configuration)
  (swap! config/configuration
         assoc :messages (msgs/load-msgs "resources/")))

(defn node [name & {:keys [port handlerfn]
                    :or {port 8080 handlerfn s/slave-handler}}]
    (let [n (atom {:name name
                   :addr [(config/cfg :localhost) port]})]
    (swap! n assoc :server (s/start-server :port port
                                           :handler (handlerfn n)))
    n))
(alter-var-root #'*out*
                (constantly *out*))

(defn subscribe [node topic msg-name]
  (prn "hi")
  (let [node-name (util/lookup node :name)
        master-url (util/to-http-addr (util/lookup node :master-addr))
        _ (prn "master-url " master-url)
        node-url (util/to-http-addr (util/lookup node :addr))
        _ (prn "node-url " node-url)
        providers []
        {providers :provider-urls}
        (m/register-subscriber master-url
                               node-name topic msg-name node-url)
        _ (prn "providers " providers)
        provider (util/resolve-ip node (first providers)) ;;todo subscribe to all providers
        _ (prn "providers " providers)
        {port :port host :host}
        (m/request-topic (util/to-http-addr provider) node-name
                         topic [["TCPROS"]])
        host (util/resolve-host node host )
        _ (prn "host port " host port)
        msg (util/msg-by-name node msg-name)
        _ (prn "message " msg)]
    (prn "subscribe with " host port node-name topic #_msg)
    (tcpros/subscribe host port node-name topic msg)))



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
