(ns asimov.configuration)

(def configuration (atom {:master-uri "http://192.168.56.101:11311/"
                          :master-addr ["172.23.198.225" 11311]
                          :localhost  "172.23.198.215"}))


(defn cfg
  ([path] (cfg path nil))
  ([path not-found]
     (get-in @configuration (if (sequential? path)
                              path
                              [path])  not-found)))

