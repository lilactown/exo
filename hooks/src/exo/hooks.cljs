(ns exo.hooks
  (:require
   ["react" :as r]
   ["use-sync-external-store/shim" :refer [useSyncExternalStore]]
   [exo.core :as exo]
   [exo.data]
   [exo.network.core :as net]
   [goog.object :as gobj]))


(defonce exo-config-context (r/createContext))


(defn provider
  [props]
  (r/createElement
   (gobj/get exo-config-context "Provider")
   #js {:value (gobj/get props "config")}
   (gobj/get props "children")))


(defn use-query
  ([query] (use-query query nil))
  ([query {:keys [enabled?]
           :or {enabled? true}}]
   (let [config (r/useContext exo-config-context)
         query-hash (hash query)
         ;; we need to provide a synchronous, unscoped way of getting the results
         ;; of a query, rather than relying on subscribe! to pass the value to
         ;; some callback.
         ;; we use a ref as that store in lieu of a way to just get a memoized
         ;; result of a query from the exo data-cache
         ;; TODO cache whole result in data-cache so that we don't rely on pull
         ^js results-store (r/useRef
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
                                    (when (not= (.-current results-store) result)
                                      (set! (.-current results-store) result))
                                    (cb)))]
              ;; we may have subscribed before initiating getting data,
              ;; so if we have results put them in the store now
              (when (not= (.-current results-store) result)
                (set! (.-current results-store) result))
              unsub))
          #js [query-hash config])

         subscribe-status (r/useCallback
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

         load (r/useCallback
               #(do (exo/preload! config query) js/undefined)
               #js [query-hash config])]
     (r/useEffect
      #(when enabled?
         (load))
      #js [load enabled?])
     {:data data
      :status status
      :loading? (= net/loading status)
      :refetch load})))


(defn use-deferred-query
  ([query] (use-deferred-query query nil))
  ([query opts]
   (let [result (use-query query opts)
         ^js prev-data (r/useRef (:data result))]
     (r/useEffect
      (fn []
        (when-not (:loading? result)
          (set! (.-current prev-data) (:data result)))
        js/undefined)
      #js [(:loading? result) (:data result)])
     (if (:loading? result)
       (assoc result :data (.-current prev-data))
       result))))


(defn use-config
  []
  (r/useContext exo-config-context))
