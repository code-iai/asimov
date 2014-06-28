(ns asimov.messages
  (:require [clojure.set :as set]
            [clojure.core.match :refer [match]]
            [asimov.util :as util]
            [instaparse.core :as insta]
            [pandect.core :as hsh]
            [gloss.core :as g]
            [slingshot.slingshot :as ss]
            [taoensso.timbre :as t]))

(def message-grammar
  " S = {<whitespace?> declaration? <whitespace? comment? ('\\n' | #'\\z')>}
   <declaration> = field | constant
   <field> = unary-field | tuple-field | list-field
   unary-field = type <whitespace> field-name
   tuple-field = type <'['>#'[0-9]+'<']'> <whitespace> field-name
   list-field = type <'[]'> <whitespace> field-name
   constant = numeric-constant | string-constant | bool-constant
   <numeric-constant> = int-constant | float-constant
   <assign> = <whitespace> field-name <whitespace? '=' whitespace?>
   <int-constant>     = int-type assign int-lit
   <float-constant>   = float-type assign float-lit
   <string-constant>  = string-type assign  string-lit
   <bool-constant>    = bool-type assign bool-lit
   <type> = primitive-type / msg-type
   <primitive-type> = int-type | float-type | string-type | bool-type | time-type
   bool-type = 'bool'
   int-type = 'int8' | 'uint8' | 'int16' | 'uint16' | 'int32' | 'uint32' |
              'int64' | 'uint64' | deprecated-int-type
   <deprecated-int-type> = 'byte' | 'char'
   float-type = 'float32' | 'float64'
   string-type = 'string'
   time-type =  'time' | 'duration'
   <field-name> = #'[a-zA-Z][a-zA-Z1-9_]*'
   msg-type = &#'[a-zA-Z]' (#'[0-9a-zA-Z_]+' <'/'>)? #'[0-9a-zA-Z_]+'
   float-lit = #'[-+]?[0-9]*.?[0-9]+([eE][-+]?[0-9]+)?'
   int-lit = #'[-+]?[0-9]*'
   string-lit = #'((?=\\S).*\\S)'?
   bool-lit = 'true' | 'false'
   comment = '#' #'.*'
   whitespace = #'[^\\S\\r\\n]'+
   ")

(def msg-parser
  (insta/parser message-grammar))

(defn primitive-type
  "Tags the given type as primitive and turns it into a keyword."
  [name]
  {:tag :primitive
   :name (keyword name)})

(defn literal
  "Tags the given type as primitive and turns it into a keyword."
  [f]
  (fn [raw]
    {:raw raw
     :read (f raw)}))

(defn transform-parse
  "Transforms the parse tree of a message into a workable list of declarations."
  [parse-res]
  (insta/transform
   {:S vector
    :unary-field
    (fn [type name]
      {:tag :variable
       :type type
       :name name})
    :tuple-field
    (fn [type arity name]
      {:tag :tuple
       :type type
       :name name
       :arity (Integer/parseInt arity)})
    :list-field
    (fn [type name]
      {:tag :list
       :type type
       :name name})
    :constant
    (fn [type name value]
      {:tag :constant
       :type type
       :name name
       :value value})
    :int-type primitive-type
    :float-type primitive-type
    :string-type primitive-type
    :bool-type primitive-type
    :time-type primitive-type
    :double-lit (literal #(Double/parseDouble %))
    :int-lit (literal #(Integer/parseInt %))
    :bool-lit (literal (fn [bl] (case bl "true" true "false" false)))
    :string-lit (literal str)
    :msg-type (fn [&[f s]] {:tag :message
                           :package (when s f)
                           :name (or s f)})}
   parse-res))

(defn make-packages-explicit
  "Takes all declarations that contain a
  message reference without an explicit
  package name and associates the provided
  package name with them."
  [package declarations]
  (mapv (fn [d]
          (if (= :message (-> d :type :tag))
            (update-in d
                       [:type :package]
                       #(cond
                         (= "Header" (-> d :type :name)) "std_msgs"
                         (nil? %) package
                         :else %))
            d))
        declarations))

(defn check-errors [msg p]
  (if (insta/failure? p)
    (do (t/error "Could not parse message!\n" (insta/get-failure p))
        (ss/throw+ {:msg msg :error (insta/get-failure p)} "Error while parsing msg!"))
    p))

(defn annotate-declarations
  "Parses the given message and returns a
  list of its declarations."
  [msgs]
  (into #{} (for [{:keys [package raw] :as msg} msgs
                  :let [declarations (->> raw
                                          msg-parser
                                          (check-errors msg)
                                          transform-parse
                                          (make-packages-explicit package))]]
              (assoc msg :declarations declarations))))

(defn annotate-dependencies
  "Annotates the set of other messages required by this message."
  [msgs]
  (into #{} (for [{:keys [declarations] :as msg} msgs
                  :let [dependencies (->> declarations
                                          (map :type)
                                          (filter #(#{:message} (:tag %)))
                                          (map #(select-keys % [:package :name]))
                                          distinct
                                          (into []))]]
              (assoc msg :dependencies dependencies))))

(defn parse-path [path]
  (when-let [[_ package message]
             (re-matches #".*?([a-zA-Z][0-9a-zA-Z_]*)/msg/([0-9a-zA-Z_]+)\.msg"
                         path)]
    {:package package
     :name message}))

(defn msgs-in-dir [root]
  (->> root
       file-seq
       (filter #(.isFile %))
       (map (fn [f] (when-let [id (parse-path (.getCanonicalPath f))]
                     (assoc id :raw (slurp f)))))
       (remove nil?)))

(defn dep-graph [msgs]
  (into {} (map (fn [msg]
                  [(select-keys msg [:name :package])
                   (into #{} (:dependencies msg))]) msgs)))

(defn ensure-complete-dependencies [msgs]
  (let [dg (dep-graph msgs)
        found-msgs (into #{} (keys dg))]
    (if-let [missing-deps (->> dg
                               (map (fn [[msg deps]]
                                      [msg (clojure.set/difference
                                            deps
                                            found-msgs)]))
                               (filter (fn [[msg deps]]
                                         (not-empty deps)))
                               (into {})
                               not-empty)]
      (ss/throw+ {:tag ::missing-deps :missing missing-deps}
                 "Missing dependencies!")
      msgs)))

(defn ensure-nocycles [msgs]
  (if-let [c (not-empty (util/cycles (dep-graph msgs)))]
    (ss/throw+ {:tag ::circular-msg :cycles c}
               "Can't load circular message definitions!")
    msgs))

(defn- serealize-declaration [d msgs]
  (condp = [(:tag d) (-> d :type :tag)]
    [:constant :primitive]
    (format "%s %s=%s"
            (-> d :type :name name)
            (:name d)
            (-> d :value :raw))
    [:variable :primitive]
    (format "%s %s"
            (-> d :type :name name)
            (:name d))
    [:tuple :primitive]
    (format "%s[%s] %s"
            (-> d :type :name name)
            (:arity d)
            (:name d))
    [:list :primitive]
    (format "%s[] %s"
            (-> d :type :name name)
            (:name d))
    [:variable :message]
    (format "%s %s"
            (get-in msgs [(select-keys (:type d) [:name :package]) :md5])
            (:name d))
    [:tuple :message]
    (format "%s %s"
            (get-in msgs [(select-keys (:type d) [:name :package]) :md5])
            (:name d))
    [:list :message]
    (format "%s %s"
            (get-in msgs [(select-keys (:type d) [:name :package]) :md5])
            (:name d))
    (println d)))

(defn md5-text [msg msgs]
  (let [constant? #(= :constant (:tag %))
        decs (:declarations msg)
        reordered (concat (filter constant? decs)
                          (remove constant? decs))]
    (->> reordered
         (map #(serealize-declaration % msgs))
         (interpose "\n")
         (apply str))))

(defn annotate-md5 [msg msgs]
  (let [text (md5-text msg msgs)
        md5 (hsh/md5 text)]
    (assoc msg :md5 md5)))

;TODO: This is stupid code, because failure=nontermination.
;Replace it with something that creates a dependency tree,
;and then flattens it out, then do a simple reduce.
;Alternatively use an step limited iteration aproach.
(defn annotate-md5s [msgs]
  (loop [annotated {}
         fresh (into #{} msgs)]
    (if (empty? fresh)
      (into #{} (vals annotated))
      (let [msg (some #(when (set/subset? (into #{} (:dependencies %))
                                          (into #{} (keys annotated)))
                         %)
                      fresh)
            amsg (annotate-md5 msg annotated)
            asmg-name (select-keys amsg [:name :package])]
        (recur (assoc annotated
                 asmg-name amsg)
               (disj fresh msg))))))


(defn cat [msg msgs]
  (let [indexed-msgs (set/index msgs [:name :package])
        separator (str (apply str (repeat 80 "=")) "\n")
        dep-text (->> msg
                      (tree-seq #(not-empty (:dependencies %))
                                #(map (comp first indexed-msgs) (:dependencies %)))
                      distinct
                      rest
                      (map (fn [m]
                             (str
                              separator
                              (format "MSG: %s/%s\n" (:package m) (:name m))
                              (:raw m)
                              "\n")))
                      (apply str))]
    (str (:raw msg) "\n" dep-text)))

(defn annotate [msgs k f]
  (->> msgs
       (map #(assoc % k (f % msgs)))
       (into #{})))

(def ros-primitive {:bool    (g/enum :byte {false 0, true 1})
                    :int8    :byte
                    :byte    :byte
                    :uint8   :ubyte
                    :int16   :int16
                    :uint16  :uint16
                    :int32   :int32
                    :uint32  :uint32
                    :int64   :int64
                    :uint64  :uint64
                    :float32 :float32
                    :float64 :float64
                    :string  (g/finite-frame :int32 (g/string :utf-8))})

(defn frame [msg msgs]
  nil)

(defn load-msgs [root]
  (-> root
      msgs-in-dir
      annotate-declarations
      annotate-dependencies
      ensure-nocycles
      ensure-complete-dependencies
      annotate-md5s
      (annotate :cat cat)))
