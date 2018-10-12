(ns ^:no-doc cloudship.connection.props.proxy
  (:import (java.net ProxySelector URI Proxy InetSocketAddress)))

(defn proxy-from-sysprops []
  (let [port (System/getProperty "http.proxyPort")
        host (System/getProperty "http.proxyHost")]
    (if (and port host)
      {:port port
       :host host})))

(defn proxy-from-selector [url]
  (let [proxies (into [] (.select (ProxySelector/getDefault) (URI. url)))]
    (if (not-empty proxies)
      (if-let [^InetSocketAddress address (.address ^Proxy (first proxies))]
        {:port (.getPort address)
         :host (.getHostString address)}))))

(defn find-default-proxy [url]
  (or (proxy-from-selector url) (proxy-from-sysprops)))