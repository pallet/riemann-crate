(ns pallet.crate.riemann-test
  (:use
   clojure.test
   pallet.crate.riemann
   [pallet.actions :only [package-manager]]
   [pallet.algo.fsmop :only [complete?]]
   [pallet.api :only [lift plan-fn group-spec]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.crate.java :only [java]]
   [pallet.crate.network-service :only [wait-for-port-listen]]
   [pallet.live-test :only [images test-nodes]]))

(deftest ^:live-test live-test
  (let [settings {}]
    (doseq [image (images)]
      (test-nodes
       [compute node-map node-types [:install :configure]]
       {:riemann
        (group-spec
         "riemann"
         :image image
         :count 1
         :extends [(java {}) (riemann settings)]
         :phases {:bootstrap (plan-fn (automated-admin-user))
                  :install (plan-fn (package-manager :update))
                  :run-test (plan-fn (wait-for-port-listen 5555))})}
       (let [op (lift (:riemann node-types)
                      :phase [:run-test]
                      :compute compute)]
         @op
         (is (complete? op)))))))
