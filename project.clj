(defproject spectacles "0.3.4"
  :description "Lenses for Clojure, checked at runtime using spec."
  :url "https://github.com/stathissideris/spectacles"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.238"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]]

  :doo {:build "test"
        :alias {:default [:node]}}

  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "out/testable.js"
                                   :main spectacles.cljs-test-runner
                                   :target :nodejs
                                   :optimizations :none}}]})
