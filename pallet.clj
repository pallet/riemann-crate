;;; Pallet project configuration file

(require
 '[pallet.crate.riemann-test
   :refer [live-nohup-test-spec live-runit-test-spec live-upstart-test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject riemann-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "riemann-live-test"
             :extends [with-automated-admin-user
                       live-nohup-test-spec]
             :roles #{:live-test :default :nohup})
           (group-spec "riemann-runit-test"
             :extends [with-automated-admin-user
                       live-runit-test-spec]
             :roles #{:live-test :default :runit})
           (group-spec "riemann-upstart-test"
             :extends [with-automated-admin-user
                       live-upstart-test-spec]
             :roles #{:live-test :default :upstart})])
