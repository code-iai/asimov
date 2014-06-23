(ns asimov.tcpros
  (:require [lamina.core :as l]
            [aleph.tcp   :as a]
            [gloss.core  :as g]
            [gloss.io    :as i]
            [taoensso.timbre :as t]))
(alter-var-root #'*out* (constantly *out*))

(def header-frame
  (g/finite-frame :uint32-le
                  (g/repeated (g/finite-frame :uint32-le
                                              [(g/string :ascii :delimiters [\=])
                                               (g/string :ascii)])
                              :prefix :none)))

(defn handler [ch client-info]
  (l/siphon ch ch))

;;(a/start-tcp-server handler {:port 10000})

(defn encode-header [h]
  (->> h
       (#(do (println %) %))
       (map (fn [[k v]] [(name k) v]))
       (into [])
       (i/encode header-frame)))

(defn decode-header [ch]
  (let [ch* (i/decode-channel-headers ch header-frame)
        h (l/read-channel ch*)]
    [ch* (future (->> @h 
                      (map (fn [[k v]] [(keyword k) v]))
                      (into {})))]))

(defn subscribe [host port callerid topic msg]
  (let [ch> (-> {:host host
                 :port port}
                a/tcp-client
                l/wait-for-result)
        [ch< inh] (decode-header (l/map* (memfn toByteBuffer) ch>))]
    (l/enqueue ch> (encode-header {:message_definition (:cat msg)
                                        :callerid callerid
                                        :topic topic
                                        :md5sum (:md5 msg)
                                   :type (str (:package msg) "/" (:name msg))}))
    (println @inh)
    ch<))
