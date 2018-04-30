(ns spectacles.cljs-test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [spectacles.impl-test]))

(doo-tests 'spectacles.impl-test)
