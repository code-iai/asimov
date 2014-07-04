(ns asimov.message
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

(defn declarations
  "Parses the given message and returns a
  list of its declarations."
  [{:keys [package raw] :as msg} msgs]
  (->> raw
       msg-parser
       (check-errors msg)
       transform-parse
       (make-packages-explicit package)))

(defn dependencies
  "Annotates the set of other messages required by this message."
  [{:keys [declarations] :as msg} msgs]
  (->> declarations
       (map :type)
       (filter #(#{:message} (:tag %)))
       (map #(select-keys % [:package :name]))
       distinct
       (into [])))

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

(defn serealize-declaration [d msgs]
  (match d
         {:tag :constant
          :name n
          :type {:tag :primitive
                 :name t}
          :value {:raw r}}
         (format "%s %s=%s" (name t) n r)
         {:tag :variable
          :name n
          :type {:tag :primitive
                 :name t}}
         (format "%s %s" (name t) n)
         {:tag :tuple
          :name n
          :arity a
          :type {:tag :primitive
                 :name t}}
         (format "%s[%s] %s" (name t) a n)
         {:tag :list
          :name n
          :type {:tag :primitive
                 :name t}}
         (format "%s[] %s" (name t) n)
         {:name n
          :type {:tag :message
                 :name t
                 :package p}}
         (format "%s %s"
                 (get-in msgs [{:package p :name t} :md5])
                 n)))

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

(defn annotate-md5s [msgs]
  (let [indexed-msgs (set/index msgs [:name :package])]
    (->>
     msgs
     (mapcat (fn [msg] (tree-seq #(not-empty (:dependencies %))
                                #(map (comp first indexed-msgs) (:dependencies %))
                                msg)))
     reverse
     distinct
     (reduce (fn [msgs msg]
               (let [amsg (annotate-md5 msg msgs)
                     asmg-name (select-keys amsg [:name :package])]
                 (assoc msgs
                   asmg-name amsg)))
             {})
     vals
     (into #{}))))

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
       (mapv #(assoc % k (f % msgs)))
       (into #{})))

(def primitive-frame {:bool    (g/enum :ubyte {false 0, true 1})
                      :int8    :byte
                      :byte    :byte
                      :uint8   :ubyte
                      :char    :ubyte
                      :int16   :int16-le
                      :uint16  :uint16-le
                      :int32   :int32-le
                      :uint32  :uint32-le
                      :int64   :int64-le
                      :uint64  :uint64-le
                      :float32 :float32-le
                      :float64 :float64-le
                      :string  (g/finite-frame :uint32-le (g/string :utf-8))
                      :time    (g/ordered-map :sec  :uint32-le
                                              :nsec :uint32-le)
                      :duration    (g/ordered-map :sec  :uint32-le
                                                  :nsec :uint32-le)})

(declare message-frame)
(defn declaration-frame [d msgs]
  (match d
         {:tag :constant
          :name n
          :type {:tag :primitive
                 :name t}
          :value {:read r}}
         [(keyword n)
          r]
         {:tag :variable
          :name n
          :type {:tag :primitive
                 :name t}}
         [(keyword n)
          (primitive-frame t)]
         {:tag :tuple
          :name n
          :arity a
          :type {:tag :primitive
                 :name t}}
         [(keyword name)
          (repeat a (primitive-frame t))]
         {:tag :list
          :name n
          :type {:tag :primitive
                 :name t}}
         [(keyword n)
          (g/finite-frame :uint32-le
                          (g/repeated (primitive-frame t)
                                      :prefix :none))]
         {:tag :variable
          :name n
          :type {:tag :message
                 :name t
                 :package p}}
         [(keyword n)
          (message-frame (-> msgs (get {:package p :name t}) first)
                         msgs)]
         {:tag :tuple
          :name n
          :arity a
          :type {:tag :message
                 :name t
                 :package p}}
         [(keyword n)
          (repeat a (message-frame (-> msgs (get {:package p :name t}) first)
                                   msgs))]
         {:tag :list
          :name n
          :type {:tag :message
                 :name t
                 :package p}}
         [(keyword n)
          (g/finite-frame :uint32-le
                          (g/repeated (message-frame (-> msgs (get {:package p :name t}) first)
                                                     msgs)
                                      :prefix :none))]))

(defn message-frame [msg msgs]
  (->> (mapv #(declaration-frame % msgs) (:declarations msg))
       (apply concat)
       (apply g/ordered-map)))

(defn frame [msg msgs]
  (g/finite-frame :uint32-le
                  (message-frame msg (set/index msgs [:name :package]))))

(defn load-msgs [root]
  (-> root
      clojure.java.io/file
      msgs-in-dir
      (annotate :declarations declarations)
      (annotate :dependencies dependencies)
      ensure-nocycles
      ensure-complete-dependencies
      annotate-md5s
      (annotate :cat cat)
      (annotate :frame frame)))
