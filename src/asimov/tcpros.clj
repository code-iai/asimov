(ns asimov.tcpros
  (:require [clojure.core.async :as as]
            [clojure.set :as set]
            [lamina.core :as l]
            [aleph.tcp   :as a]
            [aleph.formats :as f]
            [gloss.core  :as g]
            [gloss.io    :as i]
            [taoensso.timbre :as t]
            [asimov.util :as u]))

(def header-frame
  (g/finite-frame :uint32-le
                  (g/repeated (g/finite-frame :uint32-le
                                              [(g/string :ascii :delimiters [\=])
                                               (g/string :ascii)])
                              :prefix :none)))

(defn encode-header [h]
  (->> h
       (map (fn [[k v]] [(name k) v]))
       (into [])
       (i/encode header-frame)))

(defn decode-header [ch]
  (let [ch* (i/decode-channel-headers ch header-frame)
        h (l/read-channel ch*)]
    [ch* (future (->> @h
                      (map (fn [[k v]] [(keyword k) v]))
                      (into {})))]))

(defn subscribe! [host port callerid topic msg]
  (let [ch> (->> {:host host
                  :port port}
                 a/tcp-client
                 l/wait-for-result)
        [ch< inh] (decode-header (l/mapcat* f/bytes->byte-buffers ch>))]
    (l/enqueue ch> (encode-header {:message_definition (:cat msg)
                                   :callerid callerid
                                   :topic topic
                                   :md5sum (:md5 msg)
                                   :type (str (:package msg) "/" (:name msg))}))
    (i/decode-channel ch< (:frame msg))))

(defn handler-fn[node]
  (fn [ch> client-info]
    (future
      (t/trace "Incomming onnection:" client-info)
      (let [n @node
            [ch< inh] (decode-header (l/mapcat* f/bytes->byte-buffers ch>))
            inh @inh
            reply! #(l/enqueue ch> (encode-header %))
            reply-error! (fn [e] (t/error client-info ":" e)
                                (reply! {:error e}))
            msg-def (get-in n [:topics (:topic inh) :msg-def])]
        (t/trace "received Header: " inh)
        (cond
         (not msg-def)
         (reply-error! (format "No such topic:%s" (:topic inh)))
         (not= (:md5 msg-def) (:md5sum inh))
         (reply-error! (format "Mismatched md5:%s/%s"
                               (:md5 msg-def)
                               (:md5sum inh)))
         (and (get-in n [:conf :pedantic?])
              (not= (:cat msg-def) (:message_definition inh)))
         (reply-error! (format "Mismatched cat:%s/%s"
                               (:cat msg-def)
                               (:message_definition inh)))
         :else
         (do
           (t/trace client-info ":Response seems ok, will reply.")
           (reply! {:md5sum (:md5 msg-def)
                    :type (str (:package msg-def) "/" (:name msg-def))})
           (t/trace client-info ":Reply send.")
           (let [ch (as/chan)]
             (t/trace client-info ":Will start go loop.")
             (as/go-loop []
               (if-let [msg (as/<! ch)]
                 (do (l/enqueue ch> (i/encode (:frame msg-def) msg))
                     (recur))
                 (l/close ch>)))
             (t/trace client-info ":Will add new connection.")
             (t/spy :trace  (swap! node update-in
                                   [:topics (:topic inh) :connections]
                                   conj {:client client-info :chan ch})))))))))

(defn listen! [node]
  (let [handler (handler-fn node)]
    (a/start-tcp-server handler {:port 10000}))) ;TODO: Choose random port, start server, add port on node or return it?
