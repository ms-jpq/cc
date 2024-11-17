(ns srv.ffmpeg)

(defrecord FFprobeError
           [^String code
            ^String string])

(defrecord FFprobeFormat
           [^String duration
            ^String format_name
            ^String format_long_name
            tags])

(defrecord FFprobeStreamTags
           [^String language
            ^String title
            ^String ENCODER])

(defrecord FFprobeStream
           [^String codec_name
            ^String codec_long_name
            ^String codec_type
            ^Long width
            ^Long height
            ^FFprobeStreamTags tags])

(defrecord FFprobe
           [^FFprobeError error
            ^FFprobeFormat format
            ^Vec streams])
