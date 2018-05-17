(ns cloudship.util.oauth
  (:import (javafx.scene.web WebView)
           (javafx.scene.layout StackPane VBoxBuilder)
           (javafx.scene Scene SceneBuilder)
           (javafx.stage StageBuilder)
           (javafx.scene.control ButtonBuilder)))

(defonce force-toolkit-init (javafx.embed.swing.JFXPanel.))

(defn run-later*
  [f]
  (javafx.application.Platform/runLater f))

(defmacro run-later
  [& body]
  `(run-later* (fn [] ~@body)))

(defn run-now*
  [f]
  (let [result (promise)]
    (run-later
      (deliver result (try (f) (catch Throwable e e))))
    @result))

(defmacro run-now
  [& body]
  `(run-now* (fn [] ~@body)))

(defn browser-scene []
  (let [web-view (WebView.)
        web-engine (.getEngine web-view)
        sp (StackPane.)]
    (.load web-engine "http://www.google.com")
    (.add (.getChildren sp) web-view)
    (Scene. sp)))

(defn browser-stage []
  (run-now
    (.build (doto (StageBuilder/create)
              (.title "SF Browser")
              (.scene (browser-scene))))))

(defn show-browser []
  (run-now (.show (browser-stage))))

(defn event-handler*
  [f]
  (reify javafx.event.EventHandler
    (handle [this e] (f e))))

(defmacro event-handler [arg & body]
  `(event-handler* (fn ~arg ~@body)))

(def stage (atom nil))
;; You of course don't have to write it all in one block. Using a (def button (.. ButtonBuilder ...)) and then adding button is just as good (probably better most of the times).
(run-now (reset! stage (.. StageBuilder create
                           (title "Hello JavaFX")
                           (scene (.. SceneBuilder create
                                      (height 480) (width 640)
                                      (root (.. VBoxBuilder create
                                                (minHeight 480) (minWidth 640)
                                                (children [(.. ButtonBuilder create
                                                               (text "Say \"Hello Clojure\"!")
                                                               (onAction (event-handler [_] (println "Hello Clojure!")))
                                                               build)])
                                                build))
                                      build))
                           build)))

(defn -main
  [& args]
  (run-now (.showAndWait @stage)))