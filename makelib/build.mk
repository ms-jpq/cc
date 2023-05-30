.PHONY: build

out/index.html: clj/site/index.html
	mkdir --parents -- "$$(dirname -- '$@')"
	cp --recursive --force -- '$<' '$@'

out/site.css::
	node_modules/.bin/tailwindcss --minify --input css/site.scss --output '$@'

out/main.js::
	clojure -M:build

bundle:
	true

build: lint out/index.html out/site.css out/main.js
	printf -- '%s\n' DONE

