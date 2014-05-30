(ns asimov.messages
  (:require [instaparse.core :as insta]
            [pandect.core :as hsh]
            [midje.sweet :as midje]))

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

(defn primitive-type [name]
  {:tag :primitive
   :name name})

(defn transform-parse [parse-res]
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
    :msg-type (fn [&[f s]] {:tag :message :package (when s f) :name (or s f)})}
   parse-res))

(defn parse-msg [msg]
  (-> msg
      msg-parser
      transform-parse))

(midje/tabular "Different message parts."
   (midje/fact (parse-msg ?msg) => ?res)
   ?msg                                            ?res
   "# Data"                                        []
   "int32 id"                                      [{:tag :variable :type {:tag :primitive :name "int32"}  :name "id"}]
   "int8 mode   # or-combination of values to set" [{:tag :variable :type {:tag :primitive :name "int8"}   :name "mode"}]
   "geometry_msgs/PoseStamped value_posestamped"   [{:tag :variable :type {:tag :message :package "geometry_msgs" :name "PoseStamped"}  :name "value_posestamped"}]
   "PoseStamped value_posestamped"                 [{:tag :variable :type {:tag :message :package nil :name "PoseStamped"}  :name "value_posestamped"}]
   "uint8[] value_data"                            [{:tag :list :type {:tag :primitive :name "uint8"}   :name "value_data"}]
   "uint8[4] value_data"                           [{:tag :tuple :type {:tag :primitive :name "uint8"}   :name "value_data"  :arity 4}]
   "int32 TYPE_STRING=0"                           [{:tag :constant :type {:tag :primitive :name "int32"}  :name "TYPE_STRING" :value 0}]
   "string EXAMPLE=\"#comments\" are ignored"      [{:tag :constant :type {:tag :primitive :name "string"} :name "EXAMPLE"     :value "\"#comments\" are ignored"}]
   "string EXAMPLE=  \t border wspace removed \t " [{:tag :constant :type {:tag :primitive :name "string"} :name "EXAMPLE"     :value "border wspace removed"}])

(midje/fact
  (parse-msg (slurp "https://github.com/ros/common_msgs/raw/indigo-devel/geometry_msgs/msg/PoseWithCovariance.msg"))
   =>
  [{:tag :variable :type {:tag :message :package nil :name "Pose"} :name "pose"}
   {:tag :tuple :type {:tag :primitive :name "float64"} :arity 36 :name "covariance"}])

(midje/fact
 (parse-msg (slurp "https://raw.githubusercontent.com/ros/common_msgs/indigo-devel/actionlib_msgs/msg/GoalStatus.msg"))
 =>
 [{:tag :variable :type {:tag :message :package nil :name "GoalID"} :name "goal_id"}
  {:tag :variable :type {:tag :primitive :name "uint8"} :name "status"}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "PENDING" :value 0}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "ACTIVE" :value 1}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "PREEMPTED" :value 2}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "SUCCEEDED" :value 3}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "ABORTED" :value 4}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "REJECTED" :value 5}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "PREEMPTING" :value 6}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "RECALLING" :value 7}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "RECALLED" :value 8}
  {:tag :constant :type {:tag :primitive :name "uint8"} :name "LOST" :value 9}
  {:tag :variable :type {:tag :primitive :name "string"} :name "text"}])

(defn canonical-msg [msg msgs]
  nil)

(defn unparse-msg [msg]
  nil)

(defn msg-checksum [msg msgs]
  (-> msg
      canonical-msg
      unparse-msg
      hsh/md5))

(defn- find-cycles [cur {:keys [seen root stack graph] :as state}]
  (first (filter identity (for [c (remove seen cur)]
                            (if (= c root)
                              (conj stack c)
                              (find-cycles (get graph c) (-> state
                                                             (update-in [:stack] conj c)
                                                             (update-in [:seen] conj c))))))))

(defn- cycles [graph]
  (filterv identity
           (for [[root deps] graph
                 :let [stack (find-cycles deps {:seen #{} :stack [root] :graph graph :root root})]]
             stack)))

(defn load-msgs [root]
  (let [msgs (->> root
                 file-seq
                 (filter #(.isFile %))
                 (filter #(re-matches #"" %)))]))
