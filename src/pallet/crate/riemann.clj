(ns pallet.crate.riemann
  "A pallet crate to install and configure riemann"
  (:require
   [clojure.tools.logging :refer [debugf]]
   [clojure.pprint :refer [pprint]]
   [clojure.walk :refer [postwalk-replace]]
   [pallet.action :refer [with-action-options]]
   [pallet.actions :as actions
    :refer [directory exec-checked-script packages remote-directory
            remote-file]]
   [pallet.api :refer [plan-fn] :as api]
   [pallet.crate :refer [assoc-settings defmethod-plan defplan get-settings
                         service-phases]]
   [pallet.crate-install :as crate-install]
   [pallet.crate.nohup]
   [pallet.crate.service
    :refer [supervisor-config supervisor-config-map] :as service]
   [pallet.utils :refer [apply-map deep-merge]]
   [pallet.script.lib :refer [config-root file log-root]]
   [pallet.stevedore :refer [fragment]]
   [pallet.version-dispatch :refer [defmethod-version-plan
                                    defmulti-version-plan]]))


(def ^{:doc "Flag for recognising changes to configuration"}
  config-changed-flag "riemann-config")

;;; # Settings
(defn service-name
  "Return a service name for riemann."
  [{:keys [instance-id] :as options}]
  (str "riemann" (when instance-id (str "-" instance-id))))

(defn default-settings [options]
  {:version "0.2.6"
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
   :variables {:listen-host  "0.0.0.0"
               :log-file     "/var/log/riemann.log"
               :need-expire  true
               :expire-every 10
               :need-tcp     true
               :tcp-port     5555
               :need-udp     true
               :udp-port     5555
               :need-ws      true
               :ws-port      5556
               :need-repl    true
               :repl-port    5557}
   :config '(let [index (default {:state "ok"
                                  :ttl   3600}
                          (update-index (index)))]
              (streams
               (with :service "events per sec"
                     (rate 30 index))
               index))
   :base-config '(do
                   (logging/init :file :log-file)
                   (when :need-tcp
                     (tcp-server :host :listen-host :port :tcp-port))
                   (when :need-udp
                     (udp-server :host :listen-host :port :udp-port))
                   (when :need-ws
                     (ws-server :host :listen-host :port :ws-port))
                   (when :need-repl
                     (repl-server :host :listen-host :port :repl-port))
                   (when :need-expire
                     (periodically-expire :expire-every))
                   :config)})

(defn url
  [{:keys [dist-url version] :as settings}]
  {:pre [dist-url version]}
  (format dist-url version))

(defn run-command
  "Return a script command to run riemann."
  [{:keys [home user config-dir] :as settings}]
  (fragment ((file ~home "bin" "riemann") (file ~config-dir "riemann.config"))))

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

(defmethod supervisor-config-map [:riemann :upstart]
  [_ {:keys [run-command service-name user] :as settings} options]
  {:service-name service-name
   :exec run-command
   :setuid user})

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
  (let [settings (deep-merge (default-settings options) settings)
        settings (settings-map (:version settings) settings)
        settings (update-in settings [:run-command]
                            #(or % (run-command settings)))]
    (debugf "riemann settings %s" settings)
    (assoc-settings :riemann settings {:instance-id instance-id})
    (supervisor-config :riemann settings (or options {}))))

;;; # User
(defplan user
  "Create the riemann user"
  [{:keys [instance-id] :as options}]
  (let [{:keys [user owner group home]} (get-settings :riemann options)]
    (assert (string? user) "user must be a username string")
    (assert (string? owner) "owner must be a username string")
    (actions/group group :system true)
    (debugf "riemann create owner %s" owner)
    (when (not= owner user)
      (actions/user owner :group group :system true))
    (debugf "riemann create user %s" user)
    (actions/user
     user :group group :system true :create-home true :shell :bash)))

;;; # Install
(defmethod-plan crate-install/install ::download
  [facility instance-id]
  (let [{:keys [user owner group home remote-file] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (packages :apt ["bzip2"] :aptitude ["bzip2"])
    (directory home :owner owner :group group)
    (apply-map remote-directory home :owner owner :group group remote-file)))

(defplan install
  "Install riemann."
  [{:keys [instance-id]}]
  (let [{:keys [install-strategy owner group log-dir] :as settings}
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
   :flag-on-changed config-changed-flag
   :owner owner :group group
   file-source))

(defplan configure
  "Write all config files"
  [{:keys [instance-id] :as options}]
  (let [settings (get-settings :riemann options)
        {:keys [config variables base-config] :as settings} settings
        config (postwalk-replace variables config)
        variables (assoc variables :config config)
        config (postwalk-replace variables base-config)]
    (debugf "configure %s %s" settings options)
    (config-file settings "riemann.config"
                 {:content (with-out-str (pprint config))})))

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

(defplan ensure-service
  "Ensure the service is running and has read the latest configuration."
  [& {:keys [instance-id] :as options}]
  (service :instance-id instance-id
           :if-stopped true
           :action :start)
  (service :instance-id instance-id
           :if-flag config-changed-flag
           :action :reload))

(defn server-spec
  "Returns a server-spec that installs and configures riemann."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   (merge {:settings (plan-fn (pallet.crate.riemann/settings (merge settings options)))
           :install (plan-fn
                      (user options)
                      (install options))
           :configure (plan-fn
                        (configure options)
                        (apply-map service :action :enable options))
           :run (plan-fn
                 (apply-map service :action :start options))
           :ensure-service (plan-fn (apply-map ensure-service options))}
          (service-phases :riemann options service))))
