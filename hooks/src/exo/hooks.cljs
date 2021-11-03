(ns exo.hooks
  (:require
   ["react" :as r]
   ["./useSyncExternalStore.js" :refer [useSyncExternalStore]]
   [exo.core :as exo]
   [exo.data]))


(defonce exo-config-context (r/createContext))


(defn use-query
  [query]
  (let [config (r/useContext exo-config-context)
        query-hash (hash query)
        ;; we need to provide a synchronous, unscoped way of getting the results
        ;; of a query, rather than relying on subscribe! to pass the value to
        ;; some callback.
        ;; we use a ref as that store in lieu of a way to just get a memoized
        ;; result of a query from the exo data-cache
        ;; TODO cache whole result in data-cache so that we don't rely on pull
        results-store (r/useRef
                       ;; memoize this on mount because it's expensive
                       (r/useMemo
                        #(exo.data/pull
                          (:data-cache config)
                          query)
                        #js []))
        subscribe-data
        (r/useCallback
         (fn [cb]
           (let [[result unsub] (exo.data/subscribe!
                                 (:data-cache config) query
                                 (fn [result]
                                   (set! (.-current results-store) result)
                                   (cb)))]
             ;; we may have subscribed before initiating getting data,
             ;; so if we have results put them in the store now
             (set! (.-current results-store) result)
             unsub))
         #js [query-hash config])

        subscribe-status
        (r/useCallback
         (fn [cb]
           (exo/subscribe-status!
            config query
            cb))
         #js [query-hash config])

        data (useSyncExternalStore
              subscribe-data
              #(.-current results-store))

        status (useSyncExternalStore
                subscribe-status
                #(exo/current-status config query))

        refetch (r/useCallback
                 #(exo/preload! config query)
                 #js [(hash query) config])]
    (r/useEffect
     (fn []
       (refetch)
       js/undefined)
     #js [refetch])
    {:data data
     :status status
     :loading? (= :pending status)
     :refetch refetch}))


(defn use-config
  []
  (r/useContext exo-config-context))