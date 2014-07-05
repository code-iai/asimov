(ns asimov.xmlrpc
  (:require
   [lamina.core :refer :all]
   [asimov.configuration :refer :all]
   [ring.adapter.jetty :refer :all]
   [aleph.http :refer :all]
   [compojure
    [core :as compojure :refer [defroutes GET POST ANY]]
    [handler :refer [api]]]
   [necessary-evil.core :as xml-rpc]))

(defn gen-return-map [code status-message]
  {:success? (if (= 1 code) true false)
   :status-code code
   :status-message status-message})


(defn register-subscriber
  ([subscriber-node topic msg subscriber-url]
     (register-subscriber *ros-master-url* subscriber-node
                          topic msg subscriber-url))
  ([master-url subscriber-node topic msg subscriber-url]
     (let [[code message provider-url]
           (xml-rpc/call master-url :registerSubscriber
                         subscriber-node topic msg subscriber-url)]
       (-> (gen-return-map code message)
           (assoc :provider-urls provider-url)))))

(defn register-publisher
  ([publisher-node topic msg publisher-url]
     (register-publisher *ros-master-url* publisher-node
                         topic msg publisher-url))
  ([master-url publisher-node topic msg publisher-url]
     (let [[code message provider-url]
           (xml-rpc/call master-url :registerPublisher
                         publisher-node topic msg publisher-url)]
       (-> (gen-return-map code message)
           (assoc :subscriber-urls provider-url)))))

(defn request-topic
  ([subscriber-node topic protocols]
     (request-topic *ros-master-url* subscriber-node topic protocols))
  ([slave-url subscriber-node topic protocols]
     (let [res (xml-rpc/call slave-url :requestTopic
                             subscriber-node topic protocols)
           [code message [protocol host port]]
           (if (= (count res) 2) [(first res) "" (second res)] res)]
       (-> (gen-return-map code message)
           (assoc :host host :port port :protocol protocol)))))

(defn unimpl [& methods]
  (into {}
        (for [m methods]
          [m (fn [& args]
               (t/log "Noop " m " with " args)
               true)])))

(defn slave-handler [atom]
  (xml-rpc/end-point
   (merge {:requestTopic (fn [& args]
                           [1 "status message" ["TCPROS" (first (:addr @atom))
                                                (:port @atom)]])}
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
