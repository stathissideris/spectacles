(ns spectacles.lenses
  (:refer-clojure :exclude [get get-in assoc assoc-in update update-in])
  (:require [spectacles.impl :as impl]))

(def get impl/get-value)
(def get-in impl/get-value-in)

(def assoc impl/assoc-value)
(def update impl/update-value)
