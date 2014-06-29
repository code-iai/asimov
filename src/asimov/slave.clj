(ns asimov.slave
  (:use [lamina.core]
        [asimov.configuration]
        [ring.adapter.jetty]
        [aleph.http])
  (:require
   [compojure
    [core :as compojure :refer [defroutes GET POST ANY]]
    [handler :refer [api]]]
   [necessary-evil.core :as xml-rpc]))

(def TODO
  (fn [& args] (throw (Exception. "Not implemented"))))

(defn dbg [method & args]
  (do (prn method "with " args)
      true))

(def slave-handler
  (xml-rpc/end-point
   {:getBusStats (partial dbg :getBusStats)        
    :getBusInfo (partial dbg :getBusInfo)         
    :getMasterUri (partial dbg :getMasterUri)       
    :shutdown (partial dbg :shutdown)           
    :getPid (partial dbg :getPid)             
    :getSubscriptions (partial dbg :getSubscriptions)   
    :getPublications (partial dbg :getPublications)    
    :paramUpdate (partial dbg :paramUpdate)        
    :publisherUpdate (partial dbg :publisherUpdate)    
    :requestTopic (partial dbg :requestTopic)       }))
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

(defn start-server
  ([] (start-server 8080))
  ([port]
     (run-jetty #'handler {:port port :join? false})))