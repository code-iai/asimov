(ns asimov.messages
  (:require [instaparse.core :as insta]
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
     <type> = primitive-type | msg-type
     <primitive-type> = int-type | float-type | string-type | bool-type | time-type
     <bool-type> = 'bool'
     <int-type> = 'int8' | 'uint8' | 'int16' | 'uint16' | 'int32' | 'uint32' | 'int64' | 'uint64'
     <float-type> = 'float32' | 'float64'
     <string-type> = 'string'
     <time-type> =  'time' | 'duration'
     <field-name> = #'[a-zA-Z][a-zA-Z1-9_]*'
     msg-type = #'[a-zA-Z]' #'[0-9a-zA-Z_]*' ('/' #'[0-9a-zA-Z_]*')?
     float-lit = #'[-+]?[0-9]*.?[0-9]+([eE][-+]?[0-9]+)?'
     int-lit = #'[-+]?[0-9]*'
     string-lit = #'((?=\\S).*\\S)'?
     bool-lit = 'true' | 'false'
     comment = '#' #'.*'
     whitespace = #'[^\\S\\r\\n]'+
"))

(defn transform-parse [parse-res]
  (insta/transform
   {:S vector
    :simple-field
    (fn [type name]
      {:type type
       :name name})
    :unary-field
    (fn [type name]
      {:type type
       :name name})
    :tuple-field
    (fn [type arity name]
      {:type type
       :name name
       :arity (Integer/parseInt arity)})
    :list-field
    (fn [type name]
      {:type type
       :name name
       :arity :list})
    :constant
    (fn [type name value]
      {:type type
       :name name
       :value value})
    :double-lit #(Double/parseDouble %)
    :int-lit #(Integer/parseInt %)
    :bool-lit (fn [bl] (case bl "true" true "false" false))
    :string-lit str
    :msg-type str}
   parse-res))

(defn parse-message [msg]
  (-> msg
      msg-parser
      transform-parse))

(midje/tabular "Different message parts."
   (midje/fact (parse-message ?msg) => ?res)
   ?msg                                            ?res
   "# Data"                                        []
   "int32 id"                                      [{:type "int32"  :name "id"}]
   "byte mode   # or-combination of values to set" [{:type "byte"   :name "mode"}]
   "geometry_msgs/PoseStamped value_posestamped"   [{:type "geometry_msgs/PoseStamped"  :name "value_posestamped"}]
   "char[] value_data"                             [{:type "char"   :name "value_data"  :arity :list}]
   "char[4] value_data"                            [{:type "char"   :name "value_data"  :arity 4}]
   "int32 TYPE_STRING=0"                           [{:type "int32"  :name "TYPE_STRING" :value 0}]
   "string EXAMPLE=\"#comments\" are ignored"      [{:type "string" :name "EXAMPLE"     :value "\"#comments\" are ignored"}]
   "string EXAMPLE=  \t border wspace removed \t " [{:type "string" :name "EXAMPLE"     :value "border wspace removed"}])

(midje/fact
  (parse-message (slurp "https://github.com/ros/common_msgs/raw/indigo-devel/geometry_msgs/msg/PoseWithCovariance.msg"))
   =>
  [{:type "Pose" :name "pose"}
   {:type "float64" :arity 36 :name "covariance"}])

(midje/fact
 (parse-message (slurp "https://raw.githubusercontent.com/ros/common_msgs/indigo-devel/actionlib_msgs/msg/GoalStatus.msg"))
 =>
 [{:type "GoalID" :name "goal_id"}
  {:type "uint8" :name "status"}
  {:type "uint8" :name "PENDING" :value 0}
  {:type "uint8" :name "ACTIVE" :value 1}
  {:type "uint8" :name "PREEMPTED" :value 2}
  {:type "uint8" :name "SUCCEEDED" :value 3}
  {:type "uint8" :name "ABORTED" :value 4}
  {:type "uint8" :name "REJECTED" :value 5}
  {:type "uint8" :name "PREEMPTING" :value 6}
  {:type "uint8" :name "RECALLING" :value 7}
  {:type "uint8" :name "RECALLED" :value 8}
  {:type "uint8" :name "LOST" :value 9}
  {:type "string" :name "text"}])
