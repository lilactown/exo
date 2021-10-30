(ns exo.example.core
  (:require
   ["react" :as r]
   ["react-dom" :as rdom]
   [exo.core :as exo]
   [exo.hooks :as exo.hooks]
   [exo.example.data :as example.data]
   [helix.core :refer [defnc $ <>]]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(defonce exo-config
  (exo/create-config
   {:network [(fn people-link
                [query-ast _opts]
                (let [{:keys [children]} query-ast]
                  (when (and (= 1 (count children))
                             (= :people (-> children first :key)))
                    (.then (example.data/load-people)
                           (fn [people]
                             {:people people})))))
              (fn person-link
                [query-ast _opts]
                (let [{:keys [children]} query-ast
                      query-key (-> children first :key)
                      ;; is ident
                      matches? (and (vector? query-key)
                                    (= 1 (count children))
                                    (= :person/id (first query-key)))]
                  (when matches?
                    (-> (example.data/load-person (second query-key))
                        (.then (fn [person]
                                 {query-key person}))))))
              (fn best-friend-link
                [query-ast _opts]
                (let [{:keys [children]} query-ast
                      query-key (-> children first :key)
                      params (-> children first :params)
                      person-id (:person/id params)]
                  (when (and (= 1 (count children))
                             (= :best-friend query-key)
                             (some? person-id))
                    (-> person-id
                        (example.data/load-best-friend)
                        (.then (fn [data]
                                 {(list :best-friend {:person/id person-id})
                                  data}))))))]}))


(comment
  (-> exo-config
      (:data-cache)
      #_(deref)
      #_(pyramid.core/pull (exo/parameterize-query
                          best-friend-query
                          {:id 1}))
      (.-query-watches))

  (edn-query-language.core/query->ast
   (exo/parameterize-query
    best-friend-query
    {:id 1}))
  )





(def people-query
  [{:people [:person/id :person/name]}])


(def person-query
  '[{[:person/id ?id] [:person/name :person/species]}])


(def best-friend-query
  '[{(:best-friend {:person/id ?id}) [:person/id :person/name]}])


(defnc people-list
  [{:keys [on-navigate-details]}]
  (let [[data status] (exo.hooks/use-preloaded-query people-query)]
    (d/div
     ;; when we re-fetch, turn opacity down
     {:style {:opacity (if (= :pending status) 0.5 1)}}
     (d/h2 "People")
     (if (empty? (:people data)) ; initial state
       "Loading..."
       (for [person (:people data)]
         (d/div
          {:key (:person/id person)
           :style {:padding 5}}
          (:person/name person)
          " "
          (d/button
           {:on-click #(on-navigate-details (:person/id person))}
           "Details")))))))


(defnc person-best-friend
  [{:keys [id]}]
  (let [[data status] (exo.hooks/use-preloaded-query (exo/parameterize-query
                                                      best-friend-query
                                                      {:id id}))
        {:keys [best-friend]} data]
    (d/div
     (if (= :rejected status)
       "No best friend data available."
       (<>
        (d/h4 "Best friend")
        (d/div "Name: " (:person/name best-friend)) ) ))))


(defnc person-details
  [{:keys [id on-back]}]
  (let [[data status] (exo.hooks/use-preloaded-query (exo/parameterize-query
                                                      person-query
                                                      {:id id}))
        details (get data [:person/id id])]
    (d/div
     {:style {:opacity (if (= :pending status) 0.5 1)}}
     (d/h2
      (d/a
       {:on-click #(do
                     (.preventDefault %)
                     (on-back)) :href "#"}
       "<")
      " Details")
     (d/div "Name: " (:person/name details))
     (d/div "Species: " (str (:person/species details)))
     ($ person-best-friend {:id id}))))


(defnc app
  []
  (let [[[screen params] set-screen] (hooks/use-state [:people])]
    (helix.core/provider
     {:context exo.hooks/exo-config-context
      :value exo-config}
     (case screen
       :people ($ people-list
                  {:on-navigate-details
                   (fn [id]
                     (exo/preload! exo-config (exo/parameterize-query
                                               person-query
                                               {:id id}))
                     (exo/preload! exo-config (exo/parameterize-query
                                               best-friend-query
                                               {:id id}))
                     (set-screen [:details {:id id}]))})
       :details ($ person-details
                   {:id (:id params)
                    :on-back (fn []
                               (exo/preload! exo-config people-query)
                               (set-screen [:people]))})))))


#_(defonce root (rdom/createRoot (js/document.getElementById "app")))


(defn ^:dev/after-load start
  []
  (exo/preload! exo-config people-query)
  #_(.render root ($ app))
  (rdom/render ($ r/StrictMode ($ app)) (js/document.getElementById "app")))
