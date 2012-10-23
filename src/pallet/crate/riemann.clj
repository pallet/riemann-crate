;;; Copyright 2012 Hugo Duncan.
;;; All rights reserved.

;;; http://aphyr.github.com/riemann/configuring.html

(ns pallet.crate.riemann
  "A pallet crate to install and configure riemann"
  (:use
   [clojure.string :only [join]]
   [clojure.algo.monads :only [m-when]]
   [pallet.action :only [with-action-options]]
   [pallet.actions
    :only [directory exec-checked-script exec-script packages
           remote-directory remote-file service symbolic-link user group
           assoc-settings]
    :rename {user user-action group group-action
             assoc-settings assoc-settings-action
             service service-action}]
   [pallet.api :only [plan-fn server-spec]]
   [pallet.config-file.format :only [sectioned-properties]]
   [pallet.crate
    :only [def-plan-fn assoc-settings defmethod-plan get-settings
           get-node-settings group-name nodes-with-role target-id]]
   [pallet.crate-install :only [install]]
   [pallet.script.lib :only [pid-root log-root config-root user-home]]
   [pallet.stevedore :only [script]]
   [pallet.utils :only [apply-map]]
   [pallet.version-dispatch
    :only [defmulti-version-plan defmethod-version-plan]]))


(def ^{:doc "Flag for recognising changes to configuration"}
  riemann-config-changed-flag "riemann-config")

;;; # Settings
(defn default-settings []
  {:version "0.1.2"
   :user "riemann"
   :owner "riemann"
   :group "riemann"
   :home "/opt/riemann"
   :config-dir "/etc/riemann/"
   :dist-url "http://aphyr.com/riemann/riemann-%s.tar.bz2"
   :config '(do
              (logging/init :file "riemann.log")
              (tcp-server :host "0.0.0.0")
              (udp-server :host "0.0.0.0")

              (periodically-expire 10)

              (let [client (tcp-client)
                                        ; Keep events for 5 minutes by default
                    index (default :ttl 300 (update-index (index)))]

                (streams
                 (with {:metric_f 1 :host nil :state "ok" :service "events/sec"}
                       (rate 5 index))

                 (where (service #"^per")
                        (percentiles 5 [0 0.5 0.95 0.99 1]
                                     index))

                                        ; Log expired events.
                 (expired
                  (fn [event] (info "expired" event)))

                 index)))})

(defn url
  [{:keys [dist-url version] :as settings}]
  (format dist-url version))

;;; At the moment we just have a single implementation of settings,
;;; but this is open-coded.
(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (m-result
   (cond
     (:install-strategy settings) settings
     :else  (assoc settings
              :install-strategy ::download
              :remote-file {:url (url settings)
                            :tar-options "xj"}))))

(def-plan-fn riemann-settings
  "Settings for riemann"
  [{:keys [user owner group dist dist-urls cloudera-version version
           instance-id]
    :as settings}]
  [settings (m-result (merge (default-settings) settings))
   settings (settings-map (:version settings) settings)]
  (assoc-settings :riemann settings {:instance-id instance-id}))

;;; # User
(def-plan-fn riemann-user
  "Create the riemann user"
  [{:keys [instance-id] :as options}]
  [{:keys [user owner group home]} (get-settings :riemann options)]
  (group-action group :system true)
  (m-when (not= owner user) (user-action owner :group group :system true))
  (user-action user :group group :system true :create-home true :shell :bash))

;;; # Install
(defmethod-plan install ::download
  [facility instance-id]
  [{:keys [user owner group home remote-file] :as settings}
   (get-settings facility {:instance-id instance-id})]
  (directory home :owner owner :group group)
  (apply-map remote-directory home :owner owner :group group remote-file))

(def-plan-fn install-riemann
  "Install riemann."
  [& {:keys [instance-id]}]
  [settings (get-settings :riemann {:instance-id instance-id})]
  (install :riemann instance-id))

;;; # Configuration
(def-plan-fn config-file
  "Helper to write config files"
  [{:keys [owner group config-dir] :as settings} filename file-source]
  (directory config-dir :owner owner :group group)
  (apply
   remote-file (str config-dir "/" filename)
   :flag-on-changed riemann-config-changed-flag
   :owner owner :group group
   (apply concat file-source)))

(def-plan-fn riemann-conf
  "Write all config files"
  [{:keys [instance-id] :as options}]
  [{:keys [config home user owner group]
    :as settings}
   (get-settings :riemann options)]
  (config-file settings "riemann.conf" {:content (str config)}))

(def-plan-fn riemann-run
  "Run riemann."
  [{:keys [instance-id] :as options}]
  [{:keys [home user config-dir]}
   (get-settings :riemann {:instance-id instance-id})]
  (with-action-options {:script-dir home :sudo-user user}
    (exec-checked-script
     (str "Riemann run")
     ("("
      ("nohup" (str ~home "/bin/riemann") (str ~config-dir "/riemann.conf"))
      "& )"))))

(defn riemann
  "Returns a server-spec that installs and configures riemann"
  [settings & {:keys [instance-id] :as options}]
  (server-spec
   :phases
   {:settings (riemann-settings (merge settings options))
    :install (plan-fn
              (riemann-user options)
              (install-riemann :instance-id instance-id))
    :configure (plan-fn (riemann-conf options)
                        (riemann-run options))}))
