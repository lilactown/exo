(ns exo.http-example.app
  (:require
   ["react-dom" :as rdom]
   [exo.core :as exo]
   [exo.hooks :as exo.hooks]
   [exo.network.http :as exo.http]
   [exo.http-example.api :as example.api]
   [helix.core :refer [defnc $ <>]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   [lambdaisland.fetch.edn]))


(def exo-config
  (exo/create-config
   {:network [(exo.http/link
               {:default-body-handler
                (fn [_ body]
                  (exo.http/transform-keys
                   body
                   example.api/pokemon-key-map))})]}))


(defn pokemon-query
  [id]
  `[{(:http/req
      {:uri ~(str "https://pokeapi.co/api/v2/pokemon/" id "/")
       :content-type :json})
     [:pokemon/id :pokemon/name :pokemon/weight]}])


(comment
  (edn-query-language.core/query->ast
   pokemon-query)

  (exo.core/preload!
   exo-config
   pokemon-query)

  (js/console.log @(:data-cache exo-config))

  (exo.data/pull
   (:data-cache exo-config)
   (pokemon-query 1))

  (exo.data/pull
   (:data-cache exo-config)
   '[(:http/req {:uri "https://pokeapi.co/api/v2/pokemon/1/" :content-type :json})]))


(defnc app
  []
  (let [[pokemon set-pokemon] (hooks/use-state 1)
        {:keys [data loading?]} (exo.hooks/use-query (pokemon-query pokemon))]
    (d/div
     (d/input
      {:type "number"
       :value pokemon
       :on-change #(set-pokemon (.. % -target -value))})
     (d/div (pr-str loading?))
     (d/div (pr-str data)))))


(defonce root (rdom/createRoot (js/document.getElementById "app")))


(defn ^:dev/after-load start
  []
  (.render root (helix.core/provider
                 {:context exo.hooks/exo-config-context
                  :value exo-config}
                 ($ app)))
  #_(rdom/render
   ($ r/StrictMode
      (helix.core/provider
       {:context exo.hooks/exo-config-context
        :value exo-config}
       ($ app)))
   (js/document.getElementById "app")))
