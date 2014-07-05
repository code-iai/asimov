(ns asimov.message-test
  (:require [clojure.test :refer :all]
            [clojure.set :refer :all]
            [gui.diff :refer :all]
            [asimov.message :refer :all]
            [asimov.util :refer :all]))

(deftest pkg-expansion
  (testing "Implicit packages are correctly expanded
    to explicit ones."
    (are [exp inp]
      (= [exp] (make-packages-explicit 'pkg [inp]))
      {:tag :variable
       :type {:tag :message
              :package "geometry_msgs"
              :name "PoseStamped"}
       :name "value_posestamped"}
      {:tag :variable
       :type {:tag :message
              :package "geometry_msgs"
              :name "PoseStamped"}
       :name "value_posestamped"}
      {:tag :variable
       :type {:tag :message
              :package 'pkg
              :name "PoseStamped"}
       :name "value_posestamped"}
      {:tag :variable
       :type {:tag :message
              :package nil
              :name "PoseStamped"}
       :name "value_posestamped"}
      {:tag :variable
       :type {:tag :message
              :package "std_msgs"
              :name "Header"}
       :name "header"}
      {:tag :variable
       :type {:tag :message
              :package nil
              :name "Header"}
       :name "header"})))

(deftest declarations-test
  (testing "Simple single line declarations."
    (are [inp exp] (= exp
                      (declarations
                       {:name 'name
                        :package 'pkg
                        :raw inp}
                       'msgs))
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
                  :package 'pkg
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
                   :read "border wspace removed"}}]))
  (testing "Multiline declarations."
    (are [inp exp] (= exp
                      (declarations
                       {:package "common_msgs"
                        :name "PoseWithCovariance"
                        :raw inp}
                       'msgs))
         "# This represents a pose in free space with uncertainty.

Pose pose

# Row-major representation of the 6x6 covariance matrix
# The orientation parameters use a fixed-axis representation.
# In order, the parameters are:
# (x, y, z, rotation about X axis, rotation about Y axis, rotation about Z axis)
float64[36] covariance
"
         [{:tag :variable
           :type {:tag :message
                  :package "common_msgs"
                  :name "Pose"}
           :name "pose"}
          {:tag :tuple
           :type {:tag :primitive
                  :name :float64}
           :arity 36
           :name "covariance"}])))

(deftest dependencies-test
  (are [inp] (= [{:package "geometry_msgs"
                  :name "PoseStamped"}
                 {:name "GoalStatus"
                  :package "actionlib_msgs"}]
                (dependencies
                 {:declarations inp}
                 'msgs))
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
                :package "geometry_msgs"
                :name "PoseStamped"}
         :name "value_posestamped_two"}
        {:tag :list
         :type {:tag :primitive
                :name :uint8}
         :name "value_data"}
        {:tag :tuple
         :type {:tag :primitive
                :name :uint8}
         :name "value_data"
         :arity 4}
        {:tag :variable
         :type {:tag :message
                :package "actionlib_msgs"
                :name "GoalStatus"}
         :name "value_goalstatus"}
        {:tag :constant
         :type {:tag :primitive
                :name :int32}
         :name "TYPE_STRING"
         :value {:raw "0"
                 :read 0}}]))

(deftest path-parsing
  (testing "A path should be split correctly into package and message names."
    (are [path exp] (= exp (parse-path path))
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
          :name "ImageMarker"})))

(deftest integration-load-test
  (testing "Raw message loading from disk should work."
    (are [msgs exp] (some #{exp} msgs)
         (-> "resources/msgs"
             clojure.java.io/file
             msgs-in-dir)
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
"})))

(deftest dependency-graph
  (testing "Is a dependency graph correctly extracted from a given set of messages?"
    (is (= {{:package "round"
             :name "tire"}
            #{}
            {:package "vehicle"
             :name "car"},
            #{{:package "round"
               :name "tire"}
              {:package "round"
               :name "steeringwheel"}}}
           (dep-graph #{{:package "round"
                         :name "tire"
                         :dependencies []}
                        {:package "vehicle"
                         :name "car"
                         :dependencies [{:package "round"
                                         :name "tire"}
                                        {:package "round"
                                         :name "steeringwheel"}]}})))))

(deftest ensuring-dependencies
  (testing "Ensuring dependencies is the identity if all dependencies exist."
    (are [inp] (= inp (ensure-complete-dependencies inp))
         #{{:package "round"
            :name "tire"
            :dependencies []}
           {:package "vehicle"
            :name "car"
            :dependencies [{:package "round"
                            :name "tire"}]}}))

  (testing "Ensuring dependencies will throw an exception with incomplete dependencies."
    (is (thrown-with-data? #"Missing dependencies!"
                           #{{:tag :asimov.message/missing-deps
                              :missing {{:package "vehicle"
                                         :name "car"}
                                        #{{:package "round"
                                           :name "steeringwheel"}}}}}
                           (ensure-complete-dependencies
                            #{{:package "round"
                               :name "tire"
                               :dependencies #{}}
                              {:package "vehicle"
                               :name "car"
                               :dependencies [{:package "round"
                                               :name "tire"}
                                              {:package "round"
                                               :name "steeringwheel"}]}})))))

(deftest cycle-detection
  (testing  "ensure-nocycles is the identity if there are no cycles in the dependency graph"
    (are [exp] (= exp (ensure-nocycles exp))
         #{{:package "round"
            :name "tire"
            :dependencies []}
           {:package "vehicle"
            :name "car"
            :dependencies [{:package "round"
                            :name "tire"}]}})
    (testing "ensure-dependencies will throw an exception when there are cycles in the dependency graphs"
      (is (thrown-with-data? #"Can't load circular message definitions!"
                             #{{:tag :asimov.message/circular-msg
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
                                            :name "car"}]}}}
                             (ensure-nocycles
                              #{{:package "round"
                                 :name "tire"
                                 :dependencies
                                 [{:package "vehicle"
                                   :name "car"}]}
                                {:package "vehicle"
                                 :name "car"
                                 :dependencies [{:package "round"
                                                 :name "tire"}]}}))))))

(deftest integration-full-test
  (let [msgs (-> "resources/msgs"
                 clojure.java.io/file
                 msgs-in-dir
                 annotate-all)]
    (are [sel exp] (= exp (select-keys
                           (first (get (index msgs
                                              [:name :package])
                                       sel))
                           (keys exp)))
         {:name "PoseWithCovariance"
          :package "geometry_msgs"}
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
          :cat
"# This represents a pose in free space with uncertainty.

Pose pose

# Row-major representation of the 6x6 covariance matrix
# The orientation parameters use a fixed-axis representation.
# In order, the parameters are:
# (x, y, z, rotation about X axis, rotation about Y axis, rotation about Z axis)
float64[36] covariance

================================================================================
MSG: geometry_msgs/Pose
# A representation of pose in free space, composed of postion and orientation. 
Point position
Quaternion orientation

================================================================================
MSG: geometry_msgs/Point
# This contains the position of a point in free space
float64 x
float64 y
float64 z

================================================================================
MSG: geometry_msgs/Quaternion
# This represents an orientation in free space in quaternion form.

float64 x
float64 y
float64 z
float64 w

"
          :dependencies [{:package "geometry_msgs":name "Pose"}]
          :declarations
          [{:tag :variable
            :type {:tag :message
                   :package "geometry_msgs"
                   :name "Pose"} :name "pose"}
           {:tag :tuple
            :type {:tag :primitive
                   :name :float64}
            :arity 36
            :name "covariance"}]}
         {:name "GoalStatus"
          :package "actionlib_msgs"}
         {:name "GoalStatus"
          :package "actionlib_msgs"
          :raw
"GoalID goal_id
uint8 status
uint8 PENDING         = 0   # The goal has yet to be processed by the action server
uint8 ACTIVE          = 1   # The goal is currently being processed by the action server
uint8 PREEMPTED       = 2   # The goal received a cancel request after it started executing
                            #   and has since completed its execution (Terminal State)
uint8 SUCCEEDED       = 3   # The goal was achieved successfully by the action server (Terminal State)
uint8 ABORTED         = 4   # The goal was aborted during execution by the action server due
                            #    to some failure (Terminal State)
uint8 REJECTED        = 5   # The goal was rejected by the action server without being processed,
                            #    because the goal was unattainable or invalid (Terminal State)
uint8 PREEMPTING      = 6   # The goal received a cancel request after it started executing
                            #    and has not yet completed execution
uint8 RECALLING       = 7   # The goal received a cancel request before it started executing,
                            #    but the action server has not yet confirmed that the goal is canceled
uint8 RECALLED        = 8   # The goal received a cancel request before it started executing
                            #    and was successfully cancelled (Terminal State)
uint8 LOST            = 9   # An action client can determine that a goal is LOST. This should not be
                            #    sent over the wire by an action server

#Allow for the user to associate a string with GoalStatus for debugging
string text

"
          :md5 "d388f9b87b3c471f784434d671988d4a"
          :cat
"GoalID goal_id
uint8 status
uint8 PENDING         = 0   # The goal has yet to be processed by the action server
uint8 ACTIVE          = 1   # The goal is currently being processed by the action server
uint8 PREEMPTED       = 2   # The goal received a cancel request after it started executing
                            #   and has since completed its execution (Terminal State)
uint8 SUCCEEDED       = 3   # The goal was achieved successfully by the action server (Terminal State)
uint8 ABORTED         = 4   # The goal was aborted during execution by the action server due
                            #    to some failure (Terminal State)
uint8 REJECTED        = 5   # The goal was rejected by the action server without being processed,
                            #    because the goal was unattainable or invalid (Terminal State)
uint8 PREEMPTING      = 6   # The goal received a cancel request after it started executing
                            #    and has not yet completed execution
uint8 RECALLING       = 7   # The goal received a cancel request before it started executing,
                            #    but the action server has not yet confirmed that the goal is canceled
uint8 RECALLED        = 8   # The goal received a cancel request before it started executing
                            #    and was successfully cancelled (Terminal State)
uint8 LOST            = 9   # An action client can determine that a goal is LOST. This should not be
                            #    sent over the wire by an action server

#Allow for the user to associate a string with GoalStatus for debugging
string text


================================================================================
MSG: actionlib_msgs/GoalID
# The stamp should store the time at which this goal was requested.
# It is used by an action server when it tries to preempt all
# goals that were requested before a certain time
time stamp

# The id provides a way to associate feedback and
# result message with specific goal requests. The id
# specified must be unique.
string id


"
          :dependencies [{:package "actionlib_msgs"
                          :name "GoalID"}]
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
            :name "text"}]})))
