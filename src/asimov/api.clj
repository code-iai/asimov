(ns asimov.api
  (:require [clojure.set :as set]
            [asimov.xmlrpc :as x]
            [asimov.tcpros :as tcpros]
            [asimov.util :as util]
            [asimov.message :as msgs]
            [taoensso.timbre :as t]))

(defn init-node! [master-url master-port name]
  (let [n (atom {:name name
                 :client {:host "192.168.56.1"}
                 :master {:host master-url :port master-port}
                 :topics #{}
                 :msg-defs {}
                 :srv-defs {}
                 :hosts {}
                 :tcp-server nil
                 :xml-server nil})]
    (swap! n assoc
           :xml-server
           (x/listen! n))
    (swap! n assoc
           :tcp-server
           (tcpros/listen! n))
    n))

(defn lookup-host [addr node]
  (update-in addr [:host]
             #(or ((:hosts node) %)
                  %)))

(defn subscribe! [node topic]
  (let [node @node
        node-name (:name node)
        master-url (util/to-http-addr (:master node))
        node-url (util/to-http-addr (assoc (:client node)
                                      :port
                                      (get-in node [:xml-server :port])))
        msg-id (get-in (x/get-topic-types master-url node-name)
                       [:topic-types topic])
        {providers :provider-urls}
        (x/register-subscriber master-url
                               node-name topic msg-id node-url)
        provider (-> providers
                     first
                     util/deserialize-addr
                     (lookup-host node)
                     util/serialize-addr)
        ;;todo subscribe to all providers
        addr (-> provider
                 (x/request-topic node-name
                                  topic
                                  [["TCPROS"]])
                 (lookup-host node))
        msg (get-in node [:msg-defs (msgs/parse-id msg-id)])]
    (tcpros/subscribe! addr node-name topic msg)))

(defn publish! [node topic msg-id]
  (let [node @node
        msg (get-in node [:msg-defs (msgs/parse-id msg-id)])
        node-name (:name node)
        master-url (util/serialize-addr (:master node))
        node-url (util/serialize-addr (:client node))
        topic-map {topic {:msg-def msg
                          :connections #{}}}]
    (swap! node assoc
           :topics topic-map
           :conf {:pedantic? false})
    (let [res (asimov.tcpros/listen! node)]
      (x/register-publisher master-url node-name topic msg-id node-url)
      res)))

(defn add-msgs!
  ([node path]
     (let [new-msgs (->> path
                         clojure.java.io/file
                         msgs/msgs-in-dir)]
       (swap! node update-in [:msg-defs]
              #(->> new-msgs
                    (merge %)
                    msgs/annotate-all))))
  ([node id raw]
     (let [msg (msgs/parse-id id)]
       (swap! node update-in [:msg-defs]
              #(->> {msg
                     (assoc msg :raw raw)}
                    (merge %)
                    msgs/annotate-all))
       node)))

(defn add-srvs!
  ([node path] (throw (ex-info "Not implemented.")))
  ([node package name raw] (throw (ex-info "Not implemented."))))
