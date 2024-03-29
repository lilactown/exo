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
    (let [dc (l.d/data-cache {} {})
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
    (let [dc (l.d/data-cache {} {})
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
              "cleans up entity->queries relations"))))
  (t/testing "2 queries that share the same top-level index"
    (let [dc (l.d/data-cache {} {})
          query1 [{:foo [:id :bar]}]
          query2 [{:foo [:id :bar :asdf]}]
          test-dataA {:foo [{:id 1 :bar "baz" :asdf "jkl"}
                            {:id 2 :bar "qux" :asdf "qwerty"}]}
          test-dataB {:foo [{:id 1 :bar "baz" :asdf "jkl"}
                            {:id 2 :bar "qux" :asdf "qwerty"}
                            {:id 3 :bar "arst" :asdf "mneio"}]}
          [calls1 f1] (spy)
          [calls2 f2] (spy)
          [_data1 unsub1] (l.d/subscribe! dc query1 f1)
          [_data2 unsub2] (l.d/subscribe! dc query2 f2)]
      (l.d/add-data! dc query2 test-dataA)
      (t/is (= (:count @calls1) (:count @calls2))
            "same index gets updated even when no entities initially")
      (l.d/add-data! dc query1 test-dataB)
      (t/is (= (:count @calls1) (:count @calls2))
            "same index and same entities")
      (unsub1) (unsub2))))

(t/deftest delete-entity!
  (let [dc (l.d/data-cache {} {})
        query [{:foo [:id :bar]}]
        test-data {:foo [{:id 1 :bar "baz" :asdf "jkl"}
                          {:id 2 :bar "qux" :asdf "qwerty"}
                          {:id 3 :bar "arst" :asdf "mneio"}]}
        [calls f] (spy)
        [_data _unsub] (l.d/subscribe! dc query f)]
    (l.d/add-data! dc query test-data)
    (t/is (= 1 (:count @calls)))
    (l.d/delete-entity! dc [:id 2])
    (t/is (= {:id {1 {:id 1 :bar "baz" :asdf "jkl"}
                   3 {:id 3 :bar "arst" :asdf "mneio"}}
              :foo [[:id 1] [:id 3]]}
             @dc)
          "Data cache updated")
    (t/is (= {:entities #{[:id 1] [:id 3]}
              :indices #{:foo}}
             (dissoc (get (.-query-watches dc) query) :fs))
          "Query watches updated")
    (t/is (= {[:id 1] #{[{:foo [:id :bar]}]}
              [:id 3] #{[{:foo [:id :bar]}]}}
             (.-entity->queries dc))
          "Entity->queries updated")
    (t/is (= 2 (:count @calls)))
    (t/is (= {:foo [{:id 1 :bar "baz" }
                    {:id 3 :bar "arst"}]}
             (:data @calls)))))

(t/deftest evict-query!
  (t/testing "Single query"
    (let [dc (l.d/data-cache {} {})
          query [{:foo [:id :bar]}]
          test-data {:foo [{:id 1 :bar "baz" :asdf "jkl"}
                           {:id 2 :bar "qux" :asdf "qwerty"}
                           {:id 3 :bar "arst" :asdf "mneio"}]}
          [calls f] (spy)
          [_data _unsub] (l.d/subscribe! dc query f)]
      (l.d/add-data! dc query test-data)
      (t/is (= 1 (:count @calls)))
      (l.d/evict-query! dc query)
      (t/is (= {:id {}} @dc)
            "Data cache updated")
      (t/is (= {:entities #{} :indices #{}}
               (dissoc (get (.-query-watches dc) query) :fs))
            "Query watches updated")
      (t/is (= {}
               (.-entity->queries dc))
            "Entity->queries updated")
      (t/is (= 2 (:count @calls)))))
  (t/testing "Two queries that overlap entities"
    (let [dc (l.d/data-cache {} {})
          query1 [{:foo [:bar/id :baz]}]
          query2 [{:asdf [:bar/id :baz :jkl]}]
          test-data1 {:foo [{:bar/id 0 :baz 42}
                            {:bar/id 1 :baz 100}]}
          test-data2 {:asdf {:bar/id 0 :baz 42 :jkl "qwerty"}}
          [query1-calls query1-f] (spy)
          [query2-calls query2-f] (spy)
          [_data1 _unsub1] (l.d/subscribe! dc query1 query1-f)
          [_data2 _unsub2] (l.d/subscribe! dc query2 query2-f)]
      (l.d/add-data! dc query1 test-data1)
      (l.d/add-data! dc query2 test-data2)
      (t/is (= 1 (:count @query1-calls)))
      (t/is (= 1 (:count @query2-calls)))
      (t/is (= {:bar/id {0 {:bar/id 0 :baz 42 :jkl "qwerty"}
                         1 {:bar/id 1 :baz 100}}
                :asdf [:bar/id 0]
                :foo [[:bar/id 0] [:bar/id 1]]}
               @dc))
      (l.d/evict-query! dc query1)
      (t/is (= {:bar/id {0 {:bar/id 0 :baz 42 :jkl "qwerty"}}
                :asdf [:bar/id 0]}
               @dc))
      (t/is (= 2 (:count @query1-calls)))
      (t/is (= 1 (:count @query2-calls))))))


(comment
  (t/run-tests)
  )
