(ns asimov.api
  (:require [clojure.set :as set]
            [clojure.core.async :as a]
            [asimov.xmlrpc :as x]
            [asimov.tcpros :as tcpros]
            [asimov.util :as util]
            [asimov.message :as msgs]
            [taoensso.timbre :as t]))

(defn init-node!
"Starts a new ros node.
This node connects to a single master and can subscibe and publish
to other nodes that are connected to it.
Multiple nodes with the same or different masters can be started
simultaneously, this allows for communication with multiple masters
and the creation of multiple services within the same process or repl.

Expects:
 client-host:string  The address of the machine the node runs on, in the masters network.
 master-host:string The adress of the machine running the ros master.
 master-port:number The port on which the master communicates.
 hosts:{string string} A hosts map that has the same function as the hosts file.
 name:string The name of the node.

Returns an atom storing all the connections state.
Which can then be used to publish and subscribe to topics."
  [name client-host master-host master-port hosts]
  (let [n (atom {:name name
                 :client {:host client-host}
                 :master {:host master-host :port master-port}
                 :pub {}
                 :sub {}
                 :hosts hosts
                 :tcp-server nil
                 :xml-server nil})]
    (swap! n assoc
           :xml-server
           (x/listen! n))
    (swap! n assoc
           :tcp-server
           (tcpros/listen! n))
    n))

(defn- lookup-host
"Takes an adress map and returns one.
Tries to lookup the address for the given hostname in the hosts map.
Will just return the name adress map when no entry is found
for a try with the hosts file."
  [addr node]
  (update-in addr [:host]
             #(or ((:hosts node) %)
                  %)))

(defn sub!
"Subscribes a node to a topic.
Currently connection will only happen to those nodes that have already
published the topic. This is subject to change, so that connections will automatically
be added once new publishers appear.

Expects:
 node:atom A node that is supposed to make the subscription.
 msg-def:map The message definition to be used to deserialize received messages.
 topic:string The name of the topic.

Returns a core.async sliding channel that will contain messages received on the topic.
This channel will also be stored on the node atom."
  [node msg-def topic] ;TODO: check pedantic?
  (let [n @node
        node-name (:name n)
        msg-id (msgs/serialize-id msg-def)
        master-url (util/serialize-addr (:master n))
        node-url (util/serialize-addr (merge (:client n)
                                             (:xml-server n)))
        {providers :provider-urls}
        (x/register-subscriber master-url
                               node-name topic msg-id node-url)
        c (a/chan (a/sliding-buffer 1024)) ;TODO: Make buffer size configurable.
        m (a/mix c)
        connections
        (for [p providers
                :let [provider (-> p
                                   util/parse-addr
                                   (lookup-host n)
                                   util/serialize-addr)
                      addr (-> provider
                               (x/request-topic node-name
                                                topic
                                                [["TCPROS"]])
                               (lookup-host n))
                      chan (tcpros/subscribe! addr node-name topic msg-def)]]
          {:server (select-keys addr [:protocol :port :host])
           :chan chan})]
    (doseq [c connections] (a/admix m (:chan c)))
    (swap! node assoc-in
           [:sub topic]
           {:chan c
            :mult m
            :msg-def msg-def
            :connections (into #{} connections)
            :pedantic? false})
    c))

(defn pub!
"Makes the node publish to a topic.

Expects:
 node:atom A node that is supposed to make the publication.
 msg-def:map The message definition to be used to serialize send messages.
 topic:string The name of the topic.

Returns a core.async sliding channel that will forward messages onto the topic.
This channel will also be stored on the node atom."
  [node msg-def topic]
  (let [n @node
        node-name (:name n)
        msg-id (msgs/serialize-id msg-def)
        master-url (util/serialize-addr (:master n))
        node-url (util/serialize-addr (merge (:client n)
                                             (:xml-server n)))
        c (a/chan (a/sliding-buffer 1024)) ;TODO: Make buffer size configurable.
        m (a/mult c)]
    (swap! node assoc-in
           [:pub topic]
           {:chan c
            :mult m
            :msg-def msg-def
            :connections #{}
            :pedantic? false})
    (x/register-publisher master-url node-name topic msg-id node-url)
    c))

(defn msg
"Creates a single message definition from a string.
When loading a message definition for which the id already exists,
the old message definition will be overriden and message definitions
depending on it will be rebuild.
Note that messages must be loaded in the order they depend on each other,
if you want to load multiple messages you should use `msgs`.

Expects:
 msgs:map The messages already loaded.
          It has to contain the dependencies of this message definition.
          Defaults to the empty map.
 id:string The `package/name` identifier of this message.
 raw:string The raw message definition in the ros message definition format.

Returns the given message definition map with the new message added to it."
  ([id raw]
     (msg {} id raw))
  ([msgs id raw]
     (let [msg (msgs/parse-id id)]
       (->> {msg
             (assoc msg :raw raw)}
            (merge msgs)
            msgs/annotate-all))))

(defn msgs
  "Loads all the message definitions contained in the given directory.
Message definitions may be stored at arbitrary depth as long as they follow
the ros `package_name/msgs/msg_name.msg` path convention.
When loading message definitions for which the id already exists,
the old message definitions will be overriden and message definitions
depending on them will be rebuild.
Note that messages must be loaded in the order they depend on each other,
so you should load them either all at once or take appropriate measures.

Expects:
 msgs:map The messages already loaded.
          It has to contain the dependencies of this message definition.
          Defaults to the empty map.
 dir:string The directory from which the messages are to be loaded.

Returns the given message definition map with the new messages added to it."
  ([dir]
     (msgs {} dir))
  ([msgs dir]
     (->> dir
          clojure.java.io/file
          msgs/msgs-in-dir
          (merge msgs)
          msgs/annotate-all)))
