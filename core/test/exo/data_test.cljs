(ns exo.data-test
  (:require
   [clojure.test :as t]
   [exo.data :as l.d]))


(defn spy
  []
  (let [calls (atom {:count 0})]
    [calls #(doto calls
              (swap! update :count inc)
              (swap! assoc :data %))]))


(t/deftest query-subscribe
  (t/testing "query without entities updates when data is added"
    (let [dc (l.d/data-cache)
          query [{:foo [:bar]}]
          test-data {:foo {:bar "baz"}}
          [calls f] (spy)
          [data unsub] (l.d/subscribe! dc query f)]
      (t/is (= {} data))
      (t/is (= 0 (:count @calls)))
      (l.d/add-data! dc query test-data)
      (t/is (= 1 (:count @calls)))
      (t/is (= test-data (:data @calls)))
      (t/testing "doesn't call f after unsub"
        (unsub)
        (l.d/add-data! dc query (assoc-in test-data [:foo :bar] "asdf"))
        (t/is (= 1 (:count @calls)))
        (t/is (= test-data (:data @calls))))))

  (t/testing "2 queries sharing entities both update when data is added for one"
    (let [dc (l.d/data-cache)
          query1 [{:foo [:bar/id :baz]}]
          query2 [{:asdf [:bar/id :baz :jkl]}]
          test-data1 {:foo {:bar/id 0 :baz 42}}
          test-data2 {:asdf {:bar/id 0 :baz 42 :jkl "qwerty"}}
          test-data3 {:foo {:bar/id 0 :baz 100}}
          test-data4 {:asdf {:bar/id 0 :baz 100 :jkl "uiop"}}
          [query1-calls query1-f] (spy)
          [query2-calls query2-f] (spy)
          [data1 unsub1] (l.d/subscribe! dc query1 query1-f)
          [data2 unsub2] (l.d/subscribe! dc query2 query2-f)]
      (t/is (= {} data1))
      (t/is (= {} data2))
      (t/is (= 0 (:count @query1-calls)))
      (t/is (= 0 (:count @query2-calls)))

      ;; add query1-data
      (l.d/add-data! dc query1 test-data1)
      (t/is (= 1 (:count @query1-calls)))
      (t/is (= test-data1 (:data @query1-calls)))
      (t/is (= 0 (:count @query2-calls)))
      ;; query2-f hasn't been called yet
      (t/is (= nil (:data @query2-calls)))

      ;; add query2-data
      (l.d/add-data! dc query2 test-data2)
      ;; query1-f isn't called since pull result is equal
      (t/is (= 1 (:count @query1-calls)))
      ;; still equal to test-data1, because it's the same attr values as test-data2
      (t/is (= test-data1 (:data @query1-calls)))
      ;; did see changes in query2
      (t/is (= 1 (:count @query2-calls)))
      (t/is (= test-data2 (:data @query2-calls)))

      ;; add new query1-data
      (l.d/add-data! dc query2 test-data3)
      (t/is (= 2 (:count @query1-calls)))
      (t/is (= test-data3 (:data @query1-calls)))
      (t/is (= 2 (:count @query2-calls)))
      (t/is (= (assoc-in test-data2 [:asdf :baz] 100)
               (:data @query2-calls)))

      ;; add new query2-data
      (l.d/add-data! dc query2 test-data4)
      ;; query1 result is same again so isn't called and data is unchanged
      (t/is (= 2 (:count @query1-calls)))
      (t/is (= test-data3 (:data @query1-calls)))
      ;; did see changes in query2
      (t/is (= 3 (:count @query2-calls)))
      (t/is (= test-data4
               (:data @query2-calls)))

      (t/testing "doesn't call f after unsub"
        (unsub1)
        (l.d/add-data! dc query2 test-data2)
        (t/is (= 2 (:count @query1-calls)) "query1-f isn't called after unsub")
        (t/is (= test-data3 (:data @query1-calls)))

        (t/is (= 4 (:count @query2-calls)))
        (t/is (= test-data2 (:data @query2-calls)))

        (unsub2)

        (t/is (empty? (.-query-watches dc)) "cleans up query-watches")
        (t/is (empty? (get (.-entity->queries dc) [:bar/id 0]))
              "cleans up entity->queries relations")))))


(t/deftest cache-eviction)


(comment
  (t/run-tests)
  )
