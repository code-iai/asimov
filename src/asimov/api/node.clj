(ns asimov.api.node
  (:require [asimov.xmlrpc :as x]
            [asimov.configuration :as config]
            [asimov.tcpros :as tcpros]
            [asimov.util :as util]
            [asimov.message :as msgs]
            [taoensso.timbre :as timbre
             :refer [log trace  debug  info  warn  error  fatal  report]]))

(defn init! [master-url name]
  (atom {:name name
         :master-url master-url
         :address [(config/cfg :localhost) port]
         :server (x/start-server :port port
                                           :handler (x/slave-handler n))}))

(defn subscribe! [node topic]
  (let [node-name (util/lookup node :name)
        master-url (util/to-http-addr (util/lookup node :master-addr))
        node-url (util/to-http-addr (util/lookup node :addr))
        providers []
        {providers :provider-urls}
        (x/register-subscriber master-url
                               node-name topic msg-name node-url)
        provider (util/resolve-ip node (first providers)) ;;todo subscribe to all providers
        {port :port host :host}
        (x/request-topic (util/to-http-addr provider) node-name
                         topic [["TCPROS"]])
        host (util/resolve-host node host )
        msg (util/msg-by-name node msg-name)
    (tcpros/subscribe! host port node-name topic msg)))


(defn publish! [node topic msg-name]
  (let [msg (util/msg-by-name node msg-name)
        node-name (util/lookup node :name)
        master-url (util/to-http-addr (:master-addr node))
        node-url (util/to-http-addr (:addr node))
        topic-map {topic {:msg-def msg
                          :connections #{}}}]
    (swap! node assoc
           :topics topic-map
           :port port
           :conf {:pedantic? false})
    (let [res (asimov.tcpros/listen! node)]
      (x/register-publisher master-url node-name topic msg-name node-url)
      res)))
