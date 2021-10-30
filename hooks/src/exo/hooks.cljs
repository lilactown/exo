(ns exo.hooks
  (:require
   ["react" :as r]
   ["./useSyncExternalStore.js" :refer [useSyncExternalStore]]
   [exo.core :as exo]
   [exo.data]))


(defn- use-stable-reference
  [value]
  (let [^js last-value (r/useRef value)
        ret-value (if (not= (.-current last-value) value)
                    value
                    (.-current last-value))]
    (r/useEffect
     #(set! (.-current last-value) ret-value)
     #js [ret-value])
    ret-value))


(defonce exo-config-context (r/createContext))


(defn use-preloaded-query
  [query]
  (let [stable-query (use-stable-reference query)
        config (r/useContext exo-config-context)
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
                          stable-query)
                        #js []))
        subscribe-data
        (r/useCallback
         (fn [cb]
           (let [[result unsub] (exo.data/subscribe!
                                 (:data-cache config) stable-query
                                 (fn [result]
                                   (set! (.-current results-store) result)
                                   (cb)))]
             ;; we may have subscribed before initiating getting data,
             ;; so if we have results put them in the store now
             (set! (.-current results-store) result)
             unsub))
         #js [stable-query config])

        subscribe-status
        (r/useCallback
         (fn [cb]
           (exo/subscribe-status!
            config stable-query
            cb))
         #js [stable-query config])

        data #_{:people [{:person/id 0 :person/name "Rachel"}]}
        (useSyncExternalStore
              subscribe-data
              #(.-current results-store))
        status (useSyncExternalStore
                subscribe-status
                #(exo/current-status config query))]
    #js [data status]))
