(ns asimov.messages
  (:require [instaparse.core :as insta]))

(def msg-parser
  (insta/parser
    "S = FIELD
     FIELD = TYPE NAME
     TYPE = 'bool' |
     NAME = 'b'+")
