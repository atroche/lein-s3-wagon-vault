(ns lein-s3-wagon-vault.plugin
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.edn :as edn]
    [leiningen.core.main :as main]
    [vault.client :as vault])
  (:import
    java.util.Date))

(defn validate-env
  []
  (for [envname ["VAULT_ADDR" "VAULT_TOKEN"]]
    (or (seq (System/getenv envname))
        (throw (str "Could not find required environment variable " envname)))))

(defn mk-cache-dir
  "Create the cache dir if it doesn't already exist and return the File object."
  []
  (doto (io/file (str (System/getenv "HOME") "/.lein/s3-wagon-vault-cache"))
    .mkdirs))

(defn vault-client
  []
  (let [vault-addr (System/getenv "VAULT_ADDR")
        vault-token (System/getenv "VAULT_TOKEN")
        client (vault/http-client vault-addr)]
    (vault/authenticate! client :token vault-token)
      client))

(defn get-creds
  "Get credentials from cache or from vault.
  Cache is good for one hour.
  Returns [ACCESS_KEY SECRET_KEY]"
  [vault-path]
  ; TODO caching wouldn't be necessary with STS credentials.  Need to verify if
  ;      STS creds can work with s3-wagon-private.
  (let [cache-dir (mk-cache-dir)
        cache-file (io/file cache-dir (string/replace vault-path "/" "."))
        cached (if (.exists cache-file) (edn/read-string (slurp cache-file)))
        create-time (:create-time cached)
        vault-secret (if (and create-time
                              (-> (Date.) .getTime (- create-time) (< (* 1000 60 60))))
                       cached
                       (let [vault-client (vault-client)
                             new-create-time (.getTime (Date.))
                             result (assoc (vault/read-secret vault-client vault-path)
                                           :create-time new-create-time)]
                         (spit cache-file result)
                         (main/warn (str "lein-s3-wagon-vault: waiting 10 seconds "
                                         "for IAM eventual consistency"))
                         (Thread/sleep 10000)
                         result))]
    [(:access_key vault-secret) (:secret_key vault-secret)]))

(defn update-creds
  [repo vault-aws-path]
  (validate-env)
  (when-not (and (string? vault-aws-path) (.startsWith vault-aws-path "aws/creds/"))
    (throw (RuntimeException.
             (str "Invalid \":vault/\" entry in repositories. "
                  "Must start with \":vault/aws/creds/\"."
                  repo
                  vault-aws-path))))
  (let [[access-key secret-key] (get-creds vault-aws-path)]
    (assoc repo
           :username access-key
           :passphrase secret-key)))

(defn vaulted?
  [x]
  (and (string? x) (.startsWith x ":vault/")))

(defn inject-vault
  [repo-list]
  (try
    (mapv
      (fn [[repo-name {:keys [username passphrase] :as repo}]]
        (if (or (vaulted? username) (vaulted? passphrase))
          (if-not (= username passphrase)
            (throw (ex-info (str "Invalid :repositories entry, if using vault aws creds, "
                                 "then username and password must have the same setting")
                            {:repository repo}))
            [repo-name (update-creds repo (second (re-find #"^:vault/(.+)" username)))])
          [repo-name repo]))
      repo-list)
    (catch Exception e
      ; If there are errors (vault, network, etc), don't make lein bomb,
      ; just skip updating the repo-list.
      (.printStackTrace e)
      repo-list)))

(defn middleware [project]
  (-> project
      (update-in [:repositories] inject-vault)))
