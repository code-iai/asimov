(ns asimov.api
  (:require [asimov.slave :as s]
            [asimov.configuration :as config]
            [asimov.tcpros :as tcpros]
            [asimov.util :as util]
            [asimov.message :as msgs]
            [asimov.master :as m]
            [taoensso.timbre :as timbre
             :refer [log trace  debug  info  warn  error  fatal  report]]))

(timbre/set-level! :trace)

(when-not (:messages @config/configuration)
  (swap! config/configuration
         assoc :messages (msgs/load-msgs "resources/")))

(defn node! [name & {:keys [port handlerfn]
                     :or {port 8080 handlerfn s/slave-handler}}]
  (let [n (atom {:name name
                 :addr [(config/cfg :localhost) port]})]
    (swap! n assoc :server (s/start-server :port port
                                           :handler (handlerfn n)))
    n))
(alter-var-root #'*out*
                (constantly *out*))

(defn subscribe! [node topic msg-name]
  (let [node-name (util/lookup node :name)
        master-url (util/to-http-addr (util/lookup node :master-addr))
        _ (trace "master-url " master-url)
        node-url (util/to-http-addr (util/lookup node :addr))
        _ (trace "node-url " node-url)
        providers []
        {providers :provider-urls}
        (m/register-subscriber master-url
                               node-name topic msg-name node-url)
        _ (trace "providers " providers)
        provider (util/resolve-ip node (first providers)) ;;todo subscribe to all providers
        _ (trace "providers " providers)
        {port :port host :host}
        (m/request-topic (util/to-http-addr provider) node-name
                         topic [["TCPROS"]])
        host (util/resolve-host node host )
        _ (trace "host port " host port)
        msg (util/msg-by-name node msg-name)
        _ (trace "message " msg)]
    (log "Subscribed with " host port node-name topic #_msg)
    (tcpros/subscribe host port node-name topic msg)))
