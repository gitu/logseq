(ns frontend.components.select
  "Generic component for fuzzy searching items to select an item. See
  select-config to add a new use or select-type for this component. To use the
  new select-type, set :ui/open-select to the select-type. See
  :select-graph/open command for an example."
  (:require [frontend.modules.shortcut.core :as shortcut]
            [frontend.context.i18n :as i18n]
            ;; Would prefer to depend on handler.repo but unable to because
            ;; of a circular dependency
            [frontend.components.repo :as repo]
            [frontend.search :as search]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [frontend.util :as util]
            [rum.core :as rum]))

(rum/defc render-item
  [{:keys [id value]} chosen?]
  [:div.inline-grid.grid-cols-4.gap-x-4.w-full
   {:class (when chosen? "chosen")}
   [:span.col-span-3 value]
   [:div.col-span-1.justify-end.tip.flex
    (when id
      [:code.opacity-20.bg-transparent id])]])

(rum/defcs select <
  (shortcut/disable-all-shortcuts)
  (rum/local "" ::input)
  {:will-unmount (fn [state]
                   (state/set-state! [:ui/open-select] false)
                   state)}
  [state {:keys [items limit on-chosen]
          :or {limit 100}}]
  (rum/with-context [[t] i18n/*tongue-context*]
    (let [input (::input state)]
      [:div.cp__select.cp__select-main
       [:div.input-wrap
        [:input.cp__select-input.w-full
         {:type        "text"
          :placeholder (t :select/prompt)
          :auto-focus  true
          :value       @input
          :on-change   (fn [e] (reset! input (util/evalue e)))}]]

       [:div.item-results-wrap
        (ui/auto-complete
         (search/fuzzy-search items @input :limit limit :extract-fn :value)
         {:item-render render-item
          :class       "cp__select-results"
          :on-chosen   (fn [x]
                         (state/close-modal!)
                         (on-chosen x))})]])))

(def select-config
  "Config that supports multiple types (uses) of this component. To add a new
  type, add a key with the value being a map with the following keys:

  * :items-fn - fn that returns items with a :value key that are used for the
    fuzzy search and selection. Items can have an optional :id and are displayed
    lightly for a given item.
  * :on-chosen - fn that is given item when it is chosen."
  {:select-graph
   {:items-fn (fn []
                (map #(hash-map :value (second (util/get-dir-and-basename (:url %)))
                                :id (:url %))
                     (state/get-repos)))
    :on-chosen #(repo/switch-repo-if-writes-finished? (:id %))}})

(rum/defc select-modal < rum/reactive
  []
  (when-let [select-type (state/sub [:ui/open-select])]
    (let [{:keys [on-chosen items-fn]} (select-config select-type)]
      (state/set-modal!
       #(select {:items (items-fn) :on-chosen on-chosen})
       {:fullscreen? false
        :close-btn?  false}))
    nil))
