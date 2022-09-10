(ns demo.pokemon.main
  (:require
   [cljs.pprint :refer [pprint]]
   [demo.pokemon.api :as api]
   [exo.core :as exo]
   [exo.hooks]
   [helix.core :refer [defnc $]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   ["react-dom/client" :as rdom]))

(defonce exo-config
  (exo/create-config
   {:network (fn [query _opts]
               (api/fetch-query query))}))

(defn pokemon-query
  [id]
  [{[:pokemon/id id] [:pokemon/name
                      :pokemon/id
                      :pokemon/height
                      :pokemon/weight
                      {:pokemon/sprites [:pokemon.sprites/front-default]}]}])

(defnc app
  []
  (let [[id set-id] (hooks/use-state 1)
        {:keys [data loading?]} (exo.hooks/use-query (pokemon-query id))]
    (d/div
     (d/button
      {:on-click #(set-id dec)
       :disabled (= 1 id)}
      "Prev")
     (d/button {:on-click #(set-id inc)} "Next")
     (d/div
      (d/img {:src (get-in data [[:pokemon/id id] :pokemon/sprites :pokemon.sprites/front-default])}))
     (d/div
      (d/code
       (d/pre
        (with-out-str
          (pprint data))))))))

(defonce root (rdom/createRoot (js/document.getElementById "app")))

(defn ^:dev/after-load reload
  []
  (.render root ($ exo.hooks/provider {:config exo-config}
                   ($ app))))

(defn start
  []
  (exo/preload! exo-config (pokemon-query 1))
  (reload))
