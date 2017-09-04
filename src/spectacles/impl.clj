(ns spectacles.impl
  (:require [clojure.spec.alpha :as s]))

(defn- spec-form-type [x]
  (cond (seq? x) (first x)
        (and (keyword? x) (namespace x)) :spec-ref
        :else :default))

(defmulti valid-keys spec-form-type)
(defmethod valid-keys :default [_] #{})

(defmethod valid-keys :spec-ref [s] (-> s s/get-spec s/form valid-keys))
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

(defmulti get-value* (fn [_ spec _] (spec-form-type (s/form spec))))
(defmethod get-value* `s/keys [m _ k] (get m k))
(defmethod get-value* `s/map-of [m _ k] (get m k))
(defmethod get-value* `s/and [m _ k] (if (integer? k) (nth m k) (get m k)))
(defmethod get-value* `s/or [m _ k] (if (integer? k) (nth m k) (get m k)))
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

(defmulti keys->spec-names
  "Takes a spec form and returns a map of unqualified keys to (fully
  qualified) spec names."
  spec-form-type)
(defmethod keys->spec-names :default [_] #{})
(defmethod keys->spec-names :spec-ref [s] (-> s s/get-spec s/form keys->spec-names))
(defmethod keys->spec-names `s/cat [_] ::na)
(defmethod keys->spec-names `s/keys
  [[_ & spec]]
  (as-> spec $
    (partition 2 $)
    (mapcat second $)
    (zipmap (map (comp keyword name) $) $)))

(defmethod keys->spec-names `s/and
  [[_ & specs]]
  (apply merge (map keys->spec-names specs)))

(defmethod keys->spec-names `s/or
  [spec]
  (apply merge (map keys->spec-names (drop 1 (take-nth 2 spec)))))

(defn- key->spec [parent-spec spec-name]
  (if (and (keyword? spec-name) (namespace spec-name))
    spec-name
    (let [m (-> parent-spec s/get-spec s/form keys->spec-names)]
      (if (= ::na m) m
          (get m spec-name)))))

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

(defmulti assoc-value* (fn [_ spec _ _] (spec-form-type (s/form spec))))
(defmethod assoc-value* :spec-ref [m spec k v] (assoc-value* m (-> spec s/get-spec s/form) k v))
(defmethod assoc-value* `s/keys [m _ k v]
  (assoc m k v))
(defmethod assoc-value* `s/and [m _ k v] (if (integer? k) (assoc (vec m) k v) (assoc m k v)))
(defmethod assoc-value* `s/or [m _ k v] (if (integer? k) (assoc (vec m) k v) (assoc m k v)))
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

(defn compose [lens1 lens2]
  (let [last-spec (reduce key->spec (first lens1) (rest lens1))]
    (if-not (= last-spec (first lens2))
      (throw (ex-info (format "Cannot compose: last spec of lens1 (%s) does not match first spec of lens2 (%s)"
                              (pr-str last-spec) (pr-str (first lens2)))
                      {:lens1 lens1
                       :lens2 lens2}))
      (vec (concat lens1 (rest lens2))))))
