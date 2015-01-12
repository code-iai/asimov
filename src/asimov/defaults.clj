(ns asimov.defaults
  (:require [clojure.java.shell :as sh]))


(def default-client-host
  (System/getenv "ROS_IP"))

(def default-master-host
  (some->
    "ROS_MASTER_URI"
    System/getenv
    (re-matches #"http://([^:]*):[0-9]*")
    second))

(def default-master-port
  (some->>
    "ROS_MASTER_URI"
    System/getenv
    (re-matches #"http://[^:]*:([0-9]*)")
    second
    Integer/parseInt))


(def default-hosts {})
