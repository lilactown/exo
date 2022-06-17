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
    (let [dc (l.d/data-cache {} (atom {}) {})
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
    (let [dc (l.d/data-cache {} (atom {}) {})
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
    (let [dc (l.d/data-cache {} (atom {}) {})
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


(defn eventually
  [f timeout]
  (let [deadline (+ (js/performance.now) timeout)
        cb (fn cb [res rej]
             (let [ret (f)]
               (cond
                 ret (res ret)
                 (>= (js/performance.now) deadline) (rej (ex-info "Timeout" {}))
                 ;; do a macrotask
                 :else (js/setTimeout #(cb res rej)))))]
    (js/Promise. cb)))


#_(-> (eventually
     (let [count (atom 0)]
       (fn []
         (swap! count inc)
         (when (= 100 @count)
           true)))
     1000)
    (.then #(prn :success %))
    (.catch #(prn :error %)))


(t/deftest cache-eviction
  (t/testing "adds entity to queue"
    (let [janitor (atom {})
          dc (l.d/data-cache {} janitor {})
          query [{:foo [:id :bar]}]
          test-data {:foo [{:id 1 :bar "baz" :asdf "jkl"}
                           {:id 2 :bar "qux" :asdf "qwerty"}]}
          [_calls f] (spy)
          [_data unsub] (l.d/subscribe! dc query f)]
      (l.d/add-data! dc query test-data)
      (t/is (empty? @janitor))
      (unsub)
      (t/is (get @janitor [:id 1]))
      (t/is (get @janitor [:id 2]))))
  (t/testing "sweep"
    (t/async
     done
     (let [janitor (l.d/janitor)
           time-to-keep 500
           dc (l.d/data-cache {} janitor {:janitor/time-to-keep time-to-keep})
           query [{:foo [:id :bar]}]
           test-data {:foo [{:id 1 :bar "baz" :asdf "jkl"}
                            {:id 2 :bar "qux" :asdf "qwerty"}]}
           [_calls f] (spy)
           [_data unsub] (l.d/subscribe! dc query f)
           min-wait (+ time-to-keep (js/performance.now))]
       (l.d/add-data! dc query test-data)
       (unsub)
       (t/is (get-in @dc [:id 1]))
       (t/is (get-in @dc [:id 2]))
       (-> (eventually
            #(and
              (not (get-in @dc [:id 1]))
              (not (get-in @dc [:id 2]))
              (empty? (get @dc :foo)))
            (* time-to-keep 2))
           (.then #(t/is (>= (js/performance.now) min-wait)
                         "cleanup")
                  #(t/is false "cleanup"))
           (.then done done))))))


(t/deftest cancel-cache-eviction
  (t/async
   done
   (let [janitor (l.d/janitor)
         time-to-keep 500
         dc (l.d/data-cache {} janitor {:janitor/time-to-keep time-to-keep})
         query [{:foo [:id :bar]}]
         test-data {:foo [{:id 1 :bar "baz" :asdf "jkl"}
                          {:id 2 :bar "qux" :asdf "qwerty"}]}
         [_calls f] (spy)
         [_data unsub] (l.d/subscribe! dc query f)
         min-wait (+ time-to-keep (js/performance.now))]
     (l.d/add-data! dc query test-data)
     (unsub)
     ;; re-subscribe some time in the future before the time-to-keep is over
     (js/setTimeout
        (fn []
          (l.d/subscribe! dc query f))
        (/ time-to-keep 2))
     (-> (eventually
          #(and
            (not (get-in @dc [:id 1]))
            (not (get-in @dc [:id 2]))
            (empty? (get @dc :foo)))
          (* time-to-keep 2))
         (.then #(do (t/is false "canceled cleanup")
                     (t/is (empty? @janitor)))
                #(do (t/is true "canceled cleanup")
                     (t/is (empty? @janitor))))
         (.then done done)))))


(comment
  (t/run-tests)
  )
