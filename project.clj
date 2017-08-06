(defproject spectacles "0.3.0"
  :description "Lenses for Clojure, checked at runtime using spec."
  :url "https://github.com/stathissideris/spectacles"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :profiles     {:provided
                 {:dependencies [[org.clojure/clojure "1.8.0"]
                                 [clojure-future-spec "1.9.0-alpha17"]]}})
