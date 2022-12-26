(ns exo.data
  (:require
   [pyramid.core :as p]
   [clojure.set :as c.set]))


(defprotocol IDataCache
  (-add-query-watch [o query f]
    "Runs `f` anytime the results of `query` change.
Returns the result of running `query`")
  (-update-query-entities [o query entities]
    "Updates the set of entities that `queries` contains, to handle notifying
watchers of `query` when `entities` change.")
  (-delete-entity [o lookup-ref])
  (-notify-watches! [o query entities indices]
    "Notifies any watches of `query`, and any watches of queries that contain
any of `entities` or `indices`, of changes.")
  (-remove-query-watch [o query f]
    "Removes `f` from watches on `query`. Cleans up `query` completely if it's
the last watcher."))


(defn update!
  ([tcoll key f x]
   (assoc! tcoll key (f (get tcoll key) x)))
  ([tcoll key f x y & xs]
   (assoc! tcoll key (apply f (get tcoll key) x y xs))))


(def conj-set
  (fnil conj #{}))


(deftype DataCache [cache
                    query-watches
                    entity->queries
                    index->queries
                    opts]
  Object
  (equiv [this other]
    (-equiv this other))

  IEquiv
  (-equiv [o other]
    (identical? o other))

  IDeref
  (-deref [_] cache)

  IDataCache
  (-add-query-watch [this query f]
    (let [{:keys [data entities indices]} (p/pull-report cache query)
          entity->queries' (transient entity->queries)
          index->queries' (transient index->queries)]
      ;; add entity => query
      (doseq [entity entities]
        (update! entity->queries' entity conj-set query))
      (set! (.-entity->queries this) (persistent! entity->queries'))
      (doseq [index indices]
        (update! index->queries' index conj-set query))
      (set! (.-index->queries this) (persistent! index->queries'))
      ;; TODO should we notify query listeners of changes to `entities`?
      ;; add query => entities, f
      (set! (.-query-watches this)
            (-> query-watches
                (assoc-in [query :entities] entities)
                (update-in [query :fs] conj-set f)
                (assoc-in [query :indices] indices)))
      data))

  (-remove-query-watch [this query f]
    (let [{:keys [entities fs indices]} (get query-watches query)
          fs' (disj fs f)]
      (if (seq fs')
        (set! (.-query-watches this) (assoc-in query-watches [query :fs] fs'))
        ;; if we have no more fs watching query, remove query from entities
        ;; and then clear out query from query-watches completely to avoid
        ;; memory leak
        (let [entity->queries' (transient entity->queries)
              index->queries' (transient index->queries)]
          (doseq [entity entities]
            (update! entity->queries' entity disj query))
          (doseq [index indices]
            (update! index->queries' index disj query))
          (set! (.-entity->queries this) (persistent! entity->queries'))
          (set! (.-index->queries this) (persistent! index->queries'))
          (set! (.-query-watches this) (dissoc query-watches query))))))

  (-update-query-entities [this query new-entities]
    ;; look up query => entities, fs
    ;; set difference new-entities, old-entities
    ;; disj query from rm-entities
    ;; conj query to new-entities
    (let [{:keys [entities]} (get query-watches query)
          rm-entities (c.set/difference entities new-entities)
          add-entities (c.set/difference new-entities entities)]
      ;; see if we have work to do
      (when (or (seq rm-entities) (seq add-entities))
        (let [entity->queries' (transient entity->queries)]
          (doseq [entity rm-entities]
            (update! entity->queries' entity disj query))
          (doseq [entity add-entities]
            (update! entity->queries' entity conj-set query))
          (set! (.-entity->queries this) (persistent! entity->queries'))
          (set!
           (.-query-watches this)
           (assoc-in query-watches [query :entities] new-entities))))))

  (-delete-entity [this lookup-ref]
    (let [cache' (p/delete cache lookup-ref)
          query-watches' (update-vals query-watches
                                      (fn [m]
                                        (update m :entities disj lookup-ref)))
          entity->queries' (dissoc entity->queries lookup-ref)
          queries (get entity->queries lookup-ref)]
      (set! (.-cache this) cache')
      (set! (.-query-watches this) query-watches')
      (set! (.-entity->queries this) entity->queries')
      queries))

  (-notify-watches! [_ query entities indices]
    (let [entity-fs (into
                     (get-in query-watches [query :fs])
                     (comp
                      (mapcat #(get entity->queries %))
                      (mapcat #(get-in query-watches [% :fs])))
                     entities)
          all-fs (into
                  entity-fs
                  (comp
                   (mapcat #(get index->queries %))
                   (mapcat #(get-in query-watches [% :fs])))
                  indices)]
      ;; TODO we rely on `f` to pull the data here. we could speed this up
      ;; by pulling here instead and calling each `f` for each query, so we
      ;; only pull each query once (in the case where multiple `f`s are watching
      ;; the same query)
      (doseq [f all-fs]
        (f cache)))))


(defn data-cache
  ([] (data-cache (p/db []) {}))
  ([db opts] (->DataCache db {} {} {} opts)))


(defn add-data!
  "Adds new data to the normalized cache, and notifies anyone listening to the
  entities the data pertains of changes."
  [^IDataCache dc query data]
  (let [old-state (.-cache dc)
        {:keys [db entities indices]} (p/add-report old-state data)]
    (set! (.-cache dc) db)
    (when-not (nil? (get-in (.-query-watches dc) [query :fs]))
      (-update-query-entities dc query entities)
      (-notify-watches! dc query entities indices))
    db))


(defn delete-entity!
  "Deletes an entity, removing it from the cache and updating all watches
  associated with it."
  [^IDataCache dc lookup-ref]
  (let [queries (-delete-entity dc lookup-ref)]
    (doseq [query queries]
      (-notify-watches! dc query nil nil))
    @dc))


(defn pull
  [dc query]
  (p/pull @dc query))


(defn subscribe!
  "Returns a tuple `[initial-data unsubscribe]`"
  [dc query f]
  (let [prev-data (atom nil)
        f' (fn [cache]
             (let [{:keys [data entities]} (p/pull-report cache query)]
               (when (not= @prev-data data)
                 (reset! prev-data data)
                 (-update-query-entities dc query entities)
                 (f data))))
        unsub #(-remove-query-watch dc query f')]
    [(-add-query-watch dc query f') unsub]))
