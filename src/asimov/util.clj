(ns asimov.util
  (:require [clojure.test :refer :all]
            [byte-streams :as b]
            [clojure.java.io :as io])
  (:import java.nio.ByteBuffer
           java.net.URL))

(defn cycles
"Detects and returns cycles in dependency graphs.
Code borrowed adapted from Cris Grangers https://www.refheap.com/20384

Expects:
 graph:map The graph encoded as a map where each key is a node
           and each value is a set of children.

Returns a set of vectors that contain the cyclic paths of nodes."
  [graph]
  (letfn [(find-cycles
            [cur {:keys [seen root stack graph] :as state}]
            (first (filter identity
                           (for [c (remove seen cur)]
                             (if (= c root)
                               (conj stack c)
                               (find-cycles (get graph c)
                                            (-> state
                                                (update-in [:stack] conj c)
                                                (update-in [:seen] conj c))))))))]
    (into #{}
          (filter identity
                  (for [[root deps] graph
                        :let [stack (find-cycles
                                     deps
                                     {:seen #{}
                                      :stack [root]
                                      :graph graph
                                      :root root})]]
                    stack)))))

(defmethod assert-expr 'thrown-with-data? [msg form]
  ;; (is (thrown-with-data? pred expr))
  ;; Asserts that the message string of the ExceptionInfo exception matches
  ;; (with re-find) the regular expression re.
  ;; Also asserts that the attached data matches the given predicate.
  (let [re (nth form 1)
        pred (nth form 2)
        body (nthnext form 3)]
    `(try ~@body
          (report {:type :fail, :message ~msg, :expected '~form, :actual nil})
          (catch clojure.lang.ExceptionInfo e#
            (let [m# (.getMessage e#)
                  dta# (:object (ex-data e#))]
              (case [(boolean (re-find ~re m#))
                     (boolean (~pred dta#))]
                [true true]
                (report {:type :pass, :message ~msg,
                         :expected '~form, :actual e#})
                [false false]
                (report {:type :fail, :message ~msg,
                         :expected '~form, :actual e#})
                [true false]
                (report {:type :fail, :message ~msg,
                         :expected '~pred, :actual dta#})
                [false true]
                (report {:type :fail, :message ~msg,
                         :expected '~re, :actual m#})))
            e#))))

(defn bytes-to-buffer
"Makes debugging byte fiddling code easier by conveniently generating bytebuffers.

Expects:
 bs:string A textual representation of bytes, written in hex grouped by 2 digits.


Returns a bytebuffer with the encoded bytes."
  [bs]
  (let [bytes (->> (clojure.string/split bs #"\s+")
                   (map (fn [s] (unchecked-byte (Integer/parseInt s 16))))
                   byte-array
                   ByteBuffer/wrap)]
    bytes))

(defn serialize-addr
"Makes working with urls easier by representing them as clojure maps.
Inverse function of `parse-addr`.

Expects:
 addr:map Contains :host and :port entries.

Returns a string of the form  \"http://<host>:<port>\""
  [addr]
  (str (URL. (or (:protocol addr) "http")
             (:host addr)
             (:port addr)
             "")))

(defn parse-addr
"Makes working with urls easier by representing them as clojure maps.
Inverse function of `serialize-addr`.

Expects:
 addr:string Of the form  \"http://<host>:<port>\"

Returns a map with :host and :port."
  [addr]
  (let [url (io/as-url addr)]
    {:protocol (.getProtocol url)
     :host (.getHost url)
     :port (.getPort url)}))

