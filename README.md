# asimov

A clojure library for the RobotOperatingSystem.

## Example

    (alter-var-root #'*out* (constantly *out*))
    (use 'asimov.api)
    (use 'clojure.core.async)
    (def m (msgs "resources"))
    (def n (init-node! "192.168.56.101" "192.168.56.1" 11311 {"lisp-tutorial" "192.168.56.101"} "/asimov"))
    (def out (pub! n (m {:package "turtlesim" :name "Velocity"}) "/turtle1/command_velocity"))
    (def in (sub! n (m {:package "turtlesim" :name "Velocity"}) "/turtle1/command_velocity"))
    (take! in println)
    (put! out {:angular 2 :linear 2})
    >>> {:angular 2.0, :linear 2.0} ;Because we subscribed to ourselves, we as well as the turtle get the topics messages.
