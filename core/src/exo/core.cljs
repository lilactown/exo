(ns exo.core
  (:require
   [exo.data :as data]
   [exo.network.core :as net]))


(defn create-config
  "Creates a new config map with default options."
  [{:keys [request-store
           data-cache
           network]
    :or {request-store (net/request-store)
         data-cache (data/data-cache)}}]
  {:request-store request-store
   :data-cache data-cache
   :network (net/compose-network-fns network)})


(defn load-data!
  "Loads a query from the network, storing the state of the request and, on
  completion, the resulting data in the cache.

  Returns a ref containing the current state of the request."
  ([config query] (load-data! config query {}))
  ([{:keys [request-store
            data-cache
            network]
     :as _config}
    query
    opts]
   (let [*stored-req (get request-store [query opts])
         *req (if (and (some? *stored-req) (net/pending? @*stored-req))
                ;; we already have a pending request during this tick, don't
                ;; start a new one
                *stored-req
                (-> query
                    (network opts)
                    (doto (net/-then
                           (fn [data]
                             (data/add-data! data-cache query data))))
                    (net/request->ref)))]
     (swap! request-store assoc-in [query opts] *req)
     *req)))


(defn preload!
  [config query opts]
  (load-data! config query opts)
  nil)
