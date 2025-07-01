(ns sdk.radio
  (:require [clojure.spec.alpha :as s]))

; https://all.api.radio-browser.info/
(defrecord Station
           [^String changeuuid
            ^String stationuuid
            ^String name
            ^String url
            ^String url_resolved
            ^String homepage
            ^String favicon
            ^String tags
            ^String country
            ^String countrycode
            ^String state
            ^String iso_3166_2
            ^String language
            ^String languagecodes
            ^Integer votes
            ^String lastchangetime
            ^String lastchangetime_iso8601
            ^String codec
            ^Integer bitrate
            ^Integer hls
            ^Integer lastcheckok
            ^String lastchecktime
            ^String lastchecktime_iso8601
            ^String lastcheckoktime
            ^String lastcheckoktime_iso8601
            ^String lastlocalchecktime
            ^String lastlocalchecktime_iso8601
            ^String clicktimestamp
            ^String clicktimestamp_iso8601
            ^Integer clickcount
            ^Integer clicktrend
            ^Double geo_lat
            ^Double geo_long
            ^Double geo_distance
            ^Boolean has_extended_info])

(defrecord Country
           [^String name
            ^String code
            ^String iso_3166_1
            ^Integer stationcount])

(defrecord State
           [^String name
            ^String country
            ^Integer stationcount])

(defrecord Language
           [^String name
            ^String iso_639
            ^Integer stationcount])
