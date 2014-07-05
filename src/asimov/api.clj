(ns asimov.api
  (:require [clojure.set :as set]
            [asimov.xmlrpc :as x]
            [asimov.tcpros :as tcpros]
            [asimov.util :as util]
            [asimov.message :as msgs]
            [taoensso.timbre :as timbre
             :refer [log trace  debug  info  warn  error  fatal  report]]))

(defn init-node! [master-url name]
  (atom {:name name
         :address [(util/localhost) port]
         :master-url master-url
         :topics #{}
         :msg-defs {}
         :srv-defs {}
         :xml-server (x/start-server :port port
                                     :handler (x/slave-handler n))
         :tcp-server nil)})

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
        (tcpros/subscribe! host port node-name topic msg))))

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

(defn add-message!
  ([node path]
     (swap! node update-in [:msg-defs]
            #(->> path
                  clojure.java.io/file
                  msgs/msgs-in-dir
                  (merge %)
                  msgs/annotate-all)))
  ([node id raw]
     (let [[_ package name] (re-matches #"([^/]*)/([^/]*)")]
       (swap! node update-in [:msg-defs]
              #(->> {id
                     {:name name
                      :package package
                      :raw raw}}
                    (merge %)
                    msgs/annotate-all)))))

(defn add-service!
  ([node path] (throw (ex-info "Not implemented.")))
  ([node package name raw] (throw (ex-info "Not implemented."))))
