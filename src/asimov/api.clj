(ns asimov.api
  (:require [clojure.set :as set]
            [clojure.core.async :as a]
            [asimov.xmlrpc :as x]
            [asimov.tcpros :as tcpros]
            [asimov.util :as util]
            [asimov.message :as msgs]
            [taoensso.timbre :as t]))

(defn init-node! [master-url master-port name]
  (let [n (atom {:name name
                 :client {:host "192.168.56.1"}
                 :master {:host master-url :port master-port}
                 :pub {}
                 :sub {}
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

(defn sub! [node msg-def topic]
  (let [n @node
        node-name (:name n)
        msg-id (msgs/serialize-id msg-def)
        master-url (util/serialize-addr (:master n))
        node-url (util/serialize-addr (merge (:client n)
                                             (:xml-server n)))
        {providers :provider-urls}
        (x/register-subscriber master-url
                               node-name topic msg-id node-url)
        provider (-> providers
                     first
                     util/parse-addr
                     (lookup-host node)
                     util/serialize-addr)
        ;;todo subscribe to all providers
        addr (-> provider
                 (x/request-topic node-name
                                  topic
                                  [["TCPROS"]])
                 (lookup-host node))]
    (tcpros/subscribe! addr node-name topic msg-def)))

(defn pub! [node msg-def topic]
  (let [n @node
        node-name (:name n)
        msg-id (msgs/serialize-id msg-def)
        master-url (util/serialize-addr (:master n))
        node-url (util/serialize-addr (merge (:client n)
                                             (:tcp-server n)))
        c (a/chan (a/sliding-buffer 1))]
    (swap! node assoc-in
           [:pub topic]
           {:chan c
            :msg-def msg-def
            :connections #{}
            :pedantic? false})
    (x/register-publisher master-url node-name topic msg-id node-url)
    c))

(defn msg
  ([id raw]
     (msg {} id raw))
  ([msgs id raw]
     (let [msg (msgs/parse-id id)]
       (->> {msg
             (assoc msg :raw raw)}
            (merge msgs)
            msgs/annotate-all))))

(defn msgs
  ([path]
     (msgs {} path))
  ([msgs path]
     (->> path
          clojure.java.io/file
          msgs/msgs-in-dir
          (merge msgs)
          msgs/annotate-all)))
