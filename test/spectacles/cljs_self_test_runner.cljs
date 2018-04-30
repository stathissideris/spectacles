(ns spectacles.cljs-self-test-runner
  (:require [clojure.test :refer [run-tests]]
            [spectacles.impl-test]))

(run-tests 'spectacles.impl-test)
