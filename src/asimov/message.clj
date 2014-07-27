(ns asimov.message
  (:require [clojure.set :as set]
            [clojure.core.match :refer [match]]
            [clojure.algo.generic.functor :refer [fmap]]
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
"Instaparse leaf transformer for primitive type token.

Expects:
 name:string the name of the type.

Returns a map with the keyworized version of the type (:name) as well as a tag
that marks it as a primitive (:tag)."
  [name]
  {:tag :primitive
   :name (keyword name)})

(defn literal
  "Generates an instaparse leaf transformer for literal value tokens.

Expects:
 f:fn A parsing function to be applied to the literals.

Returns a function that takes a literal and returns a map
containing it in its raw form (:raw) as well as the value
created by parsing it with the provided function (:read)."
  [f]
  (fn [raw]
    {:raw raw
     :read (f raw)}))

(defn transform-parse
"Instaparse tree transformer to be used on message declaration parsing results.

Expects:
 parse-tree: instaparse-tree The parse three to be transformed.

Returns a workable list of declarations."
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
  "Compiler step that makes implicit package references in a
ros message definition explicit.
For \"Header\" messages the package is \"std_msgs\".
For all other message references without an explicit package
the package the message was declared in will be choosen.


Expects:
 package:string The name of the package the message was declared in.
 declarations:vector The declarations of the message definition.

Returns the given declarations where every message reference is explicit."
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

(defn check-errors
"Compiler step that checks for parsing errors.

Expects:
 msg:string The raw unparsed message used for error reporting.
 p:instaparse-parse The parsed version of the message,
   potentially containing errors.

Returns the parsed message when parsing was successful.

Thows an exception when the message definitions parse contains any errors."
  [msg p]
  (if (insta/failure? p)
    (do (t/error "Could not parse message!\n" (insta/get-failure p))
        (ss/throw+ {:msg msg :error (insta/get-failure p)} "Error while parsing msg!"))
    p))

(defn declarations
"A compiler step for turning the raw message definition into separate declarations.
To be used with `annotate`.

Expects
 msg:map The message definition map consisting of a package name (:package)
         and the raw message declaration (:raw).
 msgs:map The entire message map to be compiled.

Returns a vector of the message definitions declarations."
  [{:keys [package raw] :as msg} msgs]
  (->> raw
       msg-parser
       (check-errors msg)
       transform-parse
       (make-packages-explicit package)))

(defn dependencies
"A compiler step for extracting a messages dependencies from its declarations.
To be used with `annotate`.

Expects
 msg:map The message definition map consisting of a collection of declarations (:declarations).
 msgs:map The entire message map to be compiled.

Returns a vector of the messages depencencies in the order they appeared."
  [msg msgs]
  (->> msg
       :declarations
       (map :type)
       (filter #(#{:message} (:tag %)))
       (map #(select-keys % [:package :name]))
       distinct
       (into [])))

(defn parse-path
"Extracts the package name and message name from a given ros message definition path."
  [path]
  (when-let [[_ package message]
             (re-matches #".*?([a-zA-Z][0-9a-zA-Z_]*)/msg/([0-9a-zA-Z_]+)\.msg"
                         path)]
    {:package package
     :name message}))

(defn msgs-in-dir
  "Loads all messages in the given directory as raw strings and accociates them
with their name and package."
  [root]
  (as-> root x
        (file-seq x)
        (filter #(.isFile %) x)
        (map (fn [f] (when-let [id (parse-path (.getCanonicalPath f))]
                      (assoc id :raw (slurp f)))) x)
        (remove nil? x)
        (set/index x [:name :package])
        (fmap first x)
        (into {} x)))

(defn dep-graph
  "Calculates the depencency graphes for the given message definition map."
  [msgs]
  (fmap #(into #{} (:dependencies %))
        msgs))

(defn ensure-complete-dependencies
  "Makes sure that the provided message definition map is complete
in regards to each message definitions dependencies.

Will throw an exception if any message definitions depencencies can't be satisfied.
Behaves like the identity otherwise."
  [msgs]
  (let [dg (dep-graph msgs)
        found-msgs (into #{} (keys dg))]
    (if-let [missing-deps (->> dg
                               (fmap #(clojure.set/difference
                                       %
                                       found-msgs))
                               (filter (fn [[msg deps]]
                                         (not-empty deps)))
                               (into {})
                               not-empty)]
      (ss/throw+ {:tag ::missing-deps :missing missing-deps}
                 "Missing dependencies!")
      msgs)))

(defn ensure-nocycles
  "Makes sure that the provided message definition map does not
contain cyclic dependencies.

Will throw an exception if cycles are found in the dependency tree.
Behaves like the identity otherwise."
  [msgs]
  (if-let [c (not-empty (util/cycles (dep-graph msgs)))]
    (ss/throw+ {:tag ::circular-msg :cycles c}
               "Can't load circular message definitions!")
    msgs))

(defn serealize-declaration
  "Takes a single message declaration and returns it as a string.
Formatted like a single line generated by `gendeps`
for usage in md5 generation."
  [d msgs]
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

(defn md5-text
"Takes a message definition and calculates the text used for md5 generation."
  [msg msgs]
  (let [constant? #(= :constant (:tag %))
        decs (:declarations msg)
        reordered (concat (filter constant? decs)
                          (remove constant? decs))]
    (->> reordered
         (map #(serealize-declaration % msgs))
         (interpose "\n")
         (apply str))))

(defn annotate-md5
  "Takes a message definition and calculates its md5.

Returns the message definition with the md5 sum annotated to it."
  [msg msgs]
  (let [text (md5-text msg msgs)
        md5 (hsh/md5 text)]
    (assoc msg :md5 md5)))

(defn annotate-md5s
  "Annotates the md5 sum to all messages in the provided message map."
  [msgs]
  (->>
   msgs
   vals
   (mapcat (fn [msg] (tree-seq #(not-empty (:dependencies %))
                              #(map msgs (:dependencies %))
                              msg)))
   reverse
   distinct
   (reduce (fn [msgs msg]
             (let [amsg (annotate-md5 msg msgs)
                   asmg-name (select-keys amsg [:name :package])]
               (assoc msgs
                 asmg-name amsg)))
           {})))

(defn cat
"Takes a message definition and calculates its `gendeps --cat` text."
  [msg msgs]
  (let [separator (str (apply str (repeat 80 "=")) "\n")
        dep-text (->> msg
                      (tree-seq #(not-empty (:dependencies %))
                                #(map msgs (:dependencies %)))
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

(defn annotate
"Takes a message and annotates it with the result of the passed function under the provided key."
  [msgs k f]
  (fmap #(assoc % k (f % msgs)) msgs))

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
(defn declaration-frame
"Takes a single declaration of a message definition and returns a gloss frame to parse it."
  [d msgs]
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
          (message-frame (msgs {:package p :name t})
                         msgs)]
         {:tag :tuple
          :name n
          :arity a
          :type {:tag :message
                 :name t
                 :package p}}
         [(keyword n)
          (repeat a (message-frame (msgs {:package p :name t})
                                   msgs))]
         {:tag :list
          :name n
          :type {:tag :message
                 :name t
                 :package p}}
         [(keyword n)
          (g/finite-frame :uint32-le
                          (g/repeated (message-frame (msgs {:package p :name t})
                                                     msgs)
                                      :prefix :none))]))

(defn message-frame
  "Takes a message definition and returns a gloss frame that parses messages with this encoding.
Can be used as subframes in other messages."
  [msg msgs]
  (->> (mapv #(declaration-frame % msgs) (:declarations msg))
       (apply concat)
       (apply g/ordered-map)))

(defn frame
  "Takes a message definition and returns a gloss frame that parses messages with this encoding.
To be used as a toplevel frame."
  [msg msgs]
  (g/finite-frame :uint32-le
                  (message-frame msg msgs)))

(defn annotate-all
  "Takes a message map and annotates all properties needed and generated by compilation.
Throws an exception if the a message or the dependency graph is malformed somehow."
  [msgs]
  (-> msgs
      (annotate :declarations declarations)
      (annotate :dependencies dependencies)
      ensure-nocycles
      ensure-complete-dependencies
      annotate-md5s
      (annotate :cat cat)
      (annotate :frame frame)))

(defn parse-id
  "Takes a message definition id string of the form `package_name/message_name`
and returns a map containing its components.

Inverse of `serialize-id`."
  [id]
  (let [[_ package name] (re-matches #"([^/]*)/([^/]*)" id)]
       {:package package :name name}))

(defn serialize-id
"Takes a message definition id map with the keys [:package :name]
and returns a it as an id string.

Inverse of `parse-id`."
  [id]
  (let [{:keys [package name]} id]
       (str package "/" name)))
