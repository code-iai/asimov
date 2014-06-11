(ns asimov.message-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [expose-testables]]
            [asimov.util :refer [throws+]]
            [asimov.messages :refer :all]))

(expose-testables asimov.messages)

(fact
 "Implicit packages are correctly expanded
  to explicit ones."
 (make-packages-explicit
  ...pkg...
  [{:tag :variable
   :type {:tag :message
          :package "geometry_msgs"
          :name "PoseStamped"}
   :name "value_posestamped"}
   {:tag :variable
    :type {:tag :message
           :package nil
           :name "PoseStamped"}
    :name "value_posestamped"}])
 =>
 [{:tag :variable
   :type {:tag :message
          :package "geometry_msgs"
          :name "PoseStamped"}
   :name "value_posestamped"}
  {:tag :variable
   :type {:tag :message
          :package ...pkg...
          :name "PoseStamped"}
   :name "value_posestamped"}])

(tabular
 "Different message parts."
 (fact
  (annotate-declarations
   [{:name ...name...
    :package ...pkg...
    :raw ?raw}])
  =>
  (contains
   [(contains
   {:declarations ?res})]))
 ?raw ?res
 "# Data"
 []
 "int32 id"
 [{:tag :variable
   :type {:tag :primitive
          :name :int32}
   :name "id"}]
 "int8 mode   # or-combination of values to set"
 [{:tag :variable
   :type {:tag :primitive
          :name :int8}
   :name "mode"}]
 "geometry_msgs/PoseStamped value_posestamped"
 [{:tag :variable
   :type {:tag :message
          :package "geometry_msgs"
          :name "PoseStamped"}
   :name "value_posestamped"}]
 "PoseStamped value_posestamped"
 [{:tag :variable
   :type {:tag :message
          :package ...pkg...
          :name "PoseStamped"}
   :name "value_posestamped"}]
 "uint8[] value_data"
 [{:tag :list
   :type {:tag :primitive
          :name :uint8}
   :name "value_data"}]
 "uint8[4] value_data"
 [{:tag :tuple
   :type {:tag :primitive
          :name :uint8}
   :name "value_data"
   :arity 4}]
 "int32 TYPE_STRING=0"
 [{:tag :constant
   :type {:tag :primitive
          :name :int32}
   :name "TYPE_STRING"
   :value {:raw "0"
           :read 0}}]
 "string EXAMPLE=\"#comments\" are ignored"
 [{:tag :constant
   :type {:tag :primitive
          :name :string}
   :name "EXAMPLE"
   :value {:raw "\"#comments\" are ignored"
           :read "\"#comments\" are ignored"}}]
 "string EXAMPLE=  \t border wspace removed \t "
 [{:tag :constant
   :type {:tag :primitive
          :name :string}
   :name "EXAMPLE"
   :value {:raw "border wspace removed"
           :read "border wspace removed"}}])

(fact
 (annotate-declarations
  [{:package "common_msgs"
   :name "PoseWithCovariance"
   :raw
"# This represents a pose in free space with uncertainty.

Pose pose

# Row-major representation of the 6x6 covariance matrix
# The orientation parameters use a fixed-axis representation.
# In order, the parameters are:
# (x, y, z, rotation about X axis, rotation about Y axis, rotation about Z axis)
float64[36] covariance
"}])
 =>
 (contains
  [(contains
  {:declarations
   [{:tag :variable
     :type {:tag :message
            :package "common_msgs"
            :name "Pose"}
     :name "pose"}
    {:tag :tuple
     :type {:tag :primitive
            :name :float64}
     :arity 36
     :name "covariance"}]})]))

(fact
 (annotate-dependencies
  [{:declarations
   [{:tag :variable
     :type {:tag :primitive
            :name :int8}
     :name "mode"}
    {:tag :variable
     :type {:tag :message
            :package "geometry_msgs"
            :name "PoseStamped"}
     :name "value_posestamped"}
    {:tag :variable
     :type {:tag :message
            :package nil
            :name "PoseStamped"}
     :name "value_posestamped"}
    {:tag :list
     :type {:tag :primitive
            :name :uint8}
     :name "value_data"}
    {:tag :tuple
     :type {:tag :primitive
            :name :uint8}
     :name "value_data"
     :arity 4}
    {:tag :constant
     :type {:tag :primitive
            :name :int32}
     :name "TYPE_STRING"
     :value {:raw "0"
             :read 0}}]}])
 =>
 (contains
  [(contains
  {:dependencies #{{:package "geometry_msgs"
                    :name "PoseStamped"}
                   {:package nil
                    :name "PoseStamped"}}})]))

(tabular
 (fact (parse-path ?path) => ?res)
 ?path ?res
 "./common_msgs/actionlib_msgs/msg/GoalID.msg"
 {:package "actionlib_msgs"
  :name "GoalID"}
 "./common_msgs/diagnostic_msgs/msg/KeyValue.msg"
 {:package "diagnostic_msgs"
  :name "KeyValue"}
 "./common_msgs/geometry_msgs/msg/Point.msg"
 {:package "geometry_msgs"
  :name "Point"}
 "./common_msgs/nav_msgs/msg/Path.msg"
 {:package "nav_msgs"
  :name "Path"}
 "./common_msgs/sensor_msgs/msg/CameraInfo.msg"
 {:package "sensor_msgs"
  :name "CameraInfo"}
 "./common_msgs/shape_msgs/msg/Mesh.msg"
 {:package "shape_msgs"
  :name "Mesh"}
 "./common_msgs/trajectory_msgs/msg/JointTrajectory.msg"
 {:package "trajectory_msgs"
  :name "JointTrajectory"}
 "./common_msgs/visualization_msgs/msg/ImageMarker.msg"
 {:package "visualization_msgs"
  :name "ImageMarker"})

(fact
 (-> "resources/common_msgs"
     clojure.java.io/file
     msgs-in-dir)
 =>
 (contains
  [{:name "PoseWithCovariance"
    :package "geometry_msgs"
    :raw
"# This represents a pose in free space with uncertainty.

Pose pose

# Row-major representation of the 6x6 covariance matrix
# The orientation parameters use a fixed-axis representation.
# In order, the parameters are:
# (x, y, z, rotation about X axis, rotation about Y axis, rotation about Z axis)
float64[36] covariance
"}
   (contains
    {:name "GoalStatus"
     :package "actionlib_msgs"})]
  :in-any-order :gaps-ok))

(fact
 (dep-graph [{:package "round"
              :name "tire"
              :dependencies #{}}
             {:package "vehicle"
              :name "car"
              :dependencies #{{:package "round"
                               :name "tire"}}}])
 =>
 {{:package "round"
   :name "tire"}
  #{}
  {:package "vehicle"
   :name "car"}
  #{{:package "round"
     :name "tire"}}})

(fact
 (dep-graph [{:package "round"
              :name "tire"
              :dependencies #{}}
             {:package "vehicle"
              :name "car"
              :dependencies #{{:package "round"
                               :name "tire"}
                              {:package "round"
                               :name "steeringwheel"}}}])
 =>
 {{:package "round"
   :name "tire"}
  #{}
  {:package "vehicle"
   :name "car"}
  #{{:package "round"
     :name "tire"}
    {:package "round"
     :name "steeringwheel"}}})

(fact "Ensuring dependencies is the identity if all dependencies exist."
            (ensure-complete-dependencies
             [{:package "round"
               :name "tire"
               :dependencies #{}}
              {:package "vehicle"
               :name "car"
               :dependencies
               #{{:package "round"
                  :name
                  "tire"}}}])
            =>
             [{:package "round"
               :name "tire"
               :dependencies #{}}
              {:package "vehicle"
               :name "car"
               :dependencies #{{:package "round"
                                :name "tire"}}}])

(fact "Ensuring dependencies will throw an exception with incomplete dependencies."
            (ensure-complete-dependencies
             [{:package "round"
               :name "tire"
               :dependencies #{}}
              {:package "vehicle"
               :name "car"
               :dependencies #{{:package "round"
                                :name "tire"}
                               {:package "round"
                                :name "steeringwheel"}}}])
            =>
            (throws+ "Missing dependencies!"
                          {:tag :asimov.messages/missing-deps
                           :missing {{:package "vehicle"
                                      :name "car"}
                                     #{{:package "round"
                                        :name "steeringwheel"}}}}))


(fact
 "ensure-nocycles is the identity if all
  there are no cycles in the dependency graph"
 (ensure-nocycles
  [{:package "round"
    :name "tire" :dependencies #{}}
   {:package "vehicle"
    :name "car"
    :dependencies #{{:package
                     "round"
                     :name "tire"}}}])
 =>
 [{:package "round"
   :name "tire"
   :dependencies #{}}
  {:package "vehicle"
   :name "car"
   :dependencies #{{:package "round"
                    :name "tire"}}}])

(fact
 "ensure-dependencies will throw an exception
  when there are cycles in the dependency graphs"
 (ensure-nocycles
  [{:package "round"
    :name "tire"
    :dependencies
    #{{:package "vehicle"
       :name "car"}}}
   {:package "vehicle"
    :name "car"
    :dependencies #{{:package "round"
                     :name "tire"}}}])
 =>
 (throws+
  "Can't load circular message definitions!"
  {:tag :asimov.messages/circular-msg
   :cycles #{[{:package "round",
               :name "tire"}
              {:package "vehicle",
               :name "car"}
              {:package "round",
               :name "tire"}]
             [{:package "vehicle",
               :name "car"}
              {:package "round",
               :name "tire"}
              {:package "vehicle",
               :name "car"}]}}))

(fact
 (-> "resources/common_msgs"
     clojure.java.io/file
     load-msgs)
 =>
 (contains
  [(contains
    {:name "PoseWithCovariance"
     :package "geometry_msgs"
     :raw
"# This represents a pose in free space with uncertainty.

Pose pose

# Row-major representation of the 6x6 covariance matrix
# The orientation parameters use a fixed-axis representation.
# In order, the parameters are:
# (x, y, z, rotation about X axis, rotation about Y axis, rotation about Z axis)
float64[36] covariance
"
     :md5 "c23e848cf1b7533a8d7c259073a97e6f"
     :dependencies #{{:package "geometry_msgs":name "Pose"}}
     :declarations
     [{:tag :variable
       :type {:tag :message
                             :package "geometry_msgs"
                             :name "Pose"} :name "pose"}
      {:tag :tuple
       :type {:tag :primitive
              :name :float64}
       :arity 36
       :name "covariance"}]})
   (contains
    {:name "GoalStatus"
     :package "actionlib_msgs"
     :md5 "d388f9b87b3c471f784434d671988d4a"
     :dependencies #{{:package "actionlib_msgs"
                      :name "GoalID"}}
     :declarations
     [{:tag :variable
       :type {:tag :message
              :package "actionlib_msgs"
              :name "GoalID"}
       :name "goal_id"}
      {:tag :variable
       :type {:tag :primitive
              :name :uint8}
       :name "status"}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "PENDING"
       :value {:raw "0"
               :read 0}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "ACTIVE"
       :value {:raw "1"
               :read 1}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "PREEMPTED"
       :value {:raw "2"
               :read 2}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "SUCCEEDED"
       :value {:raw "3"
               :read 3}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "ABORTED"
       :value {:raw "4"
               :read 4}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "REJECTED"
       :value {:raw "5"
               :read 5}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "PREEMPTING"
       :value {:raw "6"
               :read 6}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "RECALLING"
       :value {:raw "7"
               :read 7}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "RECALLED"
       :value {:raw "8"
               :read 8}}
      {:tag :constant
       :type {:tag :primitive
              :name :uint8}
       :name "LOST"
       :value {:raw "9"
               :read 9}}
      {:tag :variable
       :type {:tag :primitive
              :name :string}
       :name "text"}]})]
  :in-any-order :gaps-ok))
