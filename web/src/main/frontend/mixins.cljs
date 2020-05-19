(ns frontend.mixins
  (:require [rum.core :as rum]
            [goog.dom :as dom]
            [goog.object :as gobj]
            [frontend.keyboard :as keyboard])
  (:import [goog.events EventHandler]))

(defn detach
  "Detach all event listeners."
  [state]
  (some-> state ::event-handler .removeAll))

(defn listen
  "Register an event `handler` for events of `type` on `target`."
  [state target type handler & [opts]]
  (when-let [^EventHandler event-handler (::event-handler state)]
    (.listen event-handler target (name type) handler (clj->js opts))))

(def event-handler-mixin
  "The event handler mixin."
  {:will-mount
   (fn [state]
     (assoc state ::event-handler (EventHandler.)))
   :will-unmount
   (fn [state]
     (detach state)
     (dissoc state ::event-handler))})

;; (defn timeout-mixin
;;   "The setTimeout mixin."
;;   [name t f]
;;   {:will-mount
;;    (fn [state]
;;      (assoc state name (util/set-timeout t f)))
;;    :will-unmount
;;    (fn [state]
;;      (let [timeout (get state name)]
;;        (util/clear-timeout timeout)
;;        (dissoc state name)))})

;; (defn interval-mixin
;;   "The setInterval mixin."
;;   [name t f]
;;   {:will-mount
;;    (fn [state]
;;      (assoc state name (util/set-interval t f)))
;;    :will-unmount
;;    (fn [state]
;;      (when-let [interval (get state name)]
;;        (util/clear-interval interval))
;;      (dissoc state name))})

(defn hide-when-esc-or-outside
  [state & {:keys [on-hide node]}]
  (let [node (or node (rum/dom-node state))]
    (listen state js/window "click"
            (fn [e]
              ;; If the click target is outside of current node
              (when-not (dom/contains node (.. e -target))
                (on-hide state e))))

    (listen state node "keydown"
            (fn [e]
              (case (.-keyCode e)
                ;; Esc
                27 (on-hide state e)
                nil)))))

(defn on-enter
  [state & {:keys [on-enter node]}]
  (let [node (or node (rum/dom-node state))]
    (listen state node "keyup"
            (fn [e]
              (case (.-keyCode e)
                ;; Enter
                13 (on-enter e)
                nil)))))

(defn on-key-up
  [state keycode-map all-handler]
  (let [node (rum/dom-node state)]
    (listen state js/window "keyup"
            (fn [e]
              (let [key-code (.-keyCode e)]
                (when-let [f (get keycode-map key-code)]
                  (f state e))
                (when all-handler (all-handler e key-code)))))))

(defn on-key-down
  [state keycode-map all-handler]
  (let [node (rum/dom-node state)]
    (listen state js/window "keydown"
            (fn [e]
              (let [key-code (.-keyCode e)]
                (when-let [f (get keycode-map key-code)]
                  (f state e))
                (when all-handler (all-handler e key-code)))))))

(defn event-mixin
  ([attach-listeners]
   (event-mixin attach-listeners identity))
  ([attach-listeners init-callback]
   (merge
    event-handler-mixin
    {:init (fn [state props]
             (init-callback state))
     :did-mount (fn [state]
                  (attach-listeners state)
                  state)
     :did-remount (fn [old-state new-state]
                    (detach old-state)
                    (attach-listeners new-state)
                    new-state)})))

;; TODO: is it possible that multiple nested components using the same key `:open?`?
(defn modal
  []
  (let [k :open?]
    (event-mixin
     (fn [state]
       (let [open? (get state k)]
         (hide-when-esc-or-outside
          state
          :on-hide (fn [] (reset! open? false)))))
     (fn [state]
       (let [open? (atom false)
             component (:rum/react-component state)]
         (add-watch open? ::open
                    (fn [_ _ _ _]
                      (rum/request-render component)))
         (assoc state
                k open?
                :close-fn (fn []
                            (reset! open? false))
                :open-fn (fn []
                           (reset! open? true))
                :toggle-fn (fn []
                             (swap! open? not))))))))

(defn will-mount-effect
  [handler]
  {:will-mount (fn [state]
                 (handler (:rum/args state))
                 state)})

(defn keyboard-mixin
  "Triggers f when key is pressed while the component is mounted.
   if target is a function it will be called AFTER the component mounted
   with state and should return a dom node that is the target of the listener.
   If no target is given it is defaulted to js/window (global handler)
   Ex:
     (keyboard-mixin \"esc\" #(browse-to :home/home))"
  ([key f] (keyboard-mixin key f (fn [_] true) js/window))
  ([key f enable-f] (keyboard-mixin key f enable-f js/window))
  ([key f enable-f target]
   (let [target-fn (if (fn? target) target (fn [_] target))]
     {:did-mount
      (fn [state]
        (if (enable-f state)
          (assoc state ::keyboard-listener
                (keyboard/install-shortcut! key
                                            (fn [] (f state))
                                            false
                                            (target-fn state)))
          state))
      :will-unmount
      (fn [state]
        (when (enable-f state)
          ((::keyboard-listener state)))
        state)})))
