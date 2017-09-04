(ns spectacles.impl-test
  (:require [spectacles.impl :refer :all :as sut]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]))

(deftest valid-keys-test
  (is (= #{:echo-server :api-secret :call-receipients :msg-receipients :api-key}
         (valid-keys `(s/keys :req-un [::api-key ::api-secret ::echo-server ::call-receipients ::msg-receipients]))))
  (is (= #{} (valid-keys `(fn [x] (string? x)))))
  (is (= #{} (valid-keys `string?)))
  (is (= #{:echo-server :api-secret :call-receipients :msg-receipients :api-key}
         (valid-keys `(s/and (s/keys :req-un [::api-key ::api-secret ::echo-server ::call-receipients ::msg-receipients]) string?))))
  (is (= #{0 1 2 :config :path :text}
         (valid-keys `(s/cat :config ::file-storage :path string? :text string?)))))

(s/def ::filename string?)
(s/def ::dims (s/coll-of string?))
(s/def ::simple-map (s/map-of string? number?))
(s/def ::deeper3 string?)
(s/def ::deeper2 (s/and (s/keys :opt-un [::deeper3 ::deeper2])
                        identity))
(s/def ::deeper1 (s/or :2 (s/keys :opt-un [::deeper2])
                       :3 (s/keys :opt-un [::deeper3])))
(s/def ::target-dims (s/keys :req-un [::dims]
                             :opt-un [::the-cat ::simple-map ::deeper1]))
(s/def ::the-cat (s/cat :a string? :b number?))
(s/def ::targets (s/keys :req-un [::filename ::target-dims]))

(def targets {:filename "foo" :target-dims {:dims ["foo" "bar"]}})

(s/def ::other-keys (s/keys :req-un [::the-cat ::deeper3 ::filename]))
(s/def ::other (s/and ::other-keys (fn [x] (= 3 (count x)))))
(s/def ::other2 ::other)

(def other-value {:deeper3  "foo"
                  :filename "bar"
                  :the-cat  ["foo" 4]})

(deftest get-valid-keys-test
  (testing "for specs that refer to other specs"
    (is (= #{:the-cat :filename :deeper3}
           (@#'sut/valid-keys (-> ::other s/get-spec s/form))))
    (is (= #{:the-cat :filename :deeper3}
           (@#'sut/valid-keys (-> ::other2 s/get-spec s/form))))))

(deftest get-value-test
  (is (s/valid? ::targets targets))

  (testing "for s/keys"
    (is (= "foo" (get-value targets ::targets :filename)))
    (is (thrown? Exception (get-value targets ::targets :filo))))

  (testing "for s/cat"
    (testing "using names"
      (is (= "foo" (get-value ["foo" 10] ::the-cat :a)))
      (is (= 10 (get-value ["foo" 10] ::the-cat :b)))
      (is (thrown? Exception (get-value ["foo" 10] ::the-cat :c))))
    (testing "using indexes"
      (is (= "foo" (get-value ["foo" 10] ::the-cat 0)))
      (is (= 10 (get-value ["foo" 10] ::the-cat 1)))
      (is (thrown? Exception (get-value ["foo" 10] ::the-cat 3)))))

  (testing "for specs that refer to other specs"
    (is (= "foo" (get-value other-value ::other :deeper3)))
    (is (= "bar" (get-value other-value ::other :filename)))
    (is (= ["foo" 4] (get-value other-value ::other :the-cat)))

    (is (= "foo" (get-value other-value ::other2 :deeper3)))
    (is (= "bar" (get-value other-value ::other2 :filename)))
    (is (= ["foo" 4] (get-value other-value ::other2 :the-cat)))))

(def targets2
  {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10] :simple-map {"foo" 2 "bar" 3}}})

(deftest get-value-in-test
  (is (s/valid? ::targets targets2))

  (testing "for s/keys"
    (is (= ["foo" "bar"] (get-value-in targets [::targets :target-dims :dims])))
    (is (thrown? Exception (get-value-in targets [::targets :WRONG])))
    (is (thrown? Exception (get-value-in targets [::targets :target-dims :WRONG])))
    (testing "with missing keys and invalid keys deeper"
      (is
       (thrown?
        Exception
        (get-value-in
         {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10] :simple-map {"foo" 2 "bar" 3}}}
         [::targets :target-dims :deeper1 :WRONG]))))
    (testing "with missing keys and invalid keys deeper"
      (is
       (thrown?
        Exception
        (get-value-in
         {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10] :simple-map {"foo" 2 "bar" 3}}}
         [::targets :target-dims :deeper1 :deeper2 :WRONG])))))

  (testing "for s/keys and s/cat"
    (is (= "foo" (get-value-in targets2 [::targets :target-dims :the-cat 0])))
    (is (= "foo" (get-value-in targets2 [::targets :target-dims :the-cat :a])))
    (is (= 10 (get-value-in targets2 [::targets :target-dims :the-cat 1])))
    (is (= 10 (get-value-in targets2 [::targets :target-dims :the-cat :b])))
    (is (thrown? Exception (get-value-in targets2 [::targets :target-dims :the-cat 2])))
    (is (thrown? Exception (get-value-in targets2 [::targets :target-dims :the-cat :c]))))

  (testing "for s/map-of"
    (is (= 2 (get-value-in targets2 [::targets :target-dims :simple-map "foo"])))
    (is (thrown? Exception (get-value-in targets2 [::targets :target-dims :simple-map 10]))))

  (testing "for s/and and s/or"
    (is (= "pretty deep"
           (get-value-in
            {:filename    "foo"
             :target-dims
             {:dims ["foo" "bar"]
              :deeper1
              {:deeper2
               {:deeper2
                {:deeper3 "pretty deep"}}}}}
            [::targets :target-dims :deeper1 :deeper2 :deeper2 :deeper3])))
    (is (= "pretty deep"
           (get-value-in
            {:filename    "foo"
             :target-dims
             {:dims ["foo" "bar"]
              :deeper1
              {:deeper3 "pretty deep"}}}
            [::targets :target-dims :deeper1 :deeper3]))))

  (testing "for specs that refer to other specs"
    (is (= "foo" (get-value-in other-value [::other :the-cat 0])))
    (is (= "foo" (get-value-in other-value [::other2 :the-cat 0])))
    (is (= 4 (get-value-in other-value [::other :the-cat 1])))
    (is (= 4 (get-value-in other-value [::other2 :the-cat 1])))))

(deftest assoc-value-test
  (is (= ["bar" 10] (assoc-value ["foo" 10] ::the-cat :a "bar")))
  (is (= ["foo" 20] (assoc-value ["foo" 10] ::the-cat :b 20)))

  (testing "for specs that refer to other specs"
    (is (= {:deeper3 "baz", :filename "bar", :the-cat ["foo" 4]}
           (assoc-value other-value ::other :deeper3 "baz")))
    (is (= {:deeper3 "foo", :filename "boo", :the-cat ["foo" 4]}
           (assoc-value other-value ::other :filename "boo")))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["bar" 5]}
           (assoc-value other-value ::other :the-cat ["bar" 5])))

    (is (= {:deeper3 "baz", :filename "bar", :the-cat ["foo" 4]}
           (assoc-value other-value ::other2 :deeper3 "baz")))
    (is (= {:deeper3 "foo", :filename "boo", :the-cat ["foo" 4]}
           (assoc-value other-value ::other2 :filename "boo")))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["bar" 5]}
           (assoc-value other-value ::other2 :the-cat ["bar" 5])))))

(deftest update-value-test
  (is (= ["foobar" 10] (update-value ["foo" 10] ::the-cat :a #(str % "bar"))))
  (is (= ["foo" 11] (update-value ["foo" 10] ::the-cat :b inc)))

  (testing "for specs that refer to other specs"
    (is (= {:deeper3 "foobar", :filename "bar", :the-cat ["foo" 4]}
           (update-value other-value ::other :deeper3 #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "barbar", :the-cat ["foo" 4]}
           (update-value other-value ::other :filename #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["bar" 4]}
           (update-value other-value ::other :the-cat assoc 0 "bar")))))

(def targets3
  {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10]}})

(deftest assoc-value-in-test
  (testing "for s/keys"
    (is (= {:filename "foo", :target-dims {:dims ["zoo" "far"]}}
           (assoc-value-in targets [::targets :target-dims :dims] ["zoo" "far"])))
    (is (thrown? Exception (assoc-value-in targets [::targets :target-dims :dims] 10)))
    (is (thrown? Exception (assoc-value-in targets [::targets :WRONG] 10)))
    (is (thrown? Exception (assoc-value-in targets [::targets :target-dims :WRONG] 10)))
    (testing "with missing keys and invalid keys deeper"
      (is
       (thrown?
        Exception
        (assoc-value-in
         {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10] :simple-map {"foo" 2 "bar" 3}}}
         [::targets :target-dims :deeper1 :deeper2 :WRONG] 20)))))

  (testing "for s/keys and s/cat"
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["bar" 10]}}
           (assoc-value-in targets3 [::targets :target-dims :the-cat 0] "bar")))
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["bar" 10]}}
           (assoc-value-in targets3 [::targets :target-dims :the-cat :a] "bar")))
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["foo" 20]}}
           (assoc-value-in targets3 [::targets :target-dims :the-cat 1] 20)))
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["foo" 20]}}
           (assoc-value-in targets3 [::targets :target-dims :the-cat :b] 20)))
    (is (thrown? Exception (assoc-value-in targets3 [::targets :target-dims :the-cat 2] 555)))
    (is (thrown? Exception (assoc-value-in targets3 [::targets :target-dims :the-cat :c] 555))))

  (testing "for s/and and s/or"
    (is (= {:filename    "foo"
            :target-dims
            {:dims ["foo" "bar"]
             :deeper1
             {:deeper2
              {:deeper2
               {:deeper3 "not too much"}}}}}
           (assoc-value-in
            {:filename    "foo"
             :target-dims
             {:dims ["foo" "bar"]
              :deeper1
              {:deeper2
               {:deeper2
                {:deeper3 "pretty deep"}}}}}
            [::targets :target-dims :deeper1 :deeper2 :deeper2 :deeper3]
            "not too much")))
    (is (= {:filename    "foo"
            :target-dims
            {:dims ["foo" "bar"]
             :deeper1
             {:deeper3 "not too much"}}}
           (assoc-value-in
            {:filename    "foo"
             :target-dims
             {:dims ["foo" "bar"]
              :deeper1
              {:deeper3 "pretty deep"}}}
            [::targets :target-dims :deeper1 :deeper3]
            "not too much"))))

  (testing "for specs that refer to other specs"
    (is (= {:deeper3 "baz", :filename "bar", :the-cat ["foo" 4]}
           (assoc-value-in other-value [::other :deeper3] "baz")))
    (is (= {:deeper3 "foo", :filename "boo", :the-cat ["foo" 4]}
           (assoc-value-in other-value [::other :filename] "boo")))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["bar" 4]}
           (assoc-value-in other-value [::other :the-cat 0] "bar")))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["foo" 10]}
           (assoc-value-in other-value [::other :the-cat 1] 10)))

    (is (= {:deeper3 "baz", :filename "bar", :the-cat ["foo" 4]}
           (assoc-value-in other-value [::other2 :deeper3] "baz")))
    (is (= {:deeper3 "foo", :filename "boo", :the-cat ["foo" 4]}
           (assoc-value-in other-value [::other2 :filename] "boo")))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["bar" 4]}
           (assoc-value-in other-value [::other2 :the-cat 0] "bar")))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["foo" 10]}
           (assoc-value-in other-value [::other2 :the-cat 1] 10)))))

(deftest update-value-in-test
  (testing "for s/keys"
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar" "baz"]}}
           (update-value-in targets [::targets :target-dims :dims] conj "baz")))
    (is (thrown? Exception (update-value-in targets [::targets :target-dims :dims] conj 10)))
    (is (thrown? Exception (update-value-in targets [::targets :WRONG] 10)))
    (is (thrown? Exception (update-value-in targets [::targets :target-dims :WRONG] 10))))

  (testing "for s/keys and s/cat"
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["foobar" 10]}}
           (update-value-in targets3 [::targets :target-dims :the-cat 0] str "bar")))
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["foobar" 10]}}
           (update-value-in targets3 [::targets :target-dims :the-cat :a] str "bar")))
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["foo" 11]}}
           (update-value-in targets3 [::targets :target-dims :the-cat 1] inc)))
    (is (= {:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["foo" 11]}}
           (update-value-in targets3 [::targets :target-dims :the-cat :b] inc)))
    (is (thrown? Exception (update-value-in targets3 [::targets :target-dims :the-cat 2] inc)))
    (is (thrown? Exception (update-value-in targets3 [::targets :target-dims :the-cat :c] inc))))

  (testing "for specs that refer to other specs"
    (is (= {:deeper3 "foobar", :filename "bar", :the-cat ["foo" 4]}
           (update-value-in other-value [::other :deeper3] #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "barbar", :the-cat ["foo" 4]}
           (update-value-in other-value [::other :filename] #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["foobar" 4]}
           (update-value-in other-value [::other :the-cat 0] #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["foo" 5]}
           (update-value-in other-value [::other :the-cat 1] inc)))

    (is (= {:deeper3 "foobar", :filename "bar", :the-cat ["foo" 4]}
           (update-value-in other-value [::other2 :deeper3] #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "barbar", :the-cat ["foo" 4]}
           (update-value-in other-value [::other2 :filename] #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["foobar" 4]}
           (update-value-in other-value [::other2 :the-cat 0] #(str % "bar"))))
    (is (= {:deeper3 "foo", :filename "bar", :the-cat ["foo" 5]}
           (update-value-in other-value [::other2 :the-cat 1] inc)))))

(deftest compose-test
  (is (= [:spectacles.impl-test/targets :target-dims :deeper1 :deeper2 :deeper3]
         (compose [::targets :target-dims :deeper1] [::deeper1 :deeper2 :deeper3])))
  (is (= [:spectacles.impl-test/targets :target-dims :deeper1 :deeper2 :deeper3]
         (compose [::targets :target-dims] [::target-dims :deeper1 :deeper2 :deeper3])))
  (is (thrown? Exception (compose [::targets :target-dims :deeper1] [::deeper2 :deeper3]))))
