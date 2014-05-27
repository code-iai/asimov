(ns asimov.messages
  (:require [instaparse.core :as insta]))


(def simple-parser (insta/parser "S = 'a' | 'b'"))

(def double-regex #"[-+]?[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?")

(def msg-parser
  (insta/parser
   " S = (field <#'[\\n ]'*>)*
     <field> = constant-field | variable-field
     constant-field = numeric-constant-field | string-constant-field | bool-constant-field
     variable-field = type <' '>+ name 
     name = #'[a-zA-Z_-]'+
     <type> = primitive-type | array-type | msg-type
     array-type = type <'[]'>
     msg-type = name | name  '/'  name
     <primitive-type> = numeric-type | string-type | bool-type | time-type
     <bool-type> = 'bool'
     <numeric-type> = 'int8' | 'uint8' | 'int16' | 'uint16' | 'int32' | 'uint32' | 'int64' | 'uint64' | 'float32' | 'float64'
     <string-type> = 'string'
     <time-type> =  'time' | 'duration'
     <numeric-constant-field> = numeric-type <' '>+ name <'='> numeric-lit
     <numeric-lit> = double-lit | int-lit 
     <bool-constant-field> = bool-type <' '> name <'='> bool-lit
     <string-constant-field> = string-type <' '> name <'='> string-lit
     double-lit = #'[-+]?[0-9]*.?[0-9]+([eE][-+]?[0-9]+)?'
     int-lit = #'[-+]?[0-9]*'
     <string-lit> = #'.*'
     bool-lit = 'true' | 'false'
"))


(defn transform-parse [parse-res]
  (insta/transform
   {:variable-field
    (fn [type name]
      {:tag ::variable
       :type type
       :name name})
    :constant-field
    (fn [type name value]
      {:tag ::constant
       :type type
       :name name
       :value value})
    :array-type (fn [type]
                  {:array type})
    :double-lit #(Double/parseDouble %)
    :int-list #(Integer/parseInt %)
    :bool-lit (fn [bl] (case bl "true" true "false" false))
    :S (fn [& args] (vec args))
    :msg-type #(apply str %&)
    :name #(apply str %&)}
   parse-res))


(defn parse-message [msg]
  (-> msg
      msg-parser
      transform-parse))

;;Status: variable and constant fields are recognized
;;TODO: - more type checking
;;      - what about time and duration literals?
;;      - check sanity of parse-result format
;;      - add midje tests

;;(parse-message "bool a=true\n dasgeht/auch[][] b")
;;=> [{:tag :asimov.messages/constant, :type "bool",
;;     :name "a", :value true}
;;    {:tag :asimov.messages/variable,
;;     :type {:array {:array "dasgeht/auch"}}, :name "b"}]

;;(parse-message "string stringliteralsarescary=everything until newline is recignized#\"!\"'\"'\"'''\"'\"'\"'''######")
;;[{:tag :asimov.messages/constant, :type "string",
;; :name "stringliteralsarescary",
;; :value "everything until newline is recignized#\"!\"'\"'\"'''\"'\"'\"'''######"}]