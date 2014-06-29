(ns asimov.master
  (require
   [compojure
    [core :as compojure :refer [defroutes GET POST]]
    [handler :refer [api]]]
   [necessary-evil.core :as xml-rpc]))

;;for testing locally
(def ^:dynamic *ros-master-url* "http://192.168.56.101:11311/")

;(xml-rpc/call *ros-master-url* :getSystemState "/")

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
           (assoc :provider-url provider-url)))))

(defn register-publisher
  ([publisher-node topic msg publisher-url]
     (register-publisher *ros-master-url* publisher-node
                          topic msg publisher-url))
  ([master-url publisher-node topic msg publisher-url]
     (let [[code message provider-url]
           (xml-rpc/call master-url :registerPublisher
                         publisher-node topic msg publisher-url)]
       (-> (gen-return-map code message)
           (assoc :subscriber-url provider-url)))))

(defn request-topic
  ([subscriber-node topic protocols]
     (request-topic *ros-master-url* subscriber-node topic protocols))
  ([slave-url subscriber-node topic protocols]
     (xml-rpc/call slave-url :requestTopic
                   subscriber-node topic protocols)))

(comment
  (let [res (register-subscriber "/asimov" "/turtle1/command_velocity/" "turtlesim/Velocity" "http://localhost:8080")]
    (request-topic (:provider-url res) "/asimov" "turtle1/command_velocity" [["TCPROS"]])))