(ns asimov.slave
  (:use [lamina.core]
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
    :getMasterUri       TODO
    :shutdown           TODO
    :getPid             TODO
    :getSubscriptions   TODO
    :getPublications    TODO
    :paramUpdate        TODO
    :publisherUpdate    TODO
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



(defn start-server []
  (start-http-server async-handler {:port 8080}))