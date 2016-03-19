(defproject asimov "0.1.3-SNAPSHOT"
  :description "A clojure client library for the robot operating system ROS."
  :url "https://github.com/code-iai/asimov"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/mit-license.php"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/core.match "0.2.1"]
                 [org.clojure/algo.generic "0.1.2"]
                 [com.taoensso/timbre "4.3.1"]
                 [ring "1.4.0"]
                 [compojure "1.5.0"]
                 [necessary-evil "2.0.1"]
                 [aleph "0.4.1-beta5"]
                 [io.netty/netty "3.10.5.Final"]
                 [gloss "0.2.5"]
                 [byte-streams "0.2.1"]
                 [pandect "0.3.4"]
                 [instaparse "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [slingshot "0.12.2"]]
  :plugins [[codox "0.8.10"]]
  :aot [asimov.api]
  :codox {:src-dir-uri "http://github.com/code-iai/asimov/blob/master/"
          :src-linenum-anchor-prefix "L"})
