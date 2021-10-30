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
  ([config query]
   (load-data! config query))
  ([config query opts]
   (load-data! config query opts)
   nil))


(defn current-status
  ([config query] (current-status config query {}))
  ([config query opts]
   (-> (:request-store config)
       (deref)
       (get-in [query opts])
       (some-> (deref)
               (:status)))))


(defn subscribe-status!
  ([config query f]
   (subscribe-status! config query {} f))
  ([config query opts f]
   (let [k (gensym "exo-status")
         ;; need to store current req to unsub
         current-req (atom nil)
         request-store (:request-store config)]
     (add-watch
      request-store k
      (fn [_ _ o n]
        (let [old-req (get-in o [query opts])
              new-req (get-in n [query opts])]
          (when (not= old-req new-req)
            (f (:status @new-req))
            (add-watch
             new-req
             k
             (fn [_ _ _ n]
               (f (:status n))))
            (reset! current-req new-req)))))
     (fn unsub-status []
       (when-let [*req @current-req]
         (remove-watch *req k)
         (remove-watch request-store k))))))
