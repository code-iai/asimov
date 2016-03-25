(ns asimov.xmlrpc
  (:require
   [ring.adapter.jetty :refer :all]
   [aleph.http :refer :all :exclude [get]]
   [taoensso.timbre :as t]
   [compojure
    [core :as compojure :refer [defroutes GET POST ANY]]
    [handler :refer [api]]]
   [necessary-evil.core :as xml-rpc]))

(defn gen-return-map
  [code status-message]
  {:success? (if (= 1 code) true false)
   :status-code code
   :status-message status-message})


(defn register-subscriber
  [master-url subscriber-node topic msg subscriber-url]
  (let [[code message provider-url]
        (xml-rpc/call master-url :registerSubscriber
                      subscriber-node topic msg subscriber-url)]
    (-> (gen-return-map code message)
        (assoc :provider-urls provider-url))))

(defn register-publisher
  [master-url publisher-node topic msg publisher-url]
  (let [[code message subscriber-urls]
        (xml-rpc/call master-url :registerPublisher
                      publisher-node topic msg publisher-url)]
    (-> (gen-return-map code message)
        (assoc :subscriber-urls subscriber-urls))))

(defn request-topic
  [slave-url subscriber-node topic protocols]
  (let [res (xml-rpc/call slave-url :requestTopic
                          subscriber-node topic protocols)
        [code message [protocol host port]]
        (if (= (count res) 2) [(first res) "" (second res)] res)]
    (-> (gen-return-map code message)
        (assoc :host host :port port :protocol protocol))))

(defn get-topic-types
  ([master-url node-name]
     (let [res (xml-rpc/call master-url :getTopicTypes node-name)
           [code message topic-types] res]
       (-> (gen-return-map code message)
           (assoc :topic-types (into {} topic-types))))))

(defn get-param
  [master-url node-name param-name]
  (let [[code message param-value]
        (xml-rpc/call master-url :getParam
                      node-name param-name)]
    (-> (gen-return-map code message)
        (assoc :param-value param-value))))

(defn unimpl
"Used for stubbing out unimplemented xml-rpc requests.

Expects:
 methods:&keyword The methods to be stubbed out.

Returns a map of callbacks which will signal that
an unimplemented handler has been called when executed."
  [& methods]
  (into {}
        (for [m methods]
          [m (fn [& args]
               (t/log :info "Noop " m " with " args)
               true)])))

(defn- handler-fn
"Handler used by the http-server to handle xml-rpc requests by the ros master and other nodes."
  [node]
  (xml-rpc/end-point
   (merge {:requestTopic (fn [& args]
                           (let [n @node]
                             [1 "status message" ["TCPROS"
                                                  (get-in n [:client :host])
                                                  (get-in n [:tcp-server :port])]]))}
          (unimpl
           :getBusStats
           :getBusInfo
           :getMasterUri
           :shutdown
           :getPid
           :getSubscriptions
           :getPublications
           :paramUpdate
           :publisherUpdate))))


(defn listen!
"Starts a http server for communication with the master and other nodes.

Expects:
 node:atom The node that communicates through the server and stores all state.

Returns a map containing the http server instance (:server) as well as its port (:port)."
  [node]
  (let [s (run-jetty (handler-fn node) {:port 0 :join? false})
        p (-> s
              .getConnectors
              first
              .getLocalPort)]
    {:server s :port p}))
