(ns asimov.messages
  (:require [asimov.util :as util]
            [instaparse.core :as insta]
            [pandect.core :as hsh]
            [slingshot.slingshot :as ss]))

(def msg-parser
  (insta/parser
   " S = {declaration? <whitespace? comment? ('\\n' | #'\\z')>}
   <declaration> = field | constant
   <field> = unary-field | tuple-field | list-field
   unary-field = type <whitespace> field-name
   tuple-field = type <'['>#'[0-9]+'<']'> <whitespace> field-name
   list-field = type <'[]'> <whitespace> field-name
   constant = numeric-constant | string-constant | bool-constant
   <numeric-constant> = int-constant | float-constant
   <int-constant>     = int-type <whitespace> field-name <whitespace?> <'='> <whitespace?> int-lit
   <float-constant>   = float-type <whitespace> field-name <whitespace?> <'='> <whitespace?> float-lit
   <string-constant>  = string-type  <whitespace> field-name <whitespace?> <'='> <whitespace?> string-lit
   <bool-constant>    = bool-type    <whitespace> field-name <whitespace?> <'='> <whitespace?> bool-lit
   <type> = primitive-type / msg-type
   <primitive-type> = int-type | float-type | string-type | bool-type | time-type
   bool-type = 'bool'
   int-type = 'int8' | 'uint8' | 'int16' | 'uint16' | 'int32' | 'uint32' | 'int64' | 'uint64'
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
   "))

(defn- ^{:testable true} primitive-type
  "Tags the given type as primitive and turns it into a keyword."
  [name]
  {:tag :primitive
   :name (keyword name)})

(defn- ^{:testable true} transform-parse
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
    :double-lit #(Double/parseDouble %)
    :int-lit #(Integer/parseInt %)
    :bool-lit (fn [bl] (case bl "true" true "false" false))
    :string-lit str
    :msg-type (fn [&[f s]] {:tag :message
                            :package (when s f)
                            :name (or s f)})}
   parse-res))

(defn- ^{:testable true} make-packages-explicit
  "Takes all declarations that contain a
  message reference without an explicit
  package name and associates the provided
  package name with them."
  [package declarations]
  (mapv (fn [d]
          (if (= :message (get-in d [:type :tag]))
            (update-in d [:type :package] #(or % package))
            d))
        declarations))

(defn- ^{:testable true} annotate-declarations
  "Parses the given message and returns a
   list of its declarations."
  [{:keys [package raw] :as msg}]
  (let [declarations (->> raw
                          msg-parser
                          transform-parse
                          (make-packages-explicit package))]
    (assoc msg :declarations declarations)))

(defn- ^{:testable true} annotate-dependencies
  "Annotates the set of other messages required by this message."
  [{:keys [declarations] :as msg}]
  (let [dependencies (->> declarations
                          (map :type)
                          (filter #(#{:message} (:tag %)))
                          (map #(select-keys % [:package :name]))
                          (into #{}))]
    (assoc msg :dependencies dependencies)))

(defn- ^{:testable true} parse-path [path]
  (when-let [[_ package message]
             (re-matches #".*?([a-zA-Z][0-9a-zA-Z_]*)/msg/([0-9a-zA-Z_]+)\.msg"
                         path)]
    {:package package
     :name message}))

(defn- ^{:testable true} msgs-in-dir [root]
  (->> root
       file-seq
       (filter #(.isFile %))
       (map (fn [f] (when-let [id (parse-path (.getCanonicalPath f))]
                      (assoc id :raw (slurp f)))))
       (remove nil?)))

(defn- ^{:testable true} dep-graph [msgs]
  (into {} (map (fn [msg]
                  [(select-keys msg [:name :package])
                   (:dependencies msg)]) msgs)))

(defn- ^{:testable true} ensure-complete-dependencies [msgs]
  (let [dg (dep-graph msgs)
        found-msgs (into #{}
                         (keys dg))]
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

(defn- ^{:testable true} ensure-nocycles [msgs]
  (if-let [c (not-empty (util/cycles (dep-graph msgs)))]
    (ss/throw+ {:tag ::circular-msg :cycles c} "Can't load circular message definitions!")
    msgs))

(defn- ^{:testable true} annotate-all-md5s [msgs]
  nil)

(defn load-msgs [root]
  (->> root
       msgs-in-dir
       (mapv annotate-declarations)
       (mapv annotate-dependencies)
       ensure-nocycles
       ensure-complete-dependencies
       annotate-all-md5s))
