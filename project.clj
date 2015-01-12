(defproject asimov "0.1.2"
  :description "A clojure client library for the robot operating system ROS."
  :url "https://github.com/code-iai/asimov"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/mit-license.php"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/core.match "0.2.1"]
                 [org.clojure/algo.generic "0.1.2"]
                 [org.clojure/tools.trace "0.7.8"]
                 [com.taoensso/timbre "3.2.0"]
                 [gui-diff "0.6.5"]
                 [ring "1.2.2"]
                 [compojure "1.1.8"]
                 [org.clojure/core.memoize "0.5.6"]
                 [necessary-evil "2.0.0"]
                 [aleph "0.3.2"]
                 [gloss "0.2.2"]
                 [byte-streams "0.1.10"]
                 [pandect "0.3.2"]
                 [instaparse "1.3.2"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [slingshot "0.10.3"]]
  :plugins [[codox "0.8.10"]]
  :aot [asimov.api]
  :codox {:src-dir-uri "http://github.com/code-iai/asimov/blob/master/"
          :src-linenum-anchor-prefix "L"})
