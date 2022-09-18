(ns exo.mask
  (:require
   [pyramid.core :as p]
   [edn-query-language.core :as eql]))


(deftype Mask [id eql result])


(defn fragment
  ([eql] (fragment (gensym "fragment") eql))
  ([id eql]
   (with-meta eql {:component #(->Mask id eql %)
                   :fragment/id id
                   :fragment/eql eql})))


(comment
  (def db {:foo {:bar 123 :baz 456}})
  (def baz-fragment (fragment `Baz [:baz]))
  (def query [{:foo baz-fragment}])
  (def results (p/pull db query))

  (-> results :foo .-result)




  (def bar-baz-frag (fragment nil [:bar :baz]))

  (p/pull db [{:foo [:bar :baz]}])
  ;; => {:foo {:bar 123, :baz 456}}

  (p/pull db [{:foo bar-baz-frag}])
  ;; => {:foo #object[exo.mask.Mask]}
  )
