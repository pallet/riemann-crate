(ns pallet.crate.riemann
  "A pallet crate to install and configure riemann"
  (:require
   [clojure.tools.logging :refer [debugf]]
   [pallet.actions :as actions]
   [pallet.action :refer [with-action-options]]
   [pallet.actions :refer [directory exec-checked-script remote-directory
                           remote-file]]
   [pallet.api :refer [plan-fn] :as api]
   [pallet.crate :refer [assoc-settings defmethod-plan defplan get-settings]]
   [pallet.crate-install :as crate-install]
   [pallet.crate.nohup]
   [pallet.crate.service
    :refer [supervisor-config supervisor-config-map] :as service]
   [pallet.utils :refer [apply-map]]
   [pallet.script.lib :refer [config-root file log-root]]
   [pallet.stevedore :refer [fragment]]
   [pallet.version-dispatch :refer [defmethod-version-plan
                                    defmulti-version-plan]]))


(def ^{:doc "Flag for recognising changes to configuration"}
  riemann-config-changed-flag "riemann-config")

;;; # Settings
(defn service-name
  "Return a service name for riemann."
  [{:keys [instance-id] :as options}]
  (str "riemann" (when instance-id (str "-" instance-id))))

(defn default-settings [options]
  {:version "0.1.5"
   :user "riemann"
   :owner "riemann"
   :group "riemann"
   :home "/opt/riemann"
   :config-dir (fragment (file (config-root) "riemann"))
   :log-dir (fragment (file (log-root) "riemann"))
   :supervisor :nohup
   :nohup {:process-name "java"}
   :service-name (service-name options)
   :dist-url "http://aphyr.com/riemann/riemann-%s.tar.bz2"
   :deb-url "http://aphyr.com/riemann/riemann_%s_all.deb"
   :config '(do
              (logging/init :file "/var/log/riemann/riemann.log")
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
  {:pre [dist-url version]}
  (format dist-url version))

(defn run-command
  "Return a script command to run riemann."
  [{:keys [home user config-dir] :as settings}]
  (fragment ((file ~home "bin" "riemann") (file ~config-dir "riemann.conf"))))

;;; At the moment we just have a single implementation of settings,
;;; but this is open-coded.
(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (cond
   (:install-strategy settings) settings
   :else  (assoc settings
            :install-strategy ::download
            :remote-file {:url (url settings)
                          :md5-url (str (url settings) ".md5")
                          :tar-options "xj"})))


(defmethod supervisor-config-map [:riemann :runit]
  [_ {:keys [run-command service-name user] :as settings} options]
  {:service-name service-name
   :run-file {:content (str "#!/bin/sh\nexec chpst -u " user " " run-command)}})

(defmethod supervisor-config-map [:riemann :nohup]
  [_ {:keys [run-command service-name user] :as settings} options]
  {:service-name service-name
   :run-file {:content run-command}
   :user user})

(defplan settings
  "Settings for riemann"
  [{:keys [user owner group dist dist-urls version instance-id]
    :as settings}
   & {:keys [instance-id] :as options}]
  (let [settings (merge (default-settings options) settings)
        settings (settings-map (:version settings) settings)
        settings (update-in settings [:run-command]
                            #(or % (run-command settings)))]
    (assoc-settings :riemann settings {:instance-id instance-id})
    (supervisor-config :riemann settings (or options {}))))

;;; # User
(defplan user
  "Create the riemann user"
  [{:keys [instance-id] :as options}]
  (let [{:keys [user owner group home]} (get-settings :riemann options)]
    (actions/group group :system true)
    (when (not= owner user)
      (actions/user owner :group group :system true))
    (actions/user
     user :group group :system true :create-home true :shell :bash)))

;;; # Install
(defmethod-plan crate-install/install ::download
  [facility instance-id]
  (let [{:keys [user owner group home remote-file] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (directory home :owner owner :group group)
    (apply-map remote-directory home :owner owner :group group remote-file)))

(defplan install
  "Install riemann."
  [{:keys [instance-id]}]
  (let [{:keys [owner group log-dir] :as settings}
        (get-settings :riemann {:instance-id instance-id})]
    (crate-install/install :riemann instance-id)
    (when log-dir
      (directory log-dir :owner owner :group group :mode "0755"))))

;;; # Configuration
(defplan config-file
  "Helper to write config files"
  [{:keys [owner group config-dir] :as settings} filename file-source]
  (directory config-dir :owner owner :group group)
  (apply-map
   remote-file (fragment (file ~config-dir ~filename))
   :flag-on-changed riemann-config-changed-flag
   :owner owner :group group
   file-source))

(defplan configure
  "Write all config files"
  [{:keys [instance-id] :as options}]
  (let [{:keys [config] :as settings} (get-settings :riemann options)]
    (debugf "configure %s %s" settings options)
    (config-file settings "riemann.conf" {:content (str config)})))

;;; # Run
(defplan service
  "Run the riemann service."
  [& {:keys [action if-flag if-stopped instance-id]
      :or {action :manage}
      :as options}]
  (let [{:keys [supervision-options] :as settings}
        (get-settings :riemann {:instance-id instance-id})]
    (service/service settings (merge supervision-options
                                     (dissoc options :instance-id)))))

(defn server-spec
  "Returns a server-spec that installs and configures riemann."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   {:settings (plan-fn (pallet.crate.riemann/settings (merge settings options)))
    :install (plan-fn
               (user options)
               (install options))
    :configure (plan-fn
                 (configure options)
                 (apply-map service :action :enable options))
    :run (plan-fn
           (apply-map service :action :start options))
    :stop (plan-fn
            (apply-map service :action :stop options))}))
