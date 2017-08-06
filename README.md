# spectacles

Lenses for Clojure, checked at runtime using spec. It provides `get`,
`get-in`, `assoc`, `assoc-in`, `update` and `update-in`, where keys,
paths and passed values are checked based on existing specs.

Since Clojure spec is still in alpha, this library should also be
considered to be in alpha -- so, highly experimental, very likely to
change, possibly flawed.

## Leiningen dependency

Use this with Leiningen:

`[spectacles "0.1.0"]`

## Basic Usage

Setup the scene with a few specs:

```clojure
(ns my-ns
  (:require [spectacles.lenses :as lens]
            [clojure.spec.alpha :as s]))


(s/def ::filename string?)
(s/def ::dims (s/coll-of string?))
(s/def ::target-dims (s/keys :req-un [::dims]
                             :opt-un [::the-cat]))
(s/def ::the-cat (s/cat :a string? :b number?))
(s/def ::targets (s/keys :req-un [::filename ::target-dims]))
```

...and some test data:

```clojure
(def targets {:filename "foo" :target-dims {:dims ["foo" "bar"]}})
```

You can then do:

```
> (lens/get targets ::targets :filename)

"foo"
```

The keys are checked against the `::targets` spec:

```clojure
> (lens/get targets ::targets :WRONG)

ExceptionInfo Invalid key :WRONG for spec :my-ns/targets (valid keys: #{:target-dims :filename})  clojure.core/ex-info (core.clj:4617)
```

Same goes for `get-in`:

```clojure
> (lens/get-in targets [::targets :target-dims :dims])

["foo" "bar"]
```

Note that the second argument of `get-in` is a vector whose first
element is the spec of the data structure and the rest of the vector
is the path into the key we are looking to retrieve. These two bit of
information are passed as a single vector so that you can define and
pass lenses as parameters by passing a single value.

`get-in` checks each key of the path against the root spec and also
deeper specs that it encounters:

```clojure
> (lens/get-in targets [::targets :target-dims :WRONG])

Unhandled clojure.lang.ExceptionInfo
 Invalid key :WRONG for spec :my-ns/target-dims
 (valid keys: #{:the-cat :dims})
 {:reason :invalid-key,
  :collection {:dims ["foo" "bar"]},
  :key :WRONG,
  :spec :my-ns/target-dims,
  :valid-keys #{:the-cat :dims},
  :path [:target-dims :WRONG],
  :root-map {:filename "foo", :target-dims {:dims ["foo" "bar"]}}}
```

You can also `assoc`, `assoc-in`, `update-in` values into data
structures, in which case the passed values are checked using spec
before updating the structure:

```clojure
> (lens/assoc-in targets [::targets :target-dims :dims] ["zoo" "far"])

{:filename "foo", :target-dims {:dims ["zoo" "far"]}}

> (lens/assoc-in targets [::targets :target-dims :dims] 10)

Invalid value 10 for key :dims in value {:dims ["foo" "bar"]}
  (should conform to: (clojure.spec.alpha/coll-of clojure.core/string?))

> (lens/update-in targets [::targets :target-dims :dims] conj 10)

ExceptionInfo Invalid value ["foo" "bar" 10] for key :dims in value {:dims ["foo" "bar"]}
  (should conform to: (clojure.spec.alpha/coll-of clojure.core/string?))

```

### Lenses pointing to `s/cat`

You can access values of structures spec'ed using `s/cat` by index or by name:

```clojure
> (lens/get ["foo" 10] ::the-cat 0)

"foo"

> (lens/get ["foo" 10] ::the-cat :a)

"foo"

> (def targets2
   {:filename "foo" :target-dims {:dims ["foo" "bar"] :the-cat ["foo" 10]}})

> (lens/update-in targets2 [::targets :target-dims :the-cat :a] str "bar")

{:filename "foo", :target-dims {:dims ["foo" "bar"], :the-cat ["foobar" 10]}}

> (lens/get-in targets2 [::targets :target-dims :the-cat 2])

Unhandled clojure.lang.ExceptionInfo
  Invalid key 2 for spec :spectacles.impl-test/the-cat (valid keys:
  #{0 1 :a :b})

```

## License

Copyright © 2017 Stathis Sideris

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.