(ns pallet.crate.riemann-test
  (:require
   [pallet.crate.riemann :as riemann]
   [pallet.actions :refer [package-manager]]
   [pallet.algo.fsmop :refer [complete?]]
   [pallet.api :refer [plan-fn server-spec]]
   [pallet.crate.java :refer [java]]
   [pallet.crate.network-service :refer [wait-for-port-listen]]))

(def live-test-spec
  (server-spec
   :extends [(java {}) (riemann/server-spec {})]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn (wait-for-port-listen 5555))}))
