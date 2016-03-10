(ns asimov.tcpros
  (:require [clojure.core.async :as as]
            [clojure.set :as set]
            [lamina.core :as l]
            [aleph.tcp   :as a]
            [byte-streams :as b]
            [gloss.core  :as g]
            [gloss.io    :as i]
            [taoensso.timbre :as t]
            [asimov.message :as msg]
            [asimov.util :as u]))

(def header-frame
  (g/finite-frame :uint32-le
                  (g/repeated (g/finite-frame :uint32-le
                                              [(g/string :ascii :delimiters [\=])
                                               (g/string :ascii)])
                              :prefix :none)))

(defn encode-header
  "Encodes a tcpros header map for sending over a connection.

Expects:
 h:map The tcpros header map containing arbitary keys and values.

Returns the header in binary encoded form where the contents are simply turned into strings."
  [h]
  (->> h
       (map (fn [[k v]] [(name k) v]))
       (into [])
       (i/encode header-frame)))

(defn decode-header
  "Decodes the tcpros header of a connection.

Expects:
 ch:channel An aleph channel of the connections bytestream.

Returns a vector of a future containing the parsed tcpros header as a map
and a channel containing the undecoded rest of the provided channel."
  [ch]
  (let [ch* (i/decode-channel-headers ch header-frame)
        h (l/read-channel ch*)]
    [ch* (future (->> @h
                      (map (fn [[k v]] [(keyword k) v]))
                      (into {})))]))

(defn subscribe! ;TODO: check pedantic flag and act accordingly.
"Establishes a subscribing tcpros connection with another node.

Expects:
 addr:map The address map describing the node to be connected to.
 callerid:string A string identifying the connecting node.
 topic:string The name of the topic to be subscribed to.

Returns a channel delivering messages from the node connected to."
  [addr callerid topic msg-def]
  (let [ch> (->> (select-keys addr [:host :port])
                 a/client
                 l/wait-for-result)
        [ch< inh] (decode-header (l/mapcat* b/to-byte-buffer ch>))]
    (l/enqueue ch> (encode-header {:message_definition (:cat msg-def)
                                   :callerid callerid
                                   :topic topic
                                   :md5sum (:md5 msg-def)
                                   :type (msg/serialize-id msg-def)}))
    (let [chan (as/chan)
          ch (i/decode-channel ch< (:frame msg-def))]
      (as/go-loop []
        (if-let [msg (try @(l/read-channel ch)
                          (catch IllegalStateException e nil))]
          (do (as/>! chan msg)
              (recur))
          (as/close! chan)))
      chan)))

(defn handler-fn
  "Returns a handler function that accepts and establishes
 publishing tcpros connections with other nodes.

Expects:
 node:atom The node the incomming connections should be connected to.

Returns the handler function to be used with an aleph tcp server."
  [node]
  (fn [ch> client-info]
    (future
      (t/trace "Incomming connection:" client-info)
      (let [n @node
            [ch< inh] (decode-header (l/mapcat* b/to-byte-buffer ch>))
            inh @inh
            reply! #(l/enqueue ch> (encode-header %))
            reply-error! (fn [e]
                           (t/error client-info ":" e)
                           (reply! {:error e}))
            topic (get-in n [:pub (:topic inh)])
            msg-def (:msg-def topic)]
        (t/trace "received Header: " inh)
        (cond
         (not topic)
         (reply-error! (format "No such topic:%s" (:topic inh)))
         (not= (:md5 msg-def) (:md5sum inh))
         (reply-error! (format "Mismatched md5:%s/%s"
                               (:md5 msg-def)
                               (:md5sum inh)))
         (and (:pedantic? topic)
              (not= (:cat msg-def) (:message_definition inh)))
         (reply-error! (format "Mismatched cat:%s/%s"
                               (:cat msg-def)
                               (:message_definition inh)))
         :else
         (do
           (t/trace client-info ":Response seems ok, will reply.")
           (reply! {:md5sum (:md5 msg-def)
                    :type (msg/serialize-id msg-def)})
           (t/trace client-info ":Reply send.")
           (let [ch (as/chan)]
             (t/trace client-info ":Will start go loop.")
             (as/go-loop []
               (if-let [msg (as/<! ch)]
                 (do (l/enqueue ch> (i/encode (:frame msg-def) msg))
                     (recur))
                 (l/close ch>)))
             (t/trace client-info ":Will add new connection.")
             (swap! node update-in
                    [:pub (:topic inh) :connections]
                    conj {:client client-info
                          :chan ch})
             (as/tap (:mult topic) ch))))))))

(defn rand-port
"Returns a random port in the ephemeral port range."
  []
  (+ (rand-int (- 65535 49152)) 49152))

(defn listen!
  "Starts an aleph tcp server on a random port that waits for
incomming subscription to topics of the provided node.

Expects:
 node:atom the node incomming connections are registered on.

Returns a map containing a function to stop the server as well as the randomly choosen port.

Throws an exception if no free port can be found after 1000 retries."
  [node]
  (let [handler (handler-fn node)
        ports (take 1000 (distinct (repeatedly rand-port))) ;TODO: Make retries configurable.
        server (some #(try
                        {:server (a/start-server handler {:port %})
                         :port %}
                        (catch org.jboss.netty.channel.ChannelException e
                          (t/log :info "caught exception: " e)))
                     ports)]
    (if server
      server
      (throw (ex-info "Could not find a free port."
                      {:type ::no-free-port :ports ports})))))
