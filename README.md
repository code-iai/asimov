# asimov

---

> A robot may not injure a human being or, through inaction, allow a human being to come to harm.

> A robot must obey the orders given to it by human beings, except where such orders would conflict with the First Law.

> A robot must protect its own existence as long as such protection does not conflict with the First or Second Law.

---

A clojure client library for the robot operating system ROS.

This library provides rather lightweight interface to ros topics (and services ...comming soon...) based on core.async channels.

It is completely independent of the ROS toolchain,
requiring only message definitions to work with an existing ROS system.

##Installation
Add the following dependency to your project.clj file:

[![Current Version](https://clojars.org/asimov/latest-version.svg)](https://clojars.org/asimov)

##Resources
[API reference](http://code-iai.github.io/asimov/doc/)
## Example
First let's load the library and core.async.

    (refer '[asimov.api :as ros])
    (refer '[clojure.core.async :as a])

We then load the messages required for communicating with the ROS turtle simulator. In this case they are stored within the projects resource folder
so we ask asimov to load all message definitons contained within it.

    (def m (ros/msgs "resources"))
    
We than start a ROS node from the repl. In this case "/asimov" is the name of the node we give to the master,"192.168.56.101" is our own IP address in the same network as the ROS master,  "192.168.56.1" is the
address of the computer (or VM) containing the ros master and 11311 is the default ROS master port. We also provide a hosts map, that serves as a lightweight replacement for the hosts file (only one global hosts file is bad, editing files when working in the repl is also annoying).

Note that  can start an arbitrary number of ROS nodes on a given machine or in one repl. They can even connect to different masters.

    (def n (ros/init-node! "/asimov" "192.168.56.1" "192.168.56.101" 11311 {"lisp-tutorial" "192.168.56.101"} ))

We then publish a topic from the node that we just created.
This returns a core.async channel that we can read messages from.

Note that the channel is sliding, so should you not consume fast enough old messages will get dropped.
Also note that there is no concept of latching where one can read multiple times from a topic and receive the same result. This is the result of the replacement of callbacks with channels, where the ability to read from a channel indicates a new value.

    (def out (ros/pub! n (m {:package "turtlesim" :name "Velocity"}) "/turtle1/command_velocity"))

And subscribe to the same topic from within the same node.

    (def in (ros/sub! n (m {:package "turtlesim" :name "Velocity"}) "/turtle1/command_velocity"))

We can then start a go block to read and print a single value from the topic.

    (a/go (println (a/<! in)))

And write a simple message to it.

    (a/put! out {:angular 2 :linear 2})

 The turtle will move and we will print the following message.
 
    >>> {:angular 2.0, :linear 2.0}

    
