(defproject com.palletops/riemann-crate "0.8.0-alpha.4"
  :description "Crate for riemann installation"
  :url "http://github.com/pallet/riemann-crate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :resource {:resource-paths ["doc-src"]
             :target-path "target/classes/pallet_crate/riemann_crate/"
             :includes [#"doc-src/USAGE.*"]}
  :prep-tasks ["resource" "crate-doc"])
