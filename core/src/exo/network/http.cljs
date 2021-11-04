(ns exo.network.http
  (:require
   [goog.object :as gobj]
   [lambdaisland.fetch :as fetch]))


(defn link
  [{:keys [default-body-handler]
    :or {default-body-handler identity}}]
  (fn [query-ast {:keys []}]
    (let [{:keys [children]} query-ast
          child (first children)]
      (when (and
             (= 1 (count children))
             (= :http/req (:key child)))
        (let [{:keys [uri content-type] :as params} (:params child)]
          (-> (fetch/request uri
                             {:content-type content-type
                              :accept content-type })
              (.then
               (fn [{:keys [body]}]
                 (js/console.log body)
                 {(list (:key child) (:params child))
                  (default-body-handler params body)}))))))))


(comment
  (fetch/request
   "https://pokeapi.co/api/v2/pokemon/35/"
   :content-type :json
   :accept :json)
  )


(defn transform-keys
  "Walks a tree of nested data, transforming map keys as it goes by passing the
  key path it took to get there (including the key) and the map containing the
  key to `key-fn`.

  If `key-fn` is a Clojure collection, it will only pass the current path
  `key-fn` to it. If `key-fn` returns nil, it leaves the key as is.

  When passed it a JS tree, it will convert it to CLJS data as it transforms the
  keys, acting like a super-powered js->clj."
  ([x key-fn] (transform-keys x key-fn [] nil))
  ([x key-fn path parent]
   (cond
     (satisfies? IEncodeClojure x)
      (transform-keys
       (-js->clj x {})
       key-fn
       path
       x)

     (map-entry? x)
     (let [ks (nth x 0)
           path' (conj path ks)]
       (prn path')
       [(if (associative? key-fn)
          (get key-fn path' ks)
          (or (key-fn path' parent) ks))
        (transform-keys (nth x 1) key-fn path' x)])

     (object? x)
      (->> (js-keys x)
           (reduce
            (fn [m k]
              (let [path' (conj path k)]
                (assoc!
                 m
                 (if (associative? key-fn)
                   (get key-fn path' k)
                   (or (key-fn path' x) k))
                 (transform-keys
                  (gobj/get x k)
                  key-fn
                  path'
                  x))))
            (transient {}))
           (persistent!))

      (array? x)
      (into [] (map #(transform-keys % key-fn path x)) x)

     (seq? x)
     (doall (map #(transform-keys % key-fn path x) x))

     (coll? x)
     (into (empty x) (map #(transform-keys % key-fn path x)) x)

     :else x)))
