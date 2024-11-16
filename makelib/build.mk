.PHONY: build

out/index.html: clj/site/index.html
	mkdir --parents -- "$$(dirname -- '$@')"
	cp --recursive --force -- '$<' '$@'

out/site.css::
	node_modules/.bin/tailwindcss --minify --input css/site.scss --output '$@'

out/main.js::
	clojure -M:build-cljs

out/srv.jar:
	clojure -T:build-clj jar

build: out/srv.jar out/index.html out/site.css out/main.js
