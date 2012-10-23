{:dev
 {:dependencies [[org.cloudhoist/pallet "0.8.0-SNAPSHOT"
                  :classifier "tests"]
                 [org.cloudhoist/java "0.8.0-SNAPSHOT"]
                 [riemann-clojure-client "0.0.3"]
                 [ch.qos.logback/logback-classic "1.0.0"]]
  :test-selectors {:default (complement :live-test)
                   :live-test :live-test
                   :all (constantly true)}
  :repositories {"boundary-site" "http://maven.boundary.com/artifactory/repo"}}}
