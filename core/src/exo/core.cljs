(ns exo.core
  (:require
   [clojure.string :as string]
   [exo.data :as data]
   [exo.network.core :as net]
   [edn-query-language.core :as eql]
   [clojure.zip :as zip]))


(defn create-config
  "Creates a new config map with default options."
  [{:keys [request-store
           data-cache
           network]
    :or {request-store (net/request-store)
         data-cache (data/data-cache)}}]
  {:request-store request-store
   :data-cache data-cache
   :network network})


(defn load-query!
  "Loads a query from the network, storing the state of the request and, on
  completion, the resulting data in the cache.

  Returns a ref containing the current state of the request."
  ([config query] (load-query! config query {}))
  ([{:keys [request-store
            data-cache
            network]
     :as _config}
    query
    opts]
   (let [*stored-req (get-in @request-store [query opts])
         *req (if (and (some? *stored-req) (net/loading? @*stored-req))
                ;; we already have a loading request during this tick, don't
                ;; start a new one
                *stored-req
                (-> query
                    #_(eql/query->ast)
                    (network opts)
                    (net/-then
                     (fn [data]
                       (data/add-data! data-cache query data)
                       data))
                    (net/request->ref)
                    (doto (->>
                           (swap! request-store assoc-in [query opts])))))]
     *req)))


(defn preload!
  "Loads a query from the network, storing the state of the request and, on
  completion, the resulting data in the cache."
  ([config query]
   (preload! config query {}))
  ([config query opts]
   (load-query! config query opts)
   js/undefined))


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
   (let [outer-key (gensym "exo-req-store")
         inner-key (gensym "exo-req-status")
         request-store (:request-store config)
         inner-req (get-in @request-store [query opts])
         ;; need to store current req to unsub
         current-req (atom inner-req)]

     ;; subscribe to the request immediately if available
     (when inner-req
       (add-watch
        inner-req inner-key
        (fn [_ _ _ n]
          (f (:status n)))))

     ;; watch for new requests added for this query
     (add-watch
      request-store outer-key
      (fn [_ _ o n]
        (let [old-req (get-in o [query opts])
              new-req (get-in n [query opts])]
          (when (not= old-req new-req)
            (f (:status @new-req))
            #_(when old-req (remove-watch old-req inner-key))
            (add-watch
             new-req
             inner-key
             (fn [_ _ _ n]
               (f (:status n))))
            ;; update ref to current req for unsub
            (reset! current-req new-req)))))
     (fn unsub-status []
       (remove-watch request-store outer-key)
       (when-let [*req @current-req]
         (remove-watch *req inner-key))))))


(defn- make-node
  [node children]
  (if (map-entry? node)
    (into [] children)
    (into (empty node) (if (list? node)
                         (reverse children) ; grumble
                         children))))


(defn- query-zipper
  [query]
  (zip/zipper coll? seq make-node query))


(defn- query-var?
  [x]
  (and
   (symbol? x)
   (string/starts-with? (str x) "?")))


(defn- resolve-query-var
  [qv bindings]
  (get bindings (-> (str qv) (subs 1) (keyword))))


(defn parameterize-query
  [query bindings]
  (loop [loc (query-zipper query)]
    (if (zip/end? loc)
      (zip/root loc)
      (let [node (zip/node loc)]
        (recur
         (cond-> loc
           (query-var? node)
           (zip/edit resolve-query-var bindings)

           true (zip/next)))))))
