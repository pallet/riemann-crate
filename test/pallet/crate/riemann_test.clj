(ns pallet.crate.riemann-test
  (:require
   [pallet.crate.riemann :as riemann]
   [pallet.actions :refer [package-manager]]
   [pallet.algo.fsmop :refer [complete?]]
   [pallet.api :refer [plan-fn server-spec]]
   [pallet.crate.java :as java]
   [pallet.crate.network-service :refer [wait-for-port-listen]]
   [pallet.crate.nohup :as nohup]
   [pallet.crate.runit :as runit]))

(def live-nohup-test-spec
  (server-spec
   :extends [(java/server-spec {})
             (riemann/server-spec {:supervisor :nohup})]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn (wait-for-port-listen 5555))}))

(def live-runit-test-spec
  (server-spec
   :extends [(java/server-spec {})
             (runit/server-spec {})
             (riemann/server-spec {:supervisor :runit})]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn (wait-for-port-listen 5555))}))
