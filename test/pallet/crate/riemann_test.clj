(ns pallet.crate.riemann-test
  (:require
   [clojure.test :refer [deftest is]]
   [pallet.crate.riemann :as riemann]
   [pallet.actions :refer [package-manager]]
   [pallet.algo.fsmop :refer [complete?]]
   [pallet.api :refer [plan-fn server-spec]]
   [pallet.build-actions :as build-actions]
   [pallet.crate.java :as java]
   [pallet.crate.network-service :refer [wait-for-port-listen]]
   [pallet.crate.nohup :as nohup]
   [pallet.crate.runit :as runit]
   [pallet.crate.upstart :as upstart]))

(deftest invoke-test
  (is (build-actions/build-actions {}
        (riemann/settings {:supervisor :nohup})
        (riemann/install {})
        (riemann/configure {})
        (riemann/service)))
  (is (build-actions/build-actions {}
        (riemann/settings {:supervisor :runit})
        (riemann/install {})
        (riemann/configure {})
        (riemann/service))))

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
            :test (plan-fn
                    (riemann/service :action :start)
                    (wait-for-port-listen 5555))}))

(def live-upstart-test-spec
  (server-spec
   :extends [(java/server-spec {})
             (upstart/server-spec {})
             (riemann/server-spec {:supervisor :upstart})]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn
                    (riemann/service :action :start)
                    (wait-for-port-listen 5555))}))
