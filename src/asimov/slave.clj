(ns asimov.slave
  (:use [lamina.core]
        [asimov.configuration]
        [aleph.http])
  (:require
   [compojure
    [core :as compojure :refer [defroutes GET POST]]
    [handler :refer [api]]]
   [necessary-evil.core :as xml-rpc]))

(def TODO
  (fn [& args] (throw (Exception. "Not implemented"))))

(def slave-handler
  (xml-rpc/end-point
   {:getBusStats        TODO
    :getBusInfo         TODO
    :getMasterUri       (fn [caller_id] (cfg :master-uri))
    :shutdown           TODO
    :getPid             TODO
    :getSubscriptions   (fn [caller_id] (cfg :subscriptions))
    :getPublications    (fn [caller_id] (cfg :publications))
    :paramUpdate        TODO
    :publisherUpdate    (fn [caller_id topic publishers]
                          (assoc-in @configuration
                                    [:subscriptions topic] publishers))
    :requestTopic       TODO}))
(alter-var-root #'*out* (constantly *out*))

(defn wrap-prn [handler]
  (fn [req]
    (prn "request " (pr-str req))
    (let [resp (handler req)]
      (prn "response " (pr-str resp))
      resp)))

(def handler
  (-> slave-handler
      wrap-prn))

(defn async-handler [channel request]
  (enqueue channel (#'handler request)))



(defn start-server
  ([] (start-server 8080))
  ([port]
     (start-http-server async-handler {:port port})))