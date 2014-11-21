{:dev
 {:dependencies [[com.palletops/pallet "0.8.0-beta.9" :classifier "tests"]
                 [com.palletops/java-crate "0.8.0-beta.5"]
                 [com.palletops/runit-crate "0.8.0-alpha.1"]
                 [com.palletops/upstart-crate "0.8.0-alpha.1"]
                 [com.palletops/crates "0.1.1"]
                 [riemann-clojure-client "0.0.3"]
                 [ch.qos.logback/logback-classic "1.0.9"]]
  :plugins [[com.palletops/pallet-lein "0.8.0-alpha.1"]
            [com.palletops/lein-pallet-crate "0.1.0"]
            [lein-resource "0.3.2"]
            [lein-pallet-release "RELEASE"]]
  :aliases {"live-test-up"
            ["pallet" "up"
             "--phases" "install,configure,test"
             "--selector" "live-test"]
            "live-test-down" ["pallet" "down" "--selector" "live-test"]
            "live-test" ["do" "live-test-up," "live-test-down"]}
  :test-selectors {:default (complement :live-test)
                   :live-test :live-test
                   :all (constantly true)}
  :repositories {"boundary-site" "http://maven.boundary.com/artifactory/repo"}}
 :pallet {:jvm-opts ["-Djna.nosys=true"]}
 :jclouds {:repositories
           {"sonatype"
            "https://oss.sonatype.org/content/repositories/releases/"}
           :dependencies [[org.cloudhoist/pallet-jclouds "1.5.2"]
                          [org.jclouds/jclouds-allblobstore "1.5.5"]
                          [org.jclouds/jclouds-allcompute "1.5.5"]
                          [org.jclouds.driver/jclouds-slf4j "1.5.5"
                           :exclusions [org.slf4j/slf4j-api]]
                          [org.jclouds.driver/jclouds-sshj "1.5.5"]]}
 :vmfest {:dependencies [[com.palletops/pallet-vmfest "0.3.0-alpha.4"]]}}
