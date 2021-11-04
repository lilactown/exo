(ns exo.example.app
  (:require
   ["react" :as r]
   ["react-dom" :as rdom]
   [exo.core :as exo]
   [exo.hooks :as exo.hooks]
   [exo.example.api :as example.api]
   [helix.core :refer [defnc $ <>]]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(defonce exo-config
  (exo/create-config
   {:network [example.api/people-link
              example.api/person-link
              example.api/best-friend-link]}))


(def people-query
  [{:people [:person/id :person/name]}])


(def person-query
  '[{[:person/id ?id] [:person/name :person/species]}])


(def best-friend-query
  '[{(:best-friend {:person/id ?id}) [:person/id :person/name]}])


(defnc people-list
  [{:keys [on-navigate-details]}]
  (let [{:keys [data loading?]} (exo.hooks/use-query people-query)]
    (d/div
     ;; when we re-fetch, turn opacity down
     {:style {:opacity (if loading? 0.5 1)}}
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
  (let [{:keys [data status]} (exo.hooks/use-query
                               (exo/parameterize-query
                                best-friend-query
                                {:id id}))
        {:keys [best-friend]} data]
    (d/div
     (cond
       (= :error status)
       "No best friend data available."

       (and (empty? data) (= :loading status))
       (<>
        (d/h4 "Best friend")
        (d/div "Name: <...>") )

       :else
       (<>
        (d/h4 "Best friend")
        (d/div "Name: " (:person/name best-friend)) ) ))))


(defnc person-details
  [{:keys [id on-back]}]
  (let [{:keys [data loading?]} (exo.hooks/use-query
                                 (exo/parameterize-query
                                  person-query
                                  {:id id}))
        details (get data [:person/id id])]
    (d/div
     {:style {:opacity (if loading? 0.5 1)}}
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
  (let [[[screen params] set-screen] (hooks/use-state [:people])
        exo-config (exo.hooks/use-config)]
    (case screen
      :people ($ people-list
                 {:on-navigate-details
                  (fn [id]
                    (exo/preload! exo-config (exo/parameterize-query
                                              person-query
                                              {:id id}))
                    ;; we could preload this here, but instead we'll show how
                    ;; w/o preloading it will fetch on mount
                    #_(exo/preload! exo-config (exo/parameterize-query
                                              best-friend-query
                                              {:id id}))
                    (set-screen [:details {:id id}]))})
      :details ($ person-details
                  {:id (:id params)
                   :on-back (fn []
                              (exo/preload! exo-config people-query)
                              (set-screen [:people]))}))))


(defonce root (rdom/createRoot (js/document.getElementById "app")))


(defn ^:dev/after-load start
  []
  (exo/preload! exo-config people-query)
  (.render root (helix.core/provider
                 {:context exo.hooks/exo-config-context
                  :value exo-config}
                 ($ app))))
