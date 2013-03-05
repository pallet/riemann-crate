(ns pallet.crate.riemann-test
  (:use
   clojure.test
   pallet.crate.riemann
   [pallet.actions :only [package-manager]]
   [pallet.api :only [plan-fn server-spec]]
   [pallet.algo.fsmop :only [complete?]]
   [pallet.api :only [lift plan-fn group-spec]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.crate.java :only [java]]
   [pallet.crate.network-service :only [wait-for-port-listen]]))

(def live-test-spec
  (server-spec
   :extends [(java {}) (riemann {})]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn (wait-for-port-listen 5555))}))
