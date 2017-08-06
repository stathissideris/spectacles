(ns spectacles.impl
  (:require [clojure.spec.alpha :as s]))

(defmulti valid-keys (fn [x] (if (seq? x) (first x) :default)))

(defmethod valid-keys :default [_] #{})
(defmethod valid-keys :req [[_ k]] k)
(defmethod valid-keys :opt [[_ k]] k)
(defmethod valid-keys :req-un [[_ k]] (->> k (map name) (map keyword)))
(defmethod valid-keys :opt-un [[_ k]] (->> k (map name) (map keyword)))

(defmethod valid-keys `s/map-of
  [[_ key-pred _]] (eval key-pred))

(defmethod valid-keys `s/keys
  [[_ & spec]]
  (set (mapcat valid-keys (partition 2 spec))))

(defmethod valid-keys `s/and
  [[_ & spec]]
  (set (mapcat valid-keys spec)))

(defmethod valid-keys `s/or
  [[_ & spec]]
  (set (mapcat (comp valid-keys second) (partition 2 spec))))

(defmethod valid-keys `s/cat
  [[_ & spec]]
  (set (concat (map first (partition 2 spec))
               (range (/ (count spec) 2)))))

(defmulti get-value* (fn [_ spec _] (let [form (s/form spec)] (if (seq? form) (first form) form))))
(defmethod get-value* `s/keys [m _ k] (get m k))
(defmethod get-value* `s/map-of [m _ k] (get m k))
(defmethod get-value* `s/cat [m spec k]
  (if (integer? k)
    (nth m k)
    (let [c (s/conform spec m)]
      (get c k))))

(defn get-value [m spec-name k]
  (let [spec     (s/get-spec spec-name)
        form     (s/form spec)
        valid-ks (valid-keys form)]
    (if-not (valid-ks k)
      (throw (ex-info (format "Invalid key %s for spec %s (valid keys: %s)"
                              (pr-str k) (str spec-name) (pr-str valid-ks))
                      {:reason     :invalid-key
                       :collection m
                       :key        k
                       :spec       spec-name
                       :valid-keys valid-ks}))
      (get-value* m spec k))))

(defn- keys-spec-names [[_ & ks]]
  (as-> ks $
    (partition 2 $)
    (mapcat second $)
    (zipmap (map (comp keyword name) $) $)))

(defn- key->spec [parent-spec spec-name]
  (if (and (keyword? spec-name) (namespace spec-name))
    spec-name
    (let [form (-> parent-spec s/get-spec s/form)]
      (cond (= `s/keys (first form))
            (get (keys-spec-names form) spec-name)
            (= `s/cat (first form))
            ::na))))

(defn get-value-in [m [spec-name & path]]
  (second
   (reduce
    (fn [[spec-name mm] k]
      [(key->spec spec-name k)
       (try
         (get-value mm spec-name k)
         (catch clojure.lang.ExceptionInfo e
           (throw (ex-info (.getMessage e)
                           (assoc (ex-data e)
                                  :path (vec path)
                                  :root-map m)))))])
    [spec-name m] path)))

(defmulti assoc-value* (fn [_ spec _ _] (let [form (s/form spec)] (if (seq? form) (first form) form))))
(defmethod assoc-value* `s/keys [m _ k v]
  (assoc m k v))
(defmethod assoc-value* `s/cat [m spec k v]
  (if (integer? k)
    (assoc (vec m) k v)
    (let [c (s/conform spec m)]
      (s/unform spec (assoc c k v)))))

(defn assoc-value [m spec-name k v]
  (let [key-spec (s/get-spec spec-name)
        form     (s/form key-spec)
        valid-ks (valid-keys form)]
    (if-not (valid-ks k)
      (throw (ex-info (format "Invalid key %s for spec %s (valid keys: %s)"
                              (str k) (str spec-name) (pr-str valid-ks))
                      {:reason     :invalid-key
                       :collection m
                       :key        k
                       :value      v
                       :spec       spec-name
                       :valid-keys valid-ks}))
      (let [value-spec (key->spec spec-name k)]
        (if (and (not= ::na value-spec) (not (s/valid? value-spec v)))
          (throw (ex-info (format "Invalid value %s for key %s in value %s (should conform to: %s)"
                                  (pr-str v) (str k) (pr-str m) (pr-str (s/form value-spec)))
                          {:reason     :invalid-value
                           :collection m
                           :key        k
                           :value      v
                           :spec       spec-name}))
          (assoc-value* m key-spec k v))))))

(defn update-value [m spec-name k fun & more]
  (let [v (get-value m spec-name k)]
    (assoc-value m spec-name k (apply fun v more))))

(defn assoc-value-in [m [spec-name & path] v]
  (let [[k & ks] path]
    (if ks
      (let [child-spec (key->spec spec-name k)]
        (assoc-value m spec-name k
                     (assoc-value-in (get-value m spec-name k) (into [child-spec] ks) v)))
      (assoc-value m spec-name k v))))

(defn update-value-in [m lens fun & more]
  (let [v (get-value-in m lens)]
    (assoc-value-in m lens (apply fun v more))))
