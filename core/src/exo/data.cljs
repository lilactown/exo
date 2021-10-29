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
  (-notify-entity-watches! [o query entities]
    "Notifies any watches of `query`, and any watches of queries that contain
any of `entities`, of changes.")
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


;; TODO delete data / cache eviction
(deftype DataCache [state query-watches entity->queries]
  Object
  (equiv [this other]
    (-equiv this other))

  IEquiv
  (-equiv [o other]
    (identical? o other))

  IDeref
  (-deref [_] state)

  IDataCache
  (-add-query-watch [this query f]
    (let [{:keys [data entities]} (p/pull-report state query)
          entity->queries' (transient entity->queries)]
      ;; add entity => query
      (doseq [entity entities]
        (update! entity->queries' entity conj-set query))
      (set! (.-entity->queries this) (persistent! entity->queries'))
      ;; TODO should we notify query listeners of changes to `entities`?
      ;; add query => entities, f
      (set! (.-query-watches this)
            (-> query-watches
                (assoc-in [query :entities] entities)
                (update-in [query :fs] conj-set f)))
      data))

  (-remove-query-watch [this query f]
    (let [{:keys [entities fs]} (get query-watches query)
          fs' (disj fs f)]
      (if (seq fs')
        (set! (.-query-watches this) (assoc-in query-watches [query :fs] fs'))
        ;; if we have no more fs watching query, remove query from entities
        ;; and then clear out query from query-watches completely to avoid
        ;; memory leak
        (let [entity->queries' (transient entity->queries)]
          (doseq [entity entities]
            (update! entity->queries' entity disj query))
          (set! (.-entity->queries this) (persistent! entity->queries'))
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
           (assoc-in query-watches [query :entities] new-entities)))) ))

  (-notify-entity-watches! [_ query entities]
    (let [all-fs (into
                  (get-in query-watches [query :fs])
                  (comp
                   (mapcat #(get entity->queries %))
                   (mapcat #(get-in query-watches [% :fs])))
                  entities)]
      ;; TODO we rely on `f` to pull the data here. we could speed this up
      ;; by pulling here instead and calling each `f` for each query, so we
      ;; only pull each query once (in the case where multiple `f`s are watching
      ;; the same query)
      (doseq [f all-fs]
        (f state)))))


(defn data-cache
  ([] (data-cache (p/db [])))
  ([db] (->DataCache db {} {})))


(defn add-data!
  "Adds new data to the normalized cache, and notifies anyone listening to the
  entities the data pertains of changes."
  [dc query data]
  (let [old-state (.-state dc)
        {:keys [db entities]} (p/add-report old-state data)]
    (set! (.-state dc) db)
    (when-not (nil? (get-in (.-query-watches dc) [query :fs]))
      (-update-query-entities dc query entities)
      (-notify-entity-watches! dc query entities))
    db))


(defn subscribe!
  "Returns a tuple `[data unsubscribe]`"
  [dc query f]
  (let [prev-data (atom nil)
        f' (fn [state]
             (let [{:keys [data entities]} (p/pull-report state query)]
               (when (not= @prev-data data)
                 (reset! prev-data data)
                 (-update-query-entities dc query entities)
                 (f data))))
        unsub #(-remove-query-watch dc query f')]
    [(-add-query-watch dc query f') unsub]))
