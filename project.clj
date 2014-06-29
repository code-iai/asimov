(defproject asimov "0.1.0-SNAPSHOT"
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/core.match "0.2.1"]
                 [com.taoensso/timbre "3.2.0"]
                 [gui-diff "0.6.5"]
                 [ring "1.2.2"]
                 [compojure "1.1.8"]
                 [necessary-evil "2.0.0"]
                 [aleph "0.3.2"]
                 [gloss "0.2.2"]
                 [byte-streams "0.1.10"]
                 [pandect "0.3.2"]
                 [instaparse "1.3.2"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [slingshot "0.10.3"]]
  :plugins [[lein-marginalia "0.7.1"]])
