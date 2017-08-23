(ns maria.blocks.list-view
  (:require [re-view.core :as v :refer [defview]]
            [cljsjs.markdown-it]
            [re-db.d :as d]
            [maria.blocks.code]
            [maria.blocks.prose]
            [maria.blocks.blocks :as Block]
            [maria-commands.exec :as exec]
            [maria.commands.blocks]
            [maria.eval :as e]))


(d/transact! [[:db/add :feature :in-place-eval true]])

(defview block-list
  {:key                     :source-id
   :view/initial-state      (fn [{value :value}]
                              {:last-update value
                               :blocks      (Block/ensure-blocks (Block/from-source value))})
   :view/did-mount          (fn [{:keys [view/state] :as this}]
                              (Block/focus! (first (:blocks @state)) :start)
                              (exec/set-context! {:block-list this})
                              (when (= (str (d/get-in :router/location [:query :eval])) "true")
                                (e/on-load #(exec/exec-command-name :eval/doc))))
   :view/will-unmount       #(exec/set-context! {:block-list nil})
   :view/will-receive-props (fn [{value :value
                                  state :view/state
                                  :as   this}]
                              ;; normally, the source we are passed from above during editing
                              ;; is the source we last passed upwards in :will-receive-state.
                              ;; if these are different, the document has changed and we
                              ;; should re-parse from scratch.
                              (when (not= value (:last-update @state))
                                (swap! state assoc :blocks (Block/ensure-blocks (Block/from-source value)))))
   :view/should-update      (fn [{:keys [view/state
                                         view/prev-state
                                         on-update]}]
                              (let [{:keys [blocks]} @state
                                    blocks-changed? (not= blocks (:blocks prev-state))]
                                (when blocks-changed?
                                  (let [updated-source (Block/emit-list (:blocks @state))]
                                    (v/swap-silently! state assoc :last-update updated-source)
                                    (on-update updated-source)))
                                blocks-changed?))
   :get-blocks              #(:blocks @(:view/state %))
   :splice                  (fn splice
                              ([this block value] (splice this block 0 value))
                              ([{:keys [view/state]} block n value]
                               (let [blocks (Block/splice-block (:blocks @state) block n value)]
                                 (js/setTimeout #(swap! state update :blocks Block/join-blocks) 0)
                                 (:blocks (swap! state assoc :blocks blocks)))))}
  [{:keys [view/state] :as this}]
  (let [{:keys [blocks]} @state]
    [:.w-100.flex-none.pv3
     (map (fn [block]
            (Block/render block {:blocks     blocks
                                 :block-list this})) blocks)]))

