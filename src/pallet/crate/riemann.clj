(ns pallet.crate.riemann
  "A pallet crate to install and configure riemann"
  (:require
   [pallet.actions :as actions]
   [pallet.action :refer [with-action-options]]
   [pallet.actions :refer [directory exec-checked-script remote-directory
                           remote-file]]
   [pallet.api :refer [plan-fn server-spec]]
   [pallet.crate :refer [assoc-settings defmethod-plan defplan get-settings]]
   [pallet.crate-install :refer [install]]
   [pallet.utils :refer [apply-map]]
   [pallet.version-dispatch :refer [defmethod-version-plan
                                    defmulti-version-plan]]))


(def ^{:doc "Flag for recognising changes to configuration"}
  riemann-config-changed-flag "riemann-config")

;;; # Settings
(defn default-settings []
  {:version "0.1.5"
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
  {:pre [dist-url version]}
  (format dist-url version))

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

(defplan riemann-settings
  "Settings for riemann"
  [{:keys [user owner group dist dist-urls version instance-id]
    :as settings}]
  (let [settings (merge (default-settings) settings)
        settings (settings-map (:version settings) settings)]
    (assoc-settings :riemann settings {:instance-id instance-id}))

;;; # User
  (defplan riemann-user
    "Create the riemann user"
    [{:keys [instance-id] :as options}]
    (let [{:keys [user owner group home]} (get-settings :riemann options)]
      (actions/group group :system true)
      (when (not= owner user)
        (actions/user owner :group group :system true))
      (actions/user
       user :group group :system true :create-home true :shell :bash))))

;;; # Install
(defmethod-plan install ::download
  [facility instance-id]
  (let [{:keys [user owner group home remote-file] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (directory home :owner owner :group group)
    (apply-map remote-directory home :owner owner :group group remote-file)))

(defplan install-riemann
  "Install riemann."
  [& {:keys [instance-id]}]
  (let [settings (get-settings :riemann {:instance-id instance-id})]
    (install :riemann instance-id)))

;;; # Configuration
(defplan config-file
  "Helper to write config files"
  [{:keys [owner group config-dir] :as settings} filename file-source]
  (directory config-dir :owner owner :group group)
  (apply
   remote-file (str config-dir "/" filename)
   :flag-on-changed riemann-config-changed-flag
   :owner owner :group group
   (apply concat file-source)))

(defplan riemann-conf
  "Write all config files"
  [{:keys [instance-id] :as options}]
  (let [{:keys [config home user owner group] :as settings}
        (get-settings :riemann options)]
    (config-file settings "riemann.conf" {:content (str config)})))

(defplan riemann-run
  "Run riemann."
  [{:keys [instance-id] :as options}]
  (let [{:keys [home user config-dir]}
        (get-settings :riemann {:instance-id instance-id})]
    (with-action-options {:script-dir home :sudo-user user}
      (exec-checked-script
       (str "Riemann run")
       ("("
        ("nohup" (str ~home "/bin/riemann") (str ~config-dir "/riemann.conf"))
        "& )")
       ("sleep" 5)))))

(defn riemann
  "Returns a server-spec that installs and configures riemann"
  [settings & {:keys [instance-id] :as options}]
  (server-spec
   :phases
   {:settings (plan-fn (riemann-settings (merge settings options)))
    :install (plan-fn
              (riemann-user options)
              (install-riemann :instance-id instance-id))
    :configure (plan-fn (riemann-conf options)
                        (riemann-run options))}))
