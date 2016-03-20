(ns asimov.tcpros
  (:require [clojure.core.async :as as]
            [aleph.tcp :as a]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [byte-streams :as b]
            [gloss.core :as g]
            [gloss.io :as i]
            [gloss.core.formats :as gf]   ; just for the gloss temporary fix
            [gloss.core.protocols :as gp] ; just for the gloss temporary fix
            [gloss.data.bytes :as gb]     ; just for the gloss temporary fix
            [taoensso.timbre :as t]
            [asimov.message :as msg]))

; temporary fix for gloss [START]   TODO remove when gloss issue #48 is included in latest build

(defn- decode-byte-sequence [codecs buf-seq]
  (if (empty? buf-seq)
    (let [[success x remainder] (gp/read-bytes (first codecs) buf-seq)]
      (if success
        [[x] (rest codecs) remainder]
        [nil (cons x (rest codecs)) remainder]))
    (loop [buf-seq buf-seq, vals [], codecs codecs]
      (if (or (empty? codecs) (zero? (gb/byte-count buf-seq)))
        [vals codecs buf-seq]
        (let [[success x remainder] (gp/read-bytes (first codecs) buf-seq)]
          (if success
            (recur remainder (conj vals x) (rest codecs))
            [vals (cons x (rest codecs)) remainder]))))))

(defn decode-stream-headers
  "Given a channel that emits bytes, returns a channel that will emit one decoded frame for
   each frame passed into the function.  After those frames have been decoded, the channel will
   simply emit any bytes that are passed into the source channel."
  [src & frames]
  (let [src (s/->source src)
        dst (s/stream)
        state-ref (atom {:codecs (map g/compile-frame frames) :bytes nil})
        f (fn [bytes]
            (let [{:keys [codecs] :as state} @state-ref]
              (if (empty? codecs)
                (s/put! dst bytes)
                (binding [gp/complete? (s/drained? src)]
                  (let [bytes (-> bytes gf/to-buf-seq gb/dup-bytes)
                        [s codecs remainder] (decode-byte-sequence
                                               codecs
                                               (gb/concat-bytes (:bytes state) bytes))]
                    (reset! state-ref {:codecs codecs :bytes (gf/to-buf-seq remainder)})
                    (let [res (s/put-all! dst s)]
                      (if (empty? codecs)
                        (s/put-all! dst remainder)
                        res)))))))]

    (s/connect-via src f dst {:downstream? false})
    (s/on-drained src #(do (f []) (s/close! dst)))

    dst))

; temporary fix for gloss [END]


(def header-frame
  (g/finite-frame :uint32-le
                  (g/repeated (g/finite-frame :uint32-le
                                              [(g/string :ascii :delimiters [\=])
                                               (g/string :ascii)])
                              :prefix :none)))

(defn wrap-decode-stream-header
  "Wraps a stream to decode the header frame.

Expects:
 s:stream A Manifold stream to decode the header from.

Returns a Manifold stream containing the decoded header
and the undecoded rest of the provided stream."
  [s]
  (let [out (s/stream)]
    (s/connect out s)
    (s/splice
      out
      (decode-stream-headers s header-frame))))
      ;(i/decode-stream-headers s header-frame))))    ; TODO update when gloss issue fixed

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
 s:stream A Manifold stream of the connections bytestream.

Returns the parsed tcpros header as a map."
  [s]
  (let [h (s/take! s)]
    (future (->> @h
                 (map (fn [[k v]] [(keyword k) v]))
                 (into {})))))

(defn subscribe! ;TODO: check pedantic flag and act accordingly.
  "Establishes a subscribing tcpros connection with another node.

Expects:
 addr:map The address map describing the node to be connected to.
 callerid:string A string identifying the connecting node.
 topic:string The name of the topic to be subscribed to.

Returns a channel delivering messages from the node connected to."
  [addr callerid topic msg-def]
  (let [ds (a/client (select-keys addr [:host :port]))
        s  @(d/chain ds wrap-decode-stream-header)
        inh (decode-header s)]
    (t/trace "Send message definition: " (:cat msg-def))
    @(s/put! s (encode-header {:message_definition (:cat msg-def)
                               :callerid callerid
                               :topic topic
                               :md5sum (:md5 msg-def)
                               :type (msg/serialize-id msg-def)}))
    (let [chan (as/chan)
          s*   (i/decode-stream s (:frame msg-def))]
      (t/trace "Received Header: " @inh)
      (t/trace "Will start go loop.")
      (as/go-loop []
        (if-let [msg (try @(s/take! s*)
                          (catch IllegalStateException e nil))]
          (do (as/>! chan msg)
              (recur))
          (as/close! chan)))
      chan)))

(defn handler-fn
  "Returns a handler function that accepts and establishes
 publishing tcpros connections with other nodes.

Expects:
 node:atom The node the incoming connections should be connected to.

Returns the handler function to be used with an aleph tcp server."
  [node]
  (fn [s client-info]
    (future
      (t/trace "Incoming connection:" client-info)
      (let [n @node
            s @(d/chain s wrap-decode-stream-header)
            inh @(decode-header s)
            reply! (fn [m] @(s/put! s (encode-header m)))
            reply-error! (fn [e]
                           (t/error client-info ":" e)
                           (reply! {:error e}))
            topic (get-in n [:pub (:topic inh)])
            msg-def (:msg-def topic)]
        (t/trace "Received Header: " inh)
        (cond
         (not topic)
         (reply-error! (format "No such topic: %s" (:topic inh)))
         (not= (:md5 msg-def) (:md5sum inh))
         (reply-error! (format "Mismatched md5: %s/%s"
                               (:md5 msg-def)
                               (:md5sum inh)))
         (and (:pedantic? topic)
              (not= (:cat msg-def) (:message_definition inh)))
         (reply-error! (format "Mismatched cat: %s/%s"
                               (:cat msg-def)
                               (:message_definition inh)))
         :else
         (do
           (t/trace client-info ": Response seems ok, will reply.")
           (reply! {:md5sum (:md5 msg-def)
                    :type (msg/serialize-id msg-def)})
           (t/trace client-info ": Reply sent.")
           (let [ch (as/chan)]
             (t/trace client-info ": Will start go loop.")
             (as/go-loop []
               (if-let [msg (as/<! ch)]
                 (do @(s/put! s (i/encode (:frame msg-def) msg))
                     (recur))
                 (do (t/trace client-info "Closing connection.") ; TODO remove from map
                     (s/close! s))))
             (t/trace client-info ": Will add new connection.")
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
      (do (t/log :info (str "Started TCP server on port " (:port server)))
          server)
      (throw (ex-info "Could not find a free port."
                      {:type ::no-free-port :ports ports})))))

