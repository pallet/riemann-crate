;;; Pallet project configuration file

(require
 '[pallet.crate.riemann-test :refer [live-test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject riemann-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "riemann-live-test"
                       :extends [with-automated-admin-user
                                 live-test-spec])])
