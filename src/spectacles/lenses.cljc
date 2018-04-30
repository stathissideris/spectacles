(ns spectacles.lenses
  (:refer-clojure :exclude [get get-in assoc assoc-in update update-in comp])
  (:require [spectacles.impl :as impl]))

(def get impl/get-value)
(def get-in impl/get-value-in)

(def assoc impl/assoc-value)
(def assoc-in impl/assoc-value-in)

(def update impl/update-value)
(def update-in impl/update-value-in)

(def comp impl/compose)
