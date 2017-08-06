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
(s/def ::target-dims (s/keys :req-un [::dims]
                             :opt-un [::the-cat ::simple-map]))
(s/def ::the-cat (s/cat :a string? :b number?))
(s/def ::targets (s/keys :req-un [::filename ::target-dims]))
(def targets {:filename "foo" :target-dims {:dims ["foo" "bar"]}})

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
      (is (thrown? Exception (get-value ["foo" 10] ::the-cat 3))))))

(def targets2
  {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10] :simple-map {"foo" 2 "bar" 3}}})

(deftest get-value-in-test
  (is (s/valid? ::targets targets2))

  (testing "for s/keys"
    (is (= ["foo" "bar"] (get-value-in targets [::targets :target-dims :dims])))
    (is (thrown? Exception (get-value-in targets [::targets :WRONG])))
    (is (thrown? Exception (get-value-in targets [::targets :target-dims :WRONG]))))

  (testing "for s/keys and s/cat"
    (is (= "foo" (get-value-in targets2 [::targets :target-dims :the-cat 0])))
    (is (= "foo" (get-value-in targets2 [::targets :target-dims :the-cat :a])))
    (is (= 10 (get-value-in targets2 [::targets :target-dims :the-cat 1])))
    (is (= 10 (get-value-in targets2 [::targets :target-dims :the-cat :b])))
    (is (thrown? Exception (get-value-in targets2 [::targets :target-dims :the-cat 2])))
    (is (thrown? Exception (get-value-in targets2 [::targets :target-dims :the-cat :c]))))

  (testing "for s/map-of"
    (is (= 2 (get-value-in targets2 [::targets :target-dims :simple-map "foo"])))
    (is (thrown? Exception (get-value-in targets2 [::targets :target-dims :simple-map 10])))))

(deftest update-value-test
  (is (= ["foobar" 10] (update-value ["foo" 10] ::the-cat :a #(str % "bar"))))
  (is (= ["foo" 11] (update-value ["foo" 10] ::the-cat :b inc))))

(def targets3
  {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10]}})

(deftest assoc-value-in-test
  (testing "for s/keys"
    (is (= {:filename "foo", :target-dims {:dims ["zoo" "far"]}}
           (assoc-value-in targets [::targets :target-dims :dims] ["zoo" "far"])))
    (is (thrown? Exception (assoc-value-in targets [::targets :target-dims :dims] 10)))
    (is (thrown? Exception (assoc-value-in targets [::targets :WRONG] 10)))
    (is (thrown? Exception (assoc-value-in targets [::targets :target-dims :WRONG] 10))))

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
    (is (thrown? Exception (assoc-value-in targets3 [::targets :target-dims :the-cat :c] 555)))))

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
    (is (thrown? Exception (update-value-in targets3 [::targets :target-dims :the-cat :c] inc)))))
