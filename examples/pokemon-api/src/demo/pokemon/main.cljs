(ns demo.pokemon.main
  (:require
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
  [{[:pokemon/id id] [:pokemon/name :pokemon/id]}])

(comment
  (.then (api/fetch-query (pokemon-query 1)) prn))

(defnc app
  []
  (let [[id set-id] (hooks/use-state 1)
        query-state (exo.hooks/use-query (pokemon-query id))]
    (d/div
     (d/button {:on-click #(set-id dec)
                :disabled (= 1 id)} "Prev")
     (d/button {:on-click #(set-id inc)} "Next")
     (d/div
      (pr-str query-state)))))

(defonce root (rdom/createRoot (js/document.getElementById "app")))

(defn ^:dev/after-load reload
  []
  (.render root ($ exo.hooks/provider {:config exo-config}
                   ($ app))))

(defn start
  []
  (exo/preload! exo-config (pokemon-query 1))
  (reload))
