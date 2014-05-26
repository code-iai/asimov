(ns asimov.messages
  (:require [instaparse.core :as insta]))


(def simple-parser (insta/parser "S = 'a' | 'b'"))

(def msg-parser
  (insta/parser
   " S = (field <#'[\\n ]'*>)*
     field = type <' '>* name
     name = #'[a-zA-Z_-]'*
     type = primitive-type | array-type | msg-type
     array-type = type <'[]'>
     msg-type = (name <'/'>)+ name
     primitive-type = 'bool' | 'int8' | 'uint8' | 'int16' | 'uint16' | 'int32' | 'uint32' | 'int64' | 'uint64' | 'float32' | 'float64' | 'string' | 'time' | 'duration'
"))


(defn transform-parse [parse-res]
  (insta/transform
   {:field (fn [type name]
             {:type type
              :name name})
    :primitive-type identity
    
    :S (fn [& args] (vec args))
    :name #(symbol (apply str %&))}
   parse-res))